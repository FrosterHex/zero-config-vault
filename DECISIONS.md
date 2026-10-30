# Zero-Config Vault: Architectural Decisions

Each entry records a decision, why it was made, and what it costs. Entries 14 to 18 were added during the 2026 hardening and correctness work. [SPEC.md](SPEC.md) describes the resulting behaviour in detail.

---

## 1. MongoDB as the only data store

- **Decision:** MongoDB provides every storage need: configuration documents, the read cache (TTL index), the audit trail (capped collection), telemetry (time-series collection), change notifications (change streams) and binary files (GridFS). No Redis, message broker or object store is used.
- **Reasoning:** One dependency to run and secure, and the project demonstrates how many roles MongoDB can fill.
- **Cost:** The system is bound to MongoDB features; a capped collection or change stream has no direct equivalent in most other databases.

## 2. New versions instead of in-place updates

- **Decision:** A write never changes the payload of an existing version. Creates, partial updates, rollbacks and bulk imports insert a new document with the next version number, protected by a unique index on `{applicationName, environment, version}`.
- **Reasoning:** Gives a Git-like history, makes rollback a copy of an old version, and lets two concurrent writers detect a conflict (`409`) instead of overwriting each other.
- **Cost:** Storage grows with every write. Decision 15 limits that growth. The `expiresAt` metadata field is the one field that is updated after insert.

## 3. MongoDB TTL collection as the read cache

- **Decision:** `config_cache` holds the latest version of each configuration for 300 seconds via a TTL index. Cached entries are encrypted again with an AES-256 key that exists only in the backend's memory and is regenerated on every restart.
- **Reasoning:** Removes the need for Redis. The cache only ever contains ciphertext, so a copy of the database files does not expose secrets through the cache.
- **Cost:** A restart makes the existing cache entries unreadable; they are treated as misses and expire within 5 minutes. With several backend instances, each has its own key and cannot read the others' entries.

## 4. Capped collection for the audit trail

- **Decision:** Audit entries go to `audit_logs`, a capped collection of 50 MB or 10,000 documents.
- **Reasoning:** Appends are cheap, the size is bounded without a cleanup job, and documents cannot be deleted individually.
- **Cost:** Old entries are discarded silently once the cap is reached, so this is a recent-activity log, not long-term compliance storage. Writes to a capped collection are not allowed inside a transaction, which shapes decision 17.

## 5. Hashing canonical JSON instead of BSON

- **Decision:** `documentHash` is a SHA-512 hash of the payload serialised as key-sorted JSON, computed over the encrypted payload.
- **Reasoning:** BSON field order and number types can differ between drivers and languages; a canonical form gives the same hash everywhere. Hashing the encrypted form means the hash can be checked without decrypting.
- **Cost:** The hash proves the stored document was not altered. It does not prove who wrote it; it is not a signature.

## 6. Envelope encryption for secrets

- **Decision:** Every value under a `secure_settings` key is encrypted with AES-256-GCM using its own random 256-bit data key. The data key is encrypted with a master key from `VAULT_MASTER_KEY`, and stored next to the ciphertext.
- **Reasoning:** A master-key rotation only needs to re-encrypt the small data keys, not the secrets themselves. A random key per value also means two equal secrets produce different ciphertext.
- **Cost:** Each secret carries an extra encrypted key. A re-wrap helper exists (`CryptoService.reEncryptDek`), but rotation is not exposed as an endpoint yet. Losing the master key makes every secret unreadable.

## 7. No Lombok

- **Decision:** Lombok is not used anywhere in the backend or the starter.
- **Reasoning:** Every constructor and accessor is visible in the source, which makes the code easier to review and audit.
- **Cost:** More boilerplate in model and DTO classes.

## 8. Server-Sent Events for live updates

- **Decision:** Changes reach clients over Server-Sent Events (`GET /stream/{app}/{env}`), not WebSockets or polling.
- **Reasoning:**
  - Configuration flows one way, from the vault to the clients.
  - SSE is plain HTTP, passes through proxies and load balancers, and the browser reconnects automatically.
- **Cost:** One open HTTP connection per subscriber. Events are sent from a single thread, so a stalled client can delay the others; this is acceptable at the current scale.

## 9. Optional multi-document transactions for bulk import

- **Decision:** `POST /config/bulk` runs best effort by default. With `?atomic=true` it runs all inserts in one MongoDB transaction.
- **Reasoning:** Lets a release import many configurations all-or-nothing, using MongoDB's replica-set transactions, while best-effort mode keeps working on a standalone server.
- **Cost:** Atomic mode needs a replica set. Without one the request is refused with `422` rather than silently downgraded (see decision 17).

## 10. GridFS for binary files

- **Decision:** Certificates, keystores and other files are stored in the GridFS bucket `vault_binaries`, with versioned metadata and a SHA-512 hash in `binary_configurations`.
- **Reasoning:** A BSON document is limited to 16 MB; GridFS splits files into 255 KB chunks and keeps the zero-extra-infrastructure rule.
- **Cost:** Binary versions are not covered by the retention policy, so they accumulate until deleted.

## 11. Scoped API keys stored as hashes

- **Decision:** Clients authenticate with API keys sent in `X-API-Key`.
  - The database stores only the SHA-256 hash of each key, plus a display prefix.
  - Each key has roles: `READ`, `WRITE`, `ROLLBACK`, `EXPORT`, `REVEAL_SECRETS` and `ADMIN`.
  - A key can be limited to certain applications and environments.
- **Reasoning:** A database dump does not reveal usable keys. Each service or user can be given the smallest set of permissions it needs, and secrets stay masked unless a key explicitly has `REVEAL_SECRETS`.
- **Cost:** A lost key cannot be recovered, only replaced. Development keys are seeded only in the `dev` profile and deactivated elsewhere, so the profile must never be used against production data.

## 12. Dashboard design system

- **Decision:**
  - **Stack:** Next.js 15 (App Router) with Tailwind CSS 4, Framer Motion and Three.js.
  - **Fonts:** Cormorant Garamond for headings, Montserrat for body text, JetBrains Mono for code and hashes.
  - **Theme:** a dark theme.
  - **Landing page:** a separate cover at `/` with a WebGL planet, Inter and a deep-space palette. Its styles and colour tokens are scoped under `.landing`, so the dashboard keeps its own design.
- **Reasoning:** The dashboard is the main way the project is presented. A consistent, distinctive type pairing and clear status colours make versions, hashes and secrets easy to tell apart.
- **Cost:** Custom components rather than a component library, so accessibility details are maintained by hand. The landing page adds about 1.4 MB of 3D assets, which load after the page text and are cached for a week.

## 13. Motion that respects reduced-motion settings

- **Decision:** Animations use Framer Motion through a shared provider. When the operating system requests reduced motion, transitions are switched off.
- **Reasoning:** Animation helps show state changes, but it must not affect users with motion sensitivity (WCAG 2.1, success criterion 2.3.3).
- **Cost:** New animated components must use the shared provider to inherit this behaviour.

## 14. Server-side proxy and login for the dashboard

- **Decision:** The browser never receives an API key.
  - **Proxy:** dashboard requests go to a Next.js route handler (`/api/vault/...`), which adds the key server-side.
  - **Guests:** a read key, and a sandbox key that may write only to the `playground` application.
  - **Admins:** after a password login, a signed `vault_session` cookie selects the admin key.
- **Reasoning:** Earlier versions exposed keys through `NEXT_PUBLIC_*` variables in the browser bundle. The proxy keeps keys secret and still lets visitors try writes safely during a demo.
- **Cost:** An extra hop for every request, and the login endpoint is not rate-limited, so the admin password must be long and random.

## 15. Retention: keep recent versions, expire older ones by date

- **Decision:**
  - **Kept forever:** the current version and the 5 newest superseded versions.
  - **Expiry:** an older version gets an `expiresAt` date 90 days after it leaves that window. A TTL index with `expireAfterSeconds: 0` deletes it on that date.
  - **Settings:** both numbers are configurable.
- **Alternatives considered:** keeping everything (unbounded growth), deleting by age only (could delete recent rollback targets), and a scheduled cleanup job (extra moving part).
- **Reasoning:** Recent history, the part used for rollback, is always available, while storage stays bounded using a MongoDB-native mechanism.
- **Cost:**
  - Older history disappears after the expiry period.
  - The existing index was changed in place with `collMod` (with a drop-and-recreate fallback when not permitted). As a result, older builds that declare a 90-day index cannot start against the current database without first restoring that index (see the README).

## 16. One source of live events: the change stream

- **Decision:** Live events come only from a database-level change stream on inserts into `configurations` and `binary_configurations`. The services broadcast directly only when the change stream is unavailable at startup. The mode is fixed for the life of the process.
- **Alternatives considered:** broadcasting from the services only (misses writes made outside the API and from other backend instances), or both together (duplicate events).
- **Reasoning:** Exactly one event per write, correct with several backend instances, and it demonstrates a MongoDB feature. Resume tokens and reconnect back-off (1 s to 30 s) survive network drops.
- **Cost:** Events written during an outage are replayed only if the resume token is still valid; otherwise clients rely on refetching when they reconnect.

## 17. Atomic bulk import: inserts inside the transaction, side effects after commit

- **Decision:**
  - **Inside the transaction:** only the configuration inserts.
  - **After commit:** cache eviction, retention, audit entries and fallback-mode events.
  - **On failure:** a rollback writes one `BULK_IMPORT_ROLLBACK` audit entry after the transaction ends.
  - **No transaction support:** the request fails with "atomic bulk import requires a replica set".
- **Reasoning:** MongoDB refuses writes to the capped audit collection inside a transaction, so the earlier design rolled back every atomic import on a real replica set. Running side effects after commit also guarantees that nothing announces data that was rolled back.
- **Cost:** If the process stops between commit and the side effects, the cache can be stale for up to 5 minutes and retention is applied on the next write.

## 18. Proving behaviour against a real MongoDB

- **Decision:** Retention, TTL deletion, change streams, transactions and encryption at rest are tested against a real `mongo:7.0` replica set started with Testcontainers. The integration tests run with the normal `mvn test` and are skipped when Docker is not available.
- **Reasoning:** These features depend on MongoDB server behaviour that mocks cannot reproduce. Mock-only tests had missed that capped-collection writes fail inside transactions.
- **Cost:** Tests need Docker and take longer. Testcontainers is pinned to 1.21.4, because the Spring Boot default cannot talk to Docker Engine 29.
