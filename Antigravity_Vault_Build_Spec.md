# Master Build Specification: Secure Config Vault

**Agent Context & Role:**
You are acting as a Senior Backend Security Engineer. You are tasked with generating, scaffolding, and writing the complete codebase for a highly secure, enterprise-grade configuration management system called the "Secure Config Vault." This document contains the entire architectural context, cryptographic logic, database schema, and advanced scaling requirements discussed up to this point.

Read all sections carefully before generating the Java Spring Boot codebase.

---

## 1. Technology Stack
*   **Language:** Java 17 LTS
*   **Framework:** Spring Boot 3.x
*   **Database:** MongoDB (via `spring-boot-starter-data-mongodb`) — the **only** data store. No Redis or external caches.
*   **Dependencies:** `spring-boot-starter-web`, `spring-boot-starter-validation`, `jackson-databind` (for dynamic JSON tree traversal), `jackson-dataformat-yaml` (for YAML export), `snakeyaml`.
*   **Cryptography:** Java Cryptography Architecture (JCA) utilizing `javax.crypto` (AES-256-GCM) and `java.security.MessageDigest` (SHA-512).

> **Note:** Lombok is intentionally excluded. All getters, setters, constructors, `equals()`, `hashCode()`, and `toString()` methods must be written explicitly so the codebase is fully transparent for review.

---

## 2. Project Directory Structure (Strict Enforcement)
Scaffold the project using this exact package structure `com.vault.configmanager`:

```text
src/main/java/com/vault/configmanager/
├── VaultApplication.java
├── controller/
│   ├── ConfigurationController.java
│   └── StatsController.java
├── model/
│   ├── VaultDocument.java
│   ├── AuditLog.java
│   └── CachedConfig.java
├── dto/
│   ├── request/
│   │   ├── ConfigRequest.java
│   │   ├── BulkConfigRequest.java
│   │   └── RollbackRequest.java
│   ├── response/
│   │   ├── ConfigResponse.java
│   │   ├── VersionSummary.java
│   │   ├── DiffResponse.java
│   │   ├── StatsResponse.java
│   │   └── ApiErrorResponse.java
│   └── EncryptedNodeDTO.java
├── repository/
│   ├── ConfigurationRepository.java
│   ├── AuditLogRepository.java
│   └── CachedConfigRepository.java
├── service/
│   ├── ConfigurationService.java
│   ├── CryptoService.java
│   ├── HashingService.java
│   ├── AuditService.java
│   ├── CacheService.java
│   └── ExportService.java
├── security/
│   ├── ApiKeyAuthFilter.java
│   └── KeyProvider.java
├── config/
│   └── MongoConfig.java
├── exception/
│   ├── ConfigNotFoundException.java
│   ├── IntegrityViolationException.java
│   ├── DuplicateVersionException.java
│   └── GlobalExceptionHandler.java
└── util/
    └── JsonTraverser.java
```

---

## 3. Core Database Models

### A. Configuration Document (`VaultDocument.java`)
Map this to a MongoDB collection named `configurations`. Implement Temporal Versioning (GitOps style): never update an existing document; always insert a new document with an incremented version number.

**Fields:**
*   `@Id String id;`
*   `@Indexed String applicationName;`
*   `@Indexed String environment;`
*   `int version;`
*   `String documentHash;` (SHA-512 integrity fingerprint)
*   `org.bson.Document payload;` (The dynamic JSON configuration tree)
*   `Map<String, String> tags;` (Arbitrary key-value metadata, e.g., `"release": "v2.1"`, `"deployed_by": "CI/CD"`)
*   `LocalDateTime createdAt;`

**Indexes:**
*   `@CompoundIndex(def = "{'applicationName': 1, 'environment': 1, 'version': 1}", unique = true)` — Prevents duplicate versions from concurrent writes.
*   Individual `@Indexed` on `applicationName` and `environment` for query flexibility.

### B. Audit Log (`AuditLog.java`)
Map this to a MongoDB **Capped Collection** named `audit_logs` (limit size to prevent DB bloat).

> **Note on Capped Collections:** Capped collections do not support `delete` operations and have fixed sizes. Set the cap size large enough for demo purposes (e.g., 50MB / 10,000 documents). You also cannot add secondary indexes after creation (only `_id` is available). This limitation should be documented in the project report.

**Fields:**
*   `@Id String id;`
*   `String action;` (e.g., `"READ"`, `"CREATE"`, `"ROLLBACK"`, `"EXPORT"`, `"BULK_IMPORT"`)
*   `String applicationName;`
*   `String environment;`
*   `String requestingIp;`
*   `String apiKey;` (masked, e.g., `"sk-****abcd"`)
*   `boolean success;`
*   `String failureReason;` (null if success is true)
*   `LocalDateTime timestamp;`

### C. Cached Configuration (`CachedConfig.java`)
Map this to a MongoDB collection named `config_cache`. This replaces an external Redis cache by using **MongoDB's native TTL index** feature.

**Fields:**
*   `@Id String cacheKey;` (Composite key: `"{applicationName}:{environment}"`)
*   `org.bson.Document decryptedPayload;` (The plaintext config — see security note below)
*   `LocalDateTime cachedAt;`

**TTL Index:**
*   Create a TTL index on `cachedAt` with an expiry of **300 seconds (5 minutes)**. MongoDB will automatically delete expired entries.

> **Security Consideration:** Storing decrypted payloads in the cache collection means plaintext secrets exist in MongoDB temporarily. For a production system, you would encrypt the cache values with a separate fast-rotation key. For this project, document this tradeoff in your report and note that the TTL auto-expiry limits the exposure window.

---

## 4. Security: API Key Authentication (`ApiKeyAuthFilter.java`)

Implement a `OncePerRequestFilter` that intercepts all `/api/**` requests:

1.  Extract the `X-API-Key` header from the incoming request.
2.  Compare it against a list of valid API keys loaded from `application.properties` (e.g., `vault.api.keys=sk-key1,sk-key2`).
3.  If the key is missing or invalid, return `401 Unauthorized` with an `ApiErrorResponse`.
4.  If valid, store the (masked) key in the request attributes for audit logging, and proceed.

### Key Provider Interface (`KeyProvider.java`)
Create an interface `KeyProvider` with a method `String getMasterKey()`. Implement `EnvironmentVariableKeyProvider` that reads the master encryption key from the environment variable `VAULT_MASTER_KEY`. This abstraction demonstrates separation of concerns and allows future KMS integration without code changes.

---

## 5. Cryptographic Engine Logic (`CryptoService.java` & `HashingService.java`)

### AES-256-GCM Encryption:
*   Never use ECB mode. Strictly implement AES in GCM (Galois/Counter Mode).
*   For *every single node* encrypted, generate a unique, cryptographically secure 12-byte Initialization Vector (IV) using `SecureRandom`.
*   Authentication Tag Length must be 128 bits.
*   The Master Data Encryption Key (DEK) is obtained via the `KeyProvider` interface.
*   The encryption method must return an `EncryptedNodeDTO` mapping to this structure:
    `{ "encryption_algo": "AES-256-GCM", "iv": "<Base64>", "cipher_text": "<Base64>" }`

### SHA-512 Hashing:
*   Use `MessageDigest.getInstance("SHA-512")`.
*   Hash the **canonical JSON string** (keys sorted alphabetically, consistent formatting via Jackson's `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`) of the payload *after* encryption, but *before* saving to MongoDB.
*   **Important:** Do NOT hash the BSON representation. BSON-to-string conversion is not deterministic across libraries. Always hash the canonical JSON string to ensure consistent verification.

---

## 6. The Dynamic JSON Traversal Middleware (`JsonTraverser.java`)

Use Jackson's `ObjectMapper`, `JsonNode`, and `ObjectNode` to navigate arbitrary configuration trees without strict POJO mapping.

### Encryption Traversal:
1.  Recursively scan the incoming `JsonNode` tree.
2.  If a node's key is exactly `"secure_settings"`, intercept its children.
3.  Pass the plaintext value of each child to `CryptoService`.
4.  Replace the original plaintext child node with the `EncryptedNodeDTO` JSON object returned by the CryptoService (containing the IV and Ciphertext).

### Decryption Traversal:
1.  Recursively scan for `"secure_settings"`.
2.  Extract the `iv` and `cipher_text` from each child node.
3.  Pass them to `CryptoService` for decryption.
4.  Replace the encrypted object with the restored plaintext value.

---

## 7. Caching via MongoDB TTL Collection (`CacheService.java`)

This service manages the `config_cache` collection as a read-through cache, replacing the need for Redis.

### Cache Read (on GET):
1.  Build the cache key as `"{applicationName}:{environment}"`.
2.  Query `config_cache` for the key.
3.  If found and not expired → **Cache Hit**. Return the `decryptedPayload` directly.
4.  If not found → **Cache Miss**. Proceed with full DB query, integrity check, and decryption.

### Cache Write (after successful decryption):
1.  Upsert into `config_cache` with the current `LocalDateTime` as `cachedAt`.
2.  The TTL index on `cachedAt` will auto-expire the entry after 5 minutes.

### Cache Invalidation (on POST/PATCH/Rollback):
1.  Delete the cache entry for the given `applicationName:environment` key immediately.
2.  This ensures the next read fetches fresh data.

---

## 8. API Endpoints & Business Flow (`ConfigurationController.java`)

### POST `/api/v1/vault/config` — Ingestion
1.  **Receive:** A `ConfigRequest` DTO containing the raw JSON configuration, application name, environment, and optional tags.
2.  **Encrypt:** Pass the payload to `JsonTraverser` to secure all `"secure_settings"` nodes.
3.  **Hash:** Pass the transformed JSON to `HashingService` to generate the canonical SHA-512 fingerprint.
4.  **Version:** Query MongoDB to find the highest version for this `app` + `env`. Increment by 1.
5.  **Persist:** Save the new `VaultDocument`.
6.  **Audit:** Trigger `AuditService` to log a successful `"CREATE"` action.
7.  **Cache Invalidation:** Delete the cached entry for this `app:env` from `config_cache`.
8.  **Return:** `201 Created` with `ConfigResponse` containing the document ID, version number, and hash.

### GET `/api/v1/vault/config/{app}/{env}` — Retrieval (Latest Version)
1.  **Cache Check:** Query `config_cache` for key `"{app}:{env}"`. If Cache Hit, log `"READ"` audit and return `200 OK`.
2.  **DB Query:** If Cache Miss, query MongoDB for the highest version document matching `app` and `env`. If not found, throw `ConfigNotFoundException`.
3.  **Integrity Check:** Convert the BSON payload to a canonical JSON string and run through `HashingService`. If the calculated hash does NOT match the stored `documentHash`, throw `IntegrityViolationException`, log a failed `"READ"` audit with failure reason, and abort.
4.  **Decrypt:** If the hash matches, pass the payload to `JsonTraverser` to decrypt the secure nodes.
5.  **Cache & Return:** Upsert the decrypted payload into `config_cache`, log a successful `"READ"` audit, and return `200 OK` with `ConfigResponse`.

### PATCH `/api/v1/vault/config/{app}/{env}` — Partial Update (Merge)
1.  **Receive:** A partial JSON payload in the request body.
2.  **Fetch Latest:** Query MongoDB for the current latest version of this `app` + `env`.
3.  **Deep Merge:** Merge the incoming partial JSON into the existing payload using Jackson's tree model (`JsonNode` deep merge).
4.  **Encrypt → Hash → Version → Persist → Audit → Cache Invalidate** — same as POST flow.
5.  **Return:** `201 Created` with the new version's `ConfigResponse`.

### GET `/api/v1/vault/config/{app}/{env}/versions` — List All Versions
1.  Query all `VaultDocument` entries for the given `app` + `env`, sorted by version descending.
2.  Return a list of `VersionSummary` DTOs (version number, hash, tags, createdAt). Do NOT include the full payload.

### POST `/api/v1/vault/config/{app}/{env}/rollback/{version}` — Rollback
1.  Fetch the `VaultDocument` matching `app`, `env`, and the specified `version`.
2.  If not found, throw `ConfigNotFoundException`.
3.  **Re-insert** this document as a **new version** (the next version number). Do NOT delete or mutate any existing documents.
4.  Audit log: `"ROLLBACK"`.
5.  Cache Invalidation.
6.  **Return:** `201 Created` with the new `ConfigResponse`.

### GET `/api/v1/vault/config/{app}/{env}/diff` — Diff Two Versions
*   **Query Params:** `v1` (int) and `v2` (int).
1.  Fetch both versions from MongoDB. If either is not found, throw `ConfigNotFoundException`.
2.  Decrypt both payloads.
3.  Perform a recursive key-by-key comparison of the two JSON trees.
4.  Return a `DiffResponse` containing:
    *   `added` — keys present in `v2` but not `v1`.
    *   `removed` — keys present in `v1` but not `v2`.
    *   `modified` — keys present in both but with different values (show old and new).
    *   `unchanged` — count of keys that are identical.

### GET `/api/v1/vault/config/{app}/{env}/search` — Search by Key Path
*   **Query Param:** `path` (string, e.g., `"database.host"` or `"secure_settings.api_key"`).
1.  Fetch the latest version for `app` + `env`.
2.  Decrypt the payload.
3.  Navigate the JSON tree using the dot-separated path.
4.  Return the value at that path, or `404` if the path does not exist.
5.  This demonstrates MongoDB's dot-notation query capability on nested documents.

### GET `/api/v1/vault/config/{app}/{env}/export` — Export Configuration
*   **Query Param:** `format` (string, one of: `json`, `yaml`, `env`).
1.  Fetch and decrypt the latest version.
2.  Convert using `ExportService`:
    *   `json` — return the raw JSON.
    *   `yaml` — use `jackson-dataformat-yaml` to convert JSON → YAML.
    *   `env` — flatten all leaf keys into `KEY=VALUE` format (e.g., `DATABASE_HOST=localhost`). Nested keys are joined with `_` and uppercased.
3.  Return with appropriate `Content-Type` header (`application/json`, `application/x-yaml`, `text/plain`).
4.  Audit log: `"EXPORT"`.

### POST `/api/v1/vault/config/bulk` — Bulk Import
1.  **Receive:** A `BulkConfigRequest` DTO containing an array of `ConfigRequest` objects.
2.  Process each entry through the full POST ingestion flow (encrypt → hash → version → persist).
3.  If any entry fails, log the failure but continue processing the rest (**best-effort, not transactional**).
4.  Audit log: `"BULK_IMPORT"` with count of successes and failures.
5.  **Return:** `200 OK` with a summary: total processed, succeeded, failed, and error details per failed entry.

---

## 9. Dashboard & Statistics (`StatsController.java`)

### GET `/api/v1/vault/stats` — System Dashboard
Use MongoDB **Aggregation Pipeline** to compute:
*   Total number of unique applications.
*   Total number of unique environments.
*   Total number of configuration versions stored.
*   Average versions per application.
*   Most recently updated application + environment.
*   Total audit log entries (count on capped collection).
*   Cache collection document count.

Return as a `StatsResponse` DTO. This endpoint demonstrates MongoDB's aggregation framework.

---

## 10. MongoDB TTL Auto-Expiry on Old Versions (Optional Cleanup)

Add a `@Indexed(expireAfter = "90d")` on a field `expiresAt` in `VaultDocument`:
*   When a new version is created, set `expiresAt = LocalDateTime.now().plusDays(90)` on the document.
*   **Exception:** The latest version for any `app` + `env` combination should have `expiresAt = null` (TTL indexes ignore null values, so the latest version is never deleted).
*   This auto-cleans old versions after 90 days without manual intervention and demonstrates a powerful MongoDB-native feature.

> **Note:** This is optional. If you prefer to keep all history indefinitely, omit this field. Either choice should be documented and justified in your project report.

---

## 11. Exception Handling (`GlobalExceptionHandler.java`)

Implement a `@RestControllerAdvice` class that catches all custom exceptions and returns standardized `ApiErrorResponse` DTOs:

| Exception | HTTP Status | Message |
|-----------|-------------|---------|
| `ConfigNotFoundException` | `404 Not Found` | Configuration not found for the given app/env/version. |
| `IntegrityViolationException` | `409 Conflict` | Document integrity check failed. Possible tampering detected. |
| `DuplicateVersionException` | `409 Conflict` | A version conflict occurred. Please retry. |
| `IllegalArgumentException` | `400 Bad Request` | Invalid request parameters. |
| `SecurityException` | `401 Unauthorized` | Authentication failed. |
| `Exception` (fallback) | `500 Internal Server Error` | An unexpected error occurred. |

All error responses must also trigger an audit log entry with `success = false` and the failure reason.

---

## 12. Application Configuration (`application.properties`)

```properties
# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/secure_config_vault
spring.data.mongodb.database=secure_config_vault

# Vault Security
vault.master.key=${VAULT_MASTER_KEY}
vault.api.keys=sk-dev-key-001,sk-dev-key-002

# Cache TTL (seconds)
vault.cache.ttl=300

# Old Version Expiry (days) — set to 0 to disable
vault.version.expiry-days=90

# Capped Collection Config
vault.audit.cap-size-mb=50
vault.audit.max-documents=10000
```

---

## 13. Testing & Validation Checklist

Before submission, verify:
- [ ] All `secure_settings` nodes are encrypted in MongoDB (inspect raw documents).
- [ ] Decrypted responses return plaintext values correctly.
- [ ] SHA-512 hash matches on clean reads; tamper a document manually in MongoDB and verify the `IntegrityViolationException` fires.
- [ ] Version numbers increment correctly; no duplicates exist (test concurrent writes).
- [ ] Rollback creates a new version (does NOT delete/mutate anything).
- [ ] Diff endpoint correctly identifies added, removed, and modified keys.
- [ ] Search endpoint navigates nested keys via dot-notation.
- [ ] Export works for all three formats (JSON, YAML, .env).
- [ ] Bulk import handles partial failures gracefully.
- [ ] API key authentication blocks unauthenticated requests.
- [ ] Audit logs capture all actions with correct metadata.
- [ ] Cache entries auto-expire after TTL (wait 5+ minutes and verify).
- [ ] Stats endpoint returns correct aggregation results.
- [ ] Old versions auto-expire after the configured TTL (if enabled).
