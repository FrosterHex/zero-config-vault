# Phase 2 Correctness and Real-MongoDB Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make version retention, live events, atomic bulk import and secret input handling correct, and prove it against a real MongoDB replica set.

**Architecture:** A Testcontainers base class lets every task test against a real single-node replica set. Retention moves into its own service driven by a native TTL index on exact `expiresAt` dates. `ChangeStreamService` becomes the single source of live events (fallback broadcast only when change streams are unavailable). Bulk import runs only inserts inside the transaction and performs side effects after commit.

**Tech Stack:** Spring Boot 3.2.5 / Java 17 / Spring Data MongoDB / MongoDB 7; JUnit 5 + Mockito; Testcontainers (`mongodb`, `junit-jupiter`, versions from the Spring Boot BOM).

**Spec:** `docs/superpowers/specs/2026-10-29-correctness-phase2-design.md`

## Global Constraints

- Retention defaults: `vault.version.retain-count = 5`, `vault.version.expiry-days = 90`; the current version never expires; `expiry-days <= 0` disables expiry and clears pending dates.
- TTL index on `configurations.expiresAt` has `expireAfterSeconds: 0`; an existing index with other options is changed with `collMod`, never dropped; index failures never stop startup.
- Live events: inserts only, from `configurations` and `binary_configurations`; existing event type names are kept: `CREATE`, `ROLLBACK`, `BINARY_CREATE`, `BINARY_ROLLBACK`; binary events keep `isBinary: true` in metadata.
- Event mode is decided once at startup and never changes.
- Atomic bulk import without transaction support → `BulkTransactionException` (HTTP 422) with message containing "requires a replica set".
- Ciphertext guard message: `secure_settings.<path> must be a plain value; encrypted envelopes cannot be submitted`.
- Integration tests are skipped, not failed, when Docker is unavailable.
- Commits: no `Co-Authored-By` or any Claude attribution in commit messages.

## Review Focus

1. Restarting against a database that already has the old `expireAfter 90d` index must start cleanly and end with `expireAfterSeconds: 0`. Test in Task 2.
2. An app/env with fewer than `K + 1` versions must get no `expiresAt` on any version. Test in Task 2.
3. Two writes to the same app/env in quick succession produce exactly two events, in version order. Test in Task 3.
4. After the change-stream cursor is closed underneath the listener, later writes still produce exactly one event each. Test in Task 3.
5. An atomic batch that writes the same app/env twice gets consecutive versions, and retention plus cache eviction happen once after commit. Test in Task 4.

---

### Task 1: Real-MongoDB test infrastructure and starter tests

**Files:**
- Modify: `pom.xml` (test dependencies `org.testcontainers:mongodb`, `org.testcontainers:junit-jupiter`)
- Create: `src/test/java/com/vault/configmanager/integration/MongoIntegrationTest.java`
- Create: `src/test/java/com/vault/configmanager/integration/ApplicationStartupIT.java`
- Modify: `starter/` sources only if its tests fail
- Modify: `README.md` (how to run integration tests and starter tests)

**Interfaces:**
- Produces: abstract `MongoIntegrationTest` annotated `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("dev")`, `@Testcontainers(disabledWithoutDocker = true)`; a static `@Container MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0")` (Testcontainers' single-node replica set); `@DynamicPropertySource` setting `spring.data.mongodb.uri` to `MONGO.getReplicaSetUrl("secure_config_vault")`; protected `MongoTemplate mongoTemplate` and `int port` for subclasses; a protected helper `String uniqueApp(String prefix)` returning a unique, schema-valid app name (`^[a-zA-Z0-9_-]+$`) so tests never collide with seeded demo data.
- Test classes ending in `IT` run in the normal `mvn test` (configure Surefire `includes` if needed so `*IT.java` is picked up).

- [ ] **Step 1:** Add the two test dependencies (no explicit versions). Run `mvn -q -B test-compile` → compiles.
- [ ] **Step 2: Write `ApplicationStartupIT`:** `contextStarts_againstReplicaSet` asserts the context loads, `mongoTemplate.getDb().runCommand(new Document("hello", 1))` contains `setName`, and the `configurations` collection exists after startup.
- [ ] **Step 3:** Run `mvn -B test -Dtest=ApplicationStartupIT` → PASS with Docker running. Run it once with Docker stopped (or confirm by reasoning from `disabledWithoutDocker`) → reported as skipped, not failed.
- [ ] **Step 4:** Run `mvn -B -f starter/pom.xml test`. Fix any failure in starter sources (not tests, unless a test is wrong); record the before/after output in the report.
- [ ] **Step 5:** README: add a "Tests" subsection: `mvn test` runs unit and integration tests (integration tests need Docker and are skipped without it); `mvn -f starter/pom.xml test` runs the starter's tests.
- [ ] **Step 6:** Run `mvn -B clean test` → 0 failures. Commit `test: add Testcontainers MongoDB replica-set integration test base`.

### Task 2: Version retention with an exact-date TTL index

**Files:**
- Create: `src/main/java/com/vault/configmanager/service/VersionRetentionService.java`
- Create: `src/main/java/com/vault/configmanager/config/RetentionStartupRunner.java`
- Modify: `src/main/java/com/vault/configmanager/model/VaultDocument.java:44` (remove `@Indexed(expireAfter = "90d")`)
- Modify: `src/main/java/com/vault/configmanager/config/MongoConfig.java` (TTL index ensure/collMod)
- Modify: `src/main/java/com/vault/configmanager/service/ConfigurationService.java` (remove `retirePreviousVersion`; apply retention after save in create and rollback)
- Modify: `src/main/resources/application.yml` (`vault.version.retain-count: 5`)
- Test: `src/test/java/com/vault/configmanager/integration/VersionRetentionIT.java`; update `ConfigurationServiceTest` for removed behaviour

**Interfaces:**
- Consumes: `MongoIntegrationTest`, `uniqueApp(...)` (Task 1).
- Produces:
  - `VersionRetentionService(MongoTemplate, @Value("${vault.version.retain-count:5}") int retainCount, @Value("${vault.version.expiry-days:90}") int expiryDays)`.
  - `void applyRetention(String app, String env)`: the newest `retainCount + 1` versions get `expiresAt` unset; older versions without `expiresAt` get `now + expiryDays`; older versions that already have one keep it; when `expiryDays <= 0`, unset `expiresAt` on all versions of that app/env. Uses `updateMany`, never touches payload/hash.
  - `int applyRetentionToAll()`: applies the rule to every distinct app/env; returns the number of pairs processed.
  - `ConfigurationService` constructor gains `@Autowired(required = false) VersionRetentionService` (null-safe in existing unit tests); `createConfiguration` and `rollbackToVersion` call `applyRetention(app, env)` after the new version is saved, inside a try/catch that logs and never fails the write.
  - `MongoConfig.ensureExpiryTtlIndex(MongoDatabase db)`: creates `{expiresAt: 1}` with `expireAfterSeconds: 0` if absent; if an index on that key exists with a different `expireAfterSeconds`, runs `collMod` `{ collMod: "configurations", index: { keyPattern: { expiresAt: 1 }, expireAfterSeconds: 0 } }`; catches and logs everything.
  - `RetentionStartupRunner implements ApplicationRunner`: calls `applyRetentionToAll()` once at startup; catches and logs.

- [ ] **Step 1: Write `VersionRetentionIT`** (through `ConfigurationService` with real Mongo):
  - `eightWrites_keepNewestSixWithoutExpiry`: 8 creates on one app/env → versions 8..3 have `expiresAt == null`; versions 2..1 have `expiresAt` between `now+89d` and `now+91d`.
  - `fewerThanWindow_noExpiry`: 4 creates → no version has `expiresAt`.
  - `existingExpiryNotExtended`: after 8 writes, record v1's `expiresAt`; 1 more write → v1's `expiresAt` unchanged; v3 now has a date.
  - `ttlIndexIsExactDate`: index on `{expiresAt:1}` exists with `expireAfterSeconds == 0`.
  - `pastExpiryIsDeletedByMongo`: set `ttlMonitorSleepSecs` to 1 via `adminCommand({setParameter: 1, ttlMonitorSleepSecs: 1})`, set v1's `expiresAt` to 1 minute ago, poll up to 30 s → v1 is gone, the current version remains.
  - `oldIndexMigratedInPlace`: drop the TTL index, create `{expiresAt:1}` with `expireAfterSeconds: 7776000`, call `ensureExpiryTtlIndex` → `expireAfterSeconds == 0`, no exception.
  - `expiryDisabledClearsDates`: a `VersionRetentionService` built with `expiryDays = 0` on an app/env that has dated versions → all `expiresAt` unset.
  - `rollbackAppliesRetentionAfterSave`: 7 writes then rollback to v1 → the new v8 exists and the newest 6 versions have no `expiresAt`.
- [ ] **Step 2:** Run `mvn -B test -Dtest=VersionRetentionIT` → FAIL.
- [ ] **Step 3:** Implement the interfaces above; delete `retirePreviousVersion` and its two call sites; update `ConfigurationServiceTest` expectations that relied on the old previous-version save.
- [ ] **Step 4:** Run `mvn -B clean test` → 0 failures.
- [ ] **Step 5:** Commit `fix(retention): keep the newest versions and expire older ones by exact date`.

### Task 3: Change stream as the single source of live events

**Files:**
- Modify: `src/main/java/com/vault/configmanager/service/ChangeStreamService.java`
- Modify: `src/main/java/com/vault/configmanager/service/ConfigurationService.java` (`notifyChangeStream`)
- Modify: `src/main/java/com/vault/configmanager/service/BinaryConfigService.java` (`notifyChangeStream`)
- Test: `src/test/java/com/vault/configmanager/integration/LiveEventsIT.java`; update `ChangeStreamServiceTest`

**Interfaces:**
- Produces in `ChangeStreamService`:
  - `enum EventMode { CHANGE_STREAM, FALLBACK }` and `EventMode getEventMode()`; `isChangeStreamActive()` keeps working.
  - `boolean servicesShouldBroadcast()` = `getEventMode() == FALLBACK`.
  - `void addListener(java.util.function.Consumer<ConfigChangeEvent> listener)`: invoked by `broadcastChange` for every event (used by tests; SSE emitters unchanged).
  - Startup (`@PostConstruct`): try to open a database-level `watch` with pipeline `$match: { operationType: "insert", "ns.coll": { $in: ["configurations", "binary_configurations"] } }` up to 3 times, 1 s apart, synchronously; success → `CHANGE_STREAM` mode and the read loop runs on the existing single daemon thread; failure → `FALLBACK` mode. Log the mode.
  - Read loop: store each event's resume token; on any exception reconnect with `resumeAfter(token)` (or from now if no token or the token is rejected, logging a warning), backoff 1 s doubling to 30 s, until shutdown. Never switch modes.
  - Mapping: `configurations` insert → `CREATE`, or `ROLLBACK` when `tags.rollback_from_version` exists; `binary_configurations` insert → `BINARY_CREATE`, or `BINARY_ROLLBACK` when its rollback marker exists (use the same marker `BinaryConfigService` writes today); version, hash, app, env from the inserted document; binary events carry `isBinary: true`.
  - Package-private `void closeCurrentCursorForTest()`: closes the active cursor so the loop must reconnect.
- In both services, `notifyChangeStream` broadcasts only when `changeStreamService.servicesShouldBroadcast()`.

- [ ] **Step 1: Write `LiveEventsIT`** (register a listener collecting events into a thread-safe list; await with a 10 s poll):
  - `modeIsChangeStream_onReplicaSet`.
  - `oneWrite_exactlyOneEvent`: one create → exactly one event, type `CREATE`, matching version; wait 2 s more → still one.
  - `twoQuickWrites_twoEventsInOrder`: two creates back to back → exactly two events, versions n and n+1 in order.
  - `retentionAndTtl_produceNoEvents`: 8 writes (retention updates), then expire v1 as in Task 2 → event count equals 8 exactly.
  - `rollback_isRollbackEvent`: rollback → one event of type `ROLLBACK`.
  - `binaryUpload_oneBinaryEvent`: upload via `BinaryConfigService` → one `BINARY_CREATE` with `isBinary == true`.
  - `reconnectAfterCursorClosed_noDuplicates`: `closeCurrentCursorForTest()`, then one write → exactly one event for it within 15 s.
- [ ] **Step 2:** Update `ChangeStreamServiceTest`: with a null `MongoTemplate`, mode is `FALLBACK` and `servicesShouldBroadcast()` is true; add a `ConfigurationServiceTest` case: when `servicesShouldBroadcast()` is false, `broadcastChange` is never called.
- [ ] **Step 3:** Run `mvn -B test -Dtest='LiveEventsIT,ChangeStreamServiceTest,ConfigurationServiceTest'` → FAIL.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** Run `mvn -B clean test` → 0 failures.
- [ ] **Step 6:** Commit `fix(events): make the change stream the single source of live events`.

### Task 4: Atomic bulk import with post-commit side effects

**Files:**
- Modify: `src/main/java/com/vault/configmanager/service/ConfigurationService.java` (`createConfiguration`, `bulkImport`)
- Test: `src/test/java/com/vault/configmanager/integration/BulkImportIT.java`; update `ConfigurationServiceTest`

**Interfaces:**
- Consumes: `VersionRetentionService.applyRetention` (Task 2), `ChangeStreamService.servicesShouldBroadcast` (Task 3).
- Produces: `createConfiguration(ConfigRequest, String, String)` keeps its signature and behaviour for single writes. Internally it is split into an insert step (placeholder resolution, encryption, hash, version, save; returns the saved document) and a post-commit step (cache evict, retention, fallback broadcast, audit `CREATE`, telemetry). The atomic path runs only insert steps inside the transaction and, after commit, runs the post-commit step for each saved document; retention and cache eviction run once per distinct app/env.
- Atomic path outcomes:
  - any entry fails → transaction rolls back; after it ends, one audit `BULK_IMPORT_ROLLBACK` (success false, reason with the failure count); `BulkTransactionException` with the per-entry errors list (unchanged shape).
  - all succeed → after commit, one audit `BULK_IMPORT_ATOMIC` plus per-entry `CREATE` audits.
  - `transactionManager == null` or the server rejects transactions → `BulkTransactionException` whose message contains "requires a replica set"; nothing written.

- [ ] **Step 1: Write `BulkImportIT`:**
  - `atomic_allValid_allCommittedAndAudited`: 3 entries over 2 apps → 3 versions stored; audit has `BULK_IMPORT_ATOMIC` and 3 `CREATE` for those apps.
  - `atomic_oneInvalid_nothingStored`: 2 valid + 1 invalid (environment `qa`, rejected by `$jsonSchema`) → `BulkTransactionException`; 0 documents for those apps; audit has exactly one `BULK_IMPORT_ROLLBACK` and no `CREATE` for them.
  - `atomic_sameAppTwice_consecutiveVersions`: 2 entries for one app/env → versions 1 and 2; cache entry for that app/env absent afterwards.
  - `bestEffort_unchanged`: 1 valid + 1 invalid → 1 stored, response reports 1 failure.
- [ ] **Step 2:** Unit test in `ConfigurationServiceTest`: service built with `transactionManager == null`, `bulkImport(..., atomic=true, ...)` → `BulkTransactionException` message contains "requires a replica set", repository `save` never called.
- [ ] **Step 3:** Run `mvn -B test -Dtest='BulkImportIT,ConfigurationServiceTest'` → FAIL.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** Run `mvn -B clean test` → 0 failures.
- [ ] **Step 6:** Commit `fix(bulk): run only inserts inside the transaction and side effects after commit`.

### Task 5: Ciphertext guard and encryption-at-rest proof

**Files:**
- Modify: `src/main/java/com/vault/configmanager/util/JsonTraverser.java`
- Modify: `src/main/java/com/vault/configmanager/service/ConfigurationService.java` (call the guard in the insert step from Task 4)
- Test: `JsonTraverserTest`, `ConfigurationServiceTest`, `src/test/java/com/vault/configmanager/integration/SecretStorageIT.java`

**Interfaces:**
- Produces: `public Optional<String> JsonTraverser.findEncryptedEnvelopePath(Document payload)`: returns the dotted path (e.g. `database.secure_settings.password`) of the first value inside any `secure_settings` object that `isAlreadyEncrypted` would accept; empty otherwise.
- The insert step calls it on the client payload (after placeholder resolution, before encryption) and throws `IllegalArgumentException` with the Global Constraints message, `<path>` being the path relative to the payload root. Rollback (which copies stored ciphertext) never calls it.

- [ ] **Step 1: Write tests:**
  - `JsonTraverserTest.findEncryptedEnvelopePath_detectsNestedEnvelope`: `{database:{secure_settings:{password:{<iv>, <ciphertext>, <algo>}}}}` → `database.secure_settings.password`; plain strings → empty; an envelope-shaped object outside `secure_settings` → empty.
  - `ConfigurationServiceTest.create_withEnvelope_rejected`: → `IllegalArgumentException` with the exact message; `save` never called.
  - `SecretStorageIT.storedDocumentNeverHoldsPlaintext`: create with `secure_settings.token = "plain-xyz-123"` → raw `configurations` document (via `MongoTemplate` as `org.bson.Document`) does not contain `plain-xyz-123` anywhere, including `searchableText`; reading through the service returns `plain-xyz-123`.
  - `SecretStorageIT.envelopeRejectedOverHttp`: POST `/api/v1/vault/config` with an envelope-shaped secret and the dev admin key → 400; no document stored.
  - `SecretStorageIT.bulkEnvelope_perEntryError`: atomic bulk with one envelope entry → 422 rollback; best-effort → only that entry fails.
- [ ] **Step 2:** Run them → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `mvn -B clean test` and `mvn -B -f starter/pom.xml test` → 0 failures.
- [ ] **Step 5:** Commit `fix(secrets): reject encrypted envelopes in client payloads`.
