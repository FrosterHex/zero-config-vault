# Phase 2: Vault Correctness (Retention, Live Events, Bulk Transactions, Ciphertext Guard) and Real-MongoDB Tests

Date: 2026-10-29
Status: approved 2026-10-29
Branch: `fix/correctness-phase2` (from `main` at `4e60295`)

## Intent

The features shown in the demo — versioned configuration, rollback, live updates and atomic bulk
import — must behave as the project claims, and that must be proven against a real MongoDB, not only
with mocks. Today several of them are subtly wrong:

- Old versions are deleted on a timer that is applied twice (about 180 days), the previous version is
  modified before the new one is safely saved, and nothing protects recent rollback targets.
- Every configuration write produces two or three live (SSE) events, one of them describing the old
  version.
- Atomic bulk import writes to the capped audit log, clears the cache and broadcasts events inside the
  MongoDB transaction. MongoDB rejects capped-collection writes inside transactions, so on a real
  replica set the import likely always rolls back.
- A client can submit a `secure_settings` value shaped like ciphertext; it is stored unencrypted and
  every later read of that configuration fails.

## Goals

1. **Retention policy for superseded versions:**
   - the current (latest) version never expires;
   - the most recent `K` superseded versions are always kept (default `K = 5`);
   - any older superseded version is deleted `D` days after it falls out of that window (default `D = 90`);
   - `K` and `D` are configuration properties.
2. **Exactly one live event per write**, sourced from the MongoDB change stream, for both configuration
   and binary writes; resilient to dropped connections.
3. **Atomic bulk import works on a real replica set:** all-or-nothing writes; audit, cache eviction and
   live events happen only after a successful commit (and a rollback is still audited).
4. **Ciphertext-shaped input is rejected** with HTTP 400 instead of being stored.
5. **Proof against a real MongoDB** (Testcontainers replica set) for retention, transactions, change
   streams and encryption at rest; the `starter` module's tests run and pass.

## Non-goals

- Retention for binary (GridFS) versions.
- Changing the API shape for clients, or the frontend (other than what live events already consume).
- Restructuring the Maven build into a multi-module reactor.
- Load or performance testing.

## Anti-goals

- Any change that deletes the current version of a configuration, or deletes one of the `K` newest
  superseded versions.
- A startup failure on the live (Atlas/Render) deployment because an existing index changes options.
- Live events that arrive twice, describe the wrong version, or stop permanently after one network blip.

## Constraints

- Spring Boot 3.2 / Java 17 / Spring Data MongoDB; MongoDB 7 (Atlas M0 in production, replica set
  `rs0` in docker-compose).
- The live Atlas database already contains configuration documents and an index on `expiresAt` created
  by Spring's `@Indexed(expireAfter = "90d")`, with some documents already carrying `expiresAt`
  (= replacement time + 90 days under the old logic).
- MongoDB refuses to create an index whose key matches an existing index with different options
  (`IndexOptionsConflict`); an earlier deployment failed exactly this way.
- Atlas M0 supports change streams and transactions; MongoDB's TTL monitor runs about every 60 seconds.
- Configuration documents are hashed over their payload only; `expiresAt` is metadata outside the hash.
- Docker is available on the developer machine for Testcontainers.

## Approaches considered

| Decision | Options | Chosen |
|---|---|---|
| Live event source | **A. MongoDB change stream only** (inserts only, resume token, service broadcast only as fallback when change streams are unavailable) · B. service broadcast only | **A** (user's choice): correct with several backend instances, sees writes made outside the API, showcases a MongoDB feature |
| Version retention | **TTL index on an exact `expiresAt` date** · scheduled cleanup job · keep forever | **TTL** (user chose retention; TTL is MongoDB-native and needs no scheduler) |
| Retention rule | time only · count only · **count + time** | **count + time** (user's choice: keep last 5, expire older after 90 days) |
| Changing the existing TTL index | drop and recreate · **`collMod` in place** | **`collMod`**: no window without an index, no conflict at startup |

## Design

### 1. Version retention

- `expiresAt` holds the **exact deletion time**. The TTL index on `expiresAt` uses
  `expireAfterSeconds: 0`. The `@Indexed(expireAfter = "90d")` annotation is removed; the index is
  owned by `MongoConfig`:
  - if no index on `{ expiresAt: 1 }` exists, create it with `expireAfterSeconds: 0`;
  - if one exists with a different `expireAfterSeconds`, change it with
    `collMod: "configurations", index: { keyPattern: { expiresAt: 1 }, expireAfterSeconds: 0 }`;
  - failures are logged and never stop startup (matches the existing text-index handling).
- After a **new version is saved** (create, partial update, rollback, bulk import), the service applies
  the policy to that app/env:
  - versions newer than the newest `K + 1` documents (current + `K` superseded) keep or get
    `expiresAt = null`;
  - every older version without an `expiresAt` gets `expiresAt = now + D days`;
  - older versions that already have an `expiresAt` keep it (no extension on every write).
  - This is a single `updateMany` per write (version `<` the cut-off version and `expiresAt` absent).
- A failure while applying retention is logged and does not fail the write (the new version is already
  committed; the next write re-applies the policy).
- Properties: `vault.version.retain-count` (default 5) and `vault.version.expiry-days` (existing,
  default 90). `expiry-days <= 0` disables expiry: no new dates are set, and the startup pass clears
  every pending `expiresAt` so nothing is deleted.
- Rollback targets get no special protection: retention is by version rank only. Rolling back creates
  a new current version with the target's content, so that content is never lost.
- **Existing data:** on startup, a one-time idempotent pass re-applies the policy to every app/env so
  documents carrying old-logic dates are brought in line (protected window cleared to `null`, others
  keep their date). This runs in the existing startup remediation runner or a sibling.

### 2. Exactly one live event per write

- `ChangeStreamService` opens one change stream on the database filtered to
  `operationType: "insert"` and `ns.coll` in `configurations`, `binary_configurations`, and maps each
  insert to a `ConfigChangeEvent` (`CREATE`, or `ROLLBACK` when tags carry `rollback_from_version`;
  binary events carry `isBinary: true` as today).
- Updates (retention marking), deletes (TTL) and replaces are ignored.
- **Resilience:** remember the last resume token; on an error, reconnect with `resumeAfter` using
  backoff (1 s doubling to 30 s), indefinitely while the application runs; if the token is no longer
  valid, restart from "now" and log a warning.
- **Single source, decided once:** the mode is fixed at startup and never flips afterwards, so the two
  sources can never both fire.
  - If the change stream opens successfully at startup (within a few retries), the application is in
    **change-stream mode** for its lifetime: services never broadcast. During a later outage, events
    are replayed from the resume token on reconnect; if no token exists or it is invalid, events
    written during the gap are not replayed. This is acceptable because every SSE client refetches its
    data when it (re)connects (frontend behaviour from the BFF work).
  - If the change stream cannot be opened at startup (standalone MongoDB, unit tests), the application
    is in **fallback mode** for its lifetime: services broadcast after each committed write, exactly once.
  - The active mode is logged at startup and exposed for tests.
- Events are broadcast only after the write is durable: with change streams this is inherent (inserts
  appear after commit); in fallback mode broadcasts happen after commit (see 3).

### 3. Atomic bulk import

- Inside the transaction: only the configuration inserts (and their version lookups).
- After a successful commit: cache eviction for each app/env, retention policy for each app/env,
  audit entries, and (in fallback mode) live events.
- On rollback: no cache/retention/event side effects; one audit entry `BULK_IMPORT_ROLLBACK` recording
  the failure count, written after the transaction ends.
- Best-effort (non-atomic) bulk import keeps today's per-entry behaviour.
- `atomic=true` without a transaction manager, or against a deployment that does not support
  transactions, fails with the existing `BulkTransactionException` (HTTP 422) and a clear message
  ("atomic bulk import requires a replica set") instead of silently degrading to best-effort.
- `createConfiguration` is split so the transactional path can run "insert only" and collect the
  side effects to run afterwards; the single-write API path keeps its current behaviour.

### 4. Ciphertext-shaped input

- On create, partial update and bulk import, any value inside a `secure_settings` object that is an
  object shaped like the vault's encrypted envelope (has the IV, ciphertext and algorithm fields that
  `JsonTraverser.isAlreadyEncrypted` checks) is rejected with 400
  ("secure_settings.<path> must be a plain value; encrypted envelopes cannot be submitted").
- Internal re-encryption paths (rollback copying stored ciphertext, the lifecycle listener) are not
  affected.
- In bulk import the guard is a per-entry validation error like any other: atomic mode rolls back the
  whole batch (422, `BULK_IMPORT_ROLLBACK` audited); best-effort mode fails only that entry.

### 5. Tests against a real MongoDB

- Add Testcontainers (`org.testcontainers:mongodb` + `junit-jupiter`, test scope; versions managed by
  the Spring Boot BOM) and an integration test base using `MongoDBContainer("mongo:7.0")` (a
  single-node replica set, so transactions and change streams work), wired with
  `@DynamicPropertySource`.
- Integration tests (run with the normal `mvn test`; Docker required):
  - retention: after 8 writes, the newest 6 have `expiresAt = null`, older ones have a date about
    `D` days ahead; the TTL index exists with `expireAfterSeconds: 0`; a document whose `expiresAt` is in
    the past is actually removed by MongoDB (start the container with
    `--setParameter ttlMonitorSleepSecs=1`);
  - index migration: start with the old `expireAfter 90d` index present → after startup it is `0` and
    no error;
  - atomic bulk import: one invalid entry → zero documents inserted, one `BULK_IMPORT_ROLLBACK` audit
    entry; all valid → all inserted and audited after commit;
  - live events: one write → exactly one SSE event with the new version; retention updates and TTL
    deletes produce none;
  - encryption at rest: the raw stored document never contains a plaintext secret;
  - ciphertext guard: an envelope-shaped value → 400 and nothing stored.
- Integration tests are skipped (not failed) when Docker is unavailable, and the README says how to run
  them.
- `starter`: its tests run and pass with `mvn -f starter/pom.xml test`; fix anything broken; README
  documents the command.

## Rollout

Merge to `main` deploys Render and Vercel. On first start against Atlas: the TTL index is changed in
place (`collMod`), and the one-time retention pass normalises existing `expiresAt` values. Verify on
the live site that a new version produces one live event and rollback still works.

## Calls made by the author (review these)

- Live-event mode is decided once at startup and never flips; events written during a stream outage
  with no valid resume token are not replayed (clients refetch on reconnect).
- `atomic=true` without transaction support fails with 422 instead of silently running best-effort.
- Ciphertext-shaped bulk entries are ordinary per-entry validation errors.
- `expiry-days <= 0` clears all pending expiry dates.
- Rollback targets are not exempt from retention.
- `starter` stays a separate Maven build (root `pom.xml` is the application, not an aggregator).

## Left to the builder

- Exact class/method names for the post-commit side-effect collection and the retention helper.
- Where the one-time retention pass lives (existing remediation runner or a new runner).
- Test class layout and naming.
