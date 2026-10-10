# Secure Config Vault - Implementation Tasks

## Task 1: Project Scaffolding and MongoDB Driver Setup
Initialize a Spring Boot 3.x project with Java 17. Add dependencies for `spring-boot-starter-data-mongodb`, Jackson (JSON/YAML), and SnakeYAML. Set up `application.yml` with MongoDB connection settings and configure an `X-API-Key` security filter. Remember: Do not include Lombok.

## Task 2: Implement Cryptography and Integrity Hashing Utilities
Create a service utilizing the Java Cryptography Architecture (JCA). Implement AES-256-GCM encryption/decryption routines for strings. Implement a SHA-512 hashing utility that converts incoming dynamic JSON trees into canonical strings and returns their hashes.

## Task 3: Establish Data Models and Vault Documents
Create the `VaultDocument` entity to map `applicationName`, `environment`, `version`, `documentHash`, and a Map/JsonNode `payload`. Implement a compound unique index on `applicationName`, `environment`, and `version` to enforce immutability.

## Task 4: Automated Field-Level Encryption Interceptor
Create a MongoDB lifecycle event listener (or service layer interceptor) that scans the dynamic config `payload`. If any JSON node key matches `"secure_settings"`, automatically encrypt its value via the Task 2 AES module before saving it to MongoDB, and decrypt it upon retrieval.

## Task 5: Temporal Versioning and Rollback Engine
Build the core configuration service layer. Imbed the GitOps-style versioning logic: configurations must be immutable. Every update or rollback must query the highest current version number, increment it, and insert a fresh document. 

## Task 6: Audit Logging and Native Caching Collections
Create a MongoDB configuration to establish a Capped Collection for `AuditLog` records. Create a second collection for `CachedConfig` equipped with a native MongoDB TTL index set to expire documents after 5 minutes. Implement read hooks to serve hits from this cache collection.

## Task 7: Advanced Operations - Diffing, Dot-Notation Paths, and Bulk Import
Implement utility methods to compare two version payloads and produce structural diff keys. Write a path-traversal parser to locate nested elements via dot-notation (e.g., `"database.host"`). Add a transactional best-effort endpoint for bulk processing multiple configs.

## Task 8: Config Exporter and Aggregation Dashboard Endpoints
Build endpoints to transform configuration payloads into JSON, YAML, and flat `.env` file formats. Write a MongoDB Aggregation Pipeline query to collect system-wide telemetry, return storage size distributions, version counts, and active environments.