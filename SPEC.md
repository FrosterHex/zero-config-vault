# Secure Config Vault - Specification

## 1. Overview
The Secure Config Vault is a highly secure, enterprise-grade configuration management system. It acts as a centralized repository for dynamic configuration files, providing robust versioning, encryption, integrity checking, and auditing, while using MongoDB as the exclusive data store.

## 2. Technology Stack & Architecture
* **Language:** Java 17 LTS
* **Framework:** Spring Boot 3.x
* **Database:** MongoDB (using `spring-boot-starter-data-mongodb`)
* **Core Libraries:** `jackson-databind`, `jackson-dataformat-yaml`, `snakeyaml`
* **Cryptography:** Java Cryptography Architecture (JCA) - AES-256-GCM and SHA-512.

## 3. Core Features
* **Dynamic Configuration Storage:** Arbitrary JSON tree configurations are stored using MongoDB's flexible schema.
* **Temporal Versioning (GitOps Style):** Configurations are immutable. Any update or rollback creates a new document with an incremented version number.
* **Automated Field-Level Encryption:** Any JSON node with the key `"secure_settings"` is automatically intercepted, encrypted via AES-256-GCM, and stored securely.
* **Integrity Hashing:** Every configuration is hashed using SHA-512 on its canonical JSON string to detect tampering.
* **Database-Native Caching:** Implements caching natively via MongoDB TTL (Time-To-Live) collections, eliminating the need for an external Redis cluster.
* **Audit Logging:** All read, create, rollback, and export actions are tracked in a MongoDB Capped Collection to prevent unlimited database bloat.
* **Advanced Config Operations:**
  * **Diffing:** Compare any two versions to identify added, removed, or modified keys.
  * **Rollbacks:** Instantly revert to a prior configuration state (implemented by creating a new version of the prior state).
  * **Bulk Import:** Process multiple configuration changes in a single request (best-effort execution).
  * **Exporting:** Download configurations in JSON, YAML, or `.env` formats.
  * **Path Searching:** Retrieve specific values using dot-notation paths (e.g., `"database.host"`).
* **System Dashboard:** Aggregation Pipeline metrics detailing usage, version counts, and storage statistics.
* **API Key Authentication:** All endpoints are protected by an `X-API-Key` filter mapping to authorized keys.

## 4. Data Models
1. **Configurations (`VaultDocument`):** Stores `applicationName`, `environment`, `version`, `documentHash`, and the dynamic `payload`. Compound indexed by app, env, and version to prevent conflicts.
2. **Audit Logs (`AuditLog`):** Capped collection recording actions, IPs, API keys, and success/failure states.
3. **Cache (`CachedConfig`):** TTL collection holding decrypted payloads for up to 5 minutes to optimize repeated reads.
