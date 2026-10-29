# Zero-Config Vault: System Specification

This document describes what the system does and how it is built, as implemented. [README.md](README.md) covers running and deploying it; [DECISIONS.md](DECISIONS.md) explains why it is built this way.

## 1. Overview

Zero-Config Vault stores configuration and secrets for microservices. MongoDB is its only data store: it also provides the cache, the audit log, telemetry, live change notifications and binary file storage. The system has three parts:

1. **Backend** (repository root): a Spring Boot REST and Server-Sent Events service.
2. **Client starter** (`starter/`): `vault-spring-boot-starter`, a library that loads a service's configuration from the vault at startup.
3. **Dashboard** (`frontend/`): a Next.js application with a server-side proxy, so the browser never holds an API key.

## 2. Technology

| Part | Technology |
| :--- | :--- |
| Backend | Java 17, Spring Boot 3.2.5 (Web, Data MongoDB, Validation), Jackson JSON and YAML, Springdoc OpenAPI, JCA (AES-256-GCM, SHA-512, SHA-256) |
| Database | MongoDB 7: Atlas M0 in production, a single-node replica set `rs0` locally |
| Starter | Spring Boot auto-configuration, an `EnvironmentPostProcessor`, Spring Cloud Context's `PropertySourceLocator`, and the Java HTTP client for SSE |
| Dashboard | Next.js 15.5 (App Router) with React 19, Tailwind CSS 4, Framer Motion, Three.js, Lucide icons; fonts Cormorant Garamond, Montserrat and JetBrains Mono |
| Tests | JUnit 5 and Mockito; Testcontainers (`mongo:7.0` replica set) for integration tests; Vitest for the dashboard |
| Hosting | Render (backend, Docker), Vercel (dashboard), MongoDB Atlas (database) |

Lombok is not used; all accessors and constructors are written out.

## 3. Behaviour

### 3.1 Versioned configuration

- A configuration is identified by `applicationName` and `environment`. Each write inserts a new document with `version` = highest existing version + 1. Creates, partial updates (`PATCH`, merged into the latest payload), rollbacks and bulk imports all work this way.
- A compound unique index on `{applicationName, environment, version}` rejects a concurrent duplicate version with `409 Conflict`.
- A rollback copies the stored payload of the target version into a new version tagged `rollback_from_version`.
- A diff compares two versions by dot-notation path and returns `added`, `removed`, `modified` (with `oldValue` and `newValue`) and `unchangedCount`.
- A path query returns the value at one dot-notation path, for example `server.ssl.protocol`.

### 3.2 Retention of old versions

- The current version and the newest `K` superseded versions never expire (`vault.version.retain-count`, default 5).
- When a version falls out of that window it gets `expiresAt` = now + `D` days (`vault.version.expiry-days`, default 90). A date already set is not moved.
- A TTL index on `expiresAt` with `expireAfterSeconds: 0` deletes each document on its stored date.
- The policy is applied after every committed write and once at startup for all configurations. The startup pass runs before the TTL index is switched to exact dates; if the pass fails, the index is left unchanged.
- If the database user may not run `collMod`, the index is dropped and recreated instead.
- `expiry-days` of 0 or less disables expiry and clears pending dates.
- Binary versions are not subject to retention.

### 3.3 Encryption and integrity

- Every value under a `secure_settings` object, at any depth, is encrypted before storage. Each value gets a random 256-bit data key (DEK), and is encrypted with AES-256-GCM (random 12-byte IV, 128-bit tag). The DEK is encrypted with the master key (KEK) from `VAULT_MASTER_KEY`.
- The stored envelope is an object with `encryption_algo`, `iv`, `cipher_text` and `encrypted_dek`.
- Encryption happens in the service's insert step. A Spring Data `BeforeConvertEvent` listener also encrypts any plaintext secret that reaches the repository another way.
- A client may not submit a value already shaped like an envelope. The request is rejected with `400` ("`<path>` must be a plain value; encrypted envelopes cannot be submitted"). Rollback copies stored ciphertext internally and is not affected.
- `documentHash` is a SHA-512 hash of the canonical (key-sorted) JSON of the encrypted payload. Reads recompute it and return `409 Conflict` on mismatch.
- `searchableText` (used by the text index) contains key paths and non-secret values only.
- Outside the `dev` profile the backend refuses to start with a missing, short or published development master key. On startup it encrypts any plaintext secret left by older builds.

### 3.4 Access control

- Clients send an API key in `X-API-Key`. `GET` requests to `/stream/...` and `/binaries/...` may pass it as `?apiKey=` instead, for `EventSource` and download links.
- `api_keys` stores the SHA-256 hash of each key, a display prefix, roles, allowed applications, allowed environments, an optional expiry and the last-use time.
- `ApiKeyAuthFilter` decides the required role from the request:
  - `/keys` and the global stream need `ADMIN`.
  - Paths containing `/export` need `EXPORT`; paths containing `/rollback` need `ROLLBACK`.
  - Other writes need `WRITE`; other reads need `READ`.
- The filter then checks the key's application and environment scope, and audits denied requests as `AUTH_DENIED` or `SCOPE_DENIED`. `ADMIN` implies every role.
- Secret values are returned only to keys with `REVEAL_SECRETS` (or `ADMIN`). For everyone else, values under `secure_settings` and sensitive key names (such as `password`, `token`, `api_key`, `client_secret`) are replaced with `********`. This applies in reads, history, diffs, exports, path queries and audit entries.
- `?masked=true` forces masking for any key. When a saved payload contains `********` for a secret, the existing stored value is kept.
- Development keys (`sk-dev-key-001`, `sk-dev-guest-read`, `sk-dev-guest-sandbox` and others) are seeded only in the `dev` profile and deactivated in any other profile. A production deployment seeds its first admin key from `VAULT_BOOTSTRAP_ADMIN_KEY`.

### 3.5 Live updates

- One database-level change stream watches inserts into `configurations` and `binary_configurations`, and maps each insert to a `ConfigChangeEvent`:
  - `eventType` is `CREATE`, or `ROLLBACK` when the tags carry `rollback_from_version`.
  - The event also carries `applicationName`, `environment`, `version`, `documentHash` and `timestamp`.
  - Binary events carry `isBinary: true` in their metadata.
- `GET /stream/{app}/{env}` and the admin-only `GET /stream` deliver these as SSE events named `CONFIG_CHANGE`, after an `INIT` event on connect.
- Updates and deletes produce no events, so retention marking and TTL deletions are silent.
- The backend keeps the last resume token and reconnects with back-off from 1 s doubling to 30 s. If the server rejects the token (error codes 260, 280 or 286), it restarts from the current time.
- The event source is chosen once at startup and logged. It is `CHANGE_STREAM` if the stream opens, otherwise `FALLBACK`, in which the write path broadcasts after each commit. Each write therefore produces exactly one event.

### 3.6 Bulk import

- `POST /config/bulk` with `{"configs": [...]}`.
- **Best effort (default):** each entry is saved independently and the response reports per-entry errors.
- **Atomic (`?atomic=true`):** only the inserts run inside a MongoDB transaction.
  - After commit: cache eviction, retention, audit entries and (in fallback mode) live events.
  - If any entry fails: nothing is stored, the response is `422`, and a `BULK_IMPORT_ROLLBACK` audit entry is written after the transaction ends.
  - Without transaction support: the request fails with `422` and "atomic bulk import requires a replica set".

### 3.7 Other features

- **Cache:** `config_cache` holds the latest version per application and environment for 300 seconds (TTL index). Entries are encrypted again with an AES-256 key held only in memory, which is regenerated when the process restarts.
- **Audit:** every operation is written to the capped `audit_logs` collection (50 MB, 10,000 documents). `GET /audit` returns recent entries, masked as in section 3.4.
- **Telemetry:** each read, write and rollback records latency, cache hit and status code in the time-series collection `config_access_telemetry`. The record's metadata holds application, environment, operation and client IP. `GET /stats/telemetry` buckets the data for the dashboard.
- **Statistics:** `GET /stats` runs one `$facet` aggregation that returns counts per environment, versions per application, and stored size per document (`$bsonSize`).
- **Schema validation:** `configurations` has a `$jsonSchema` validator (required fields, application-name pattern `^[a-zA-Z0-9_-]+$`, environment one of `development`, `staging`, `production`, `dr`). `GET /stats/schema` returns the rules.
- **Search:** `GET /config/search?query=` uses the text index on `applicationName`, `searchableText` and `environment`.
- **Export:** JSON, YAML or `.env`. The `.env` keys are flattened paths in upper case with `.` and `-` replaced by `_`.
- **Binary files:** stored in the GridFS bucket `vault_binaries`, with versioned metadata in `binary_configurations`:
  - the SHA-512 hash of the bytes, the size and the content type;
  - a unique index on `{applicationName, environment, filename, version}`.

  Each upload of the same file name creates a new version, and binary versions can be rolled back.

### 3.8 Client starter

- **Startup:** reads `vault.uri`, `vault.application-name` (default `spring.application.name`), `vault.environment` (default the first active profile), `vault.api-key`, `vault.fail-on-error`, `vault.stream-enabled` and `vault.timeout`.
- **Loading:** fetches `GET /config/{app}/{env}` and adds the flattened payload to the Spring `Environment` with the lowest precedence. If the vault is unreachable, startup continues with local settings unless `fail-on-error` is true.
- **Live changes:** with `stream-enabled: true`, a background SSE client publishes a `VaultConfigRefreshedEvent` for each message and reconnects after 5 seconds on failure. The application decides how to apply the change.

### 3.9 Dashboard

- **Pages:**
  - `/dashboard`: statistics and telemetry.
  - `/configs`: tree editor, version timeline, rollback, integrity badge, secret reveal for admins.
  - `/diff`: split and unified views.
  - `/binaries`: GridFS file manager.
  - `/export`.
  - `/audit`: recent entries, reloaded on each live event.
  - `/access`: API key management for admins.
  - `/nosql`: an overview of the MongoDB features.
- **Server-side proxy:** `/api/vault/[...path]` forwards requests to the backend and adds the key for the session's role.
  - Guests use a read key, and a sandbox key that may write only to the `playground` application.
  - Admins use the admin key.
  - The proxy retries idempotent reads while the backend wakes up, with a 60-second limit.
- **Login:** `/api/auth/login` checks `ADMIN_PASSWORD` and sets an HMAC-signed `vault_session` cookie; `/api/auth/logout` and `/api/auth/session` complete the flow.
- **Motion:** animations use Framer Motion and switch off when the user prefers reduced motion.

## 4. Data model

| Collection | Type | Fields |
| :--- | :--- | :--- |
| `configurations` | normal, `$jsonSchema` validator | `applicationName`, `environment`, `version`, `documentHash`, `payload` (secrets as envelopes), `tags`, `createdAt`, `expiresAt` (TTL, exact date), `searchableText` (text index) |
| `binary_configurations` | normal, unique index on `{applicationName, environment, filename, version}` | `applicationName`, `environment`, `filename`, `contentType`, `version`, `gridFsFileId`, `fileSizeBytes`, `documentHash` (SHA-512 of the bytes), `createdAt` |
| `vault_binaries.files`, `vault_binaries.chunks` | GridFS bucket | file metadata and 255 KB chunks |
| `config_cache` | TTL (300 s on `cachedAt`) | `cacheKey`, `encryptedCiphertext`, `iv`, `cachedAt` |
| `audit_logs` | capped (50 MB, 10,000 documents) | `action`, `applicationName`, `environment`, `requestingIp`, `apiKey` (masked), `success`, `failureReason`, `timestamp` |
| `config_access_telemetry` | time series (`timestamp`, `metadata`, seconds) | `timestamp`, `metadata {applicationName, environment, operation, clientIp}`, `latencyMs`, `cacheHit`, `statusCode` |
| `api_keys` | normal, unique index on `hashedSecret` | `name`, `keyPrefix`, `hashedSecret` (SHA-256), `allowedApps`, `allowedEnvironments`, `roles`, `active`, `createdAt`, `expiresAt`, `lastUsedAt` |

## 5. Repository layout

```
zero-config-vault/
├── pom.xml                      # Backend Maven build (the starter has its own pom)
├── Dockerfile                   # Backend image (Render)
├── docker-compose.yml           # MongoDB replica set, backend and dashboard for local use
├── docker/init-mongo.js         # Creates the capped and time-series collections
├── README.md, SPEC.md, DECISIONS.md, SHARDING.md
├── src/main/java/com/vault/configmanager/
│   ├── VaultApplication.java
│   ├── config/                  # MongoConfig (indexes, validator, TTL), seeding, startup runners, lifecycle listener, OpenAPI
│   ├── controller/              # Configuration, BinaryConfig, Export, Stats, Stream, Audit, ApiKey
│   ├── dto/                     # Request and response types
│   ├── exception/               # GlobalExceptionHandler and domain exceptions
│   ├── model/                   # VaultDocument, BinaryConfigDocument, CachedConfig, AuditLog, AccessTelemetry, ApiKey
│   ├── repository/              # Spring Data repositories
│   ├── security/                # ApiKeyAuthFilter, SecretVisibility, KeyProvider
│   ├── service/                 # Configuration, retention, crypto, hashing, cache, audit, telemetry, stats, export, change stream, binaries, API keys
│   └── util/                    # JsonTraverser, ConfigDiffUtil, SecretMaskingUtil
├── src/main/resources/          # application.yml, application-dev.yml
├── src/test/java/...            # Unit tests and Testcontainers integration tests (*IT)
├── starter/                     # vault-spring-boot-starter (separate Maven build)
└── frontend/                    # Next.js dashboard
    └── src/
        ├── app/                 # Pages and route handlers (api/vault proxy, api/auth)
        ├── components/          # Dashboard shell, auth, diff viewer, timeline, JSON tree editor, 3D and motion
        ├── context/             # VaultContext (role, session, backend wake state)
        └── lib/                 # API client; server/ holds session, proxy policy and config
```
