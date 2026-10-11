# Secure Config Vault - Full-Stack Implementation Roadmap

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

## Task 9: Initialize React Next.js 15 Dashboard Frontend Stack
Scaffold a Next.js 15 workspace directory inside a `frontend/` root folder. Configure Tailwind CSS and inject global typography import pairings (Cormorant Garamond and Montserrat) utilizing the visual taxonomy guidelines inside the globally installed ui ux pro max skill dataset.

## Task 10: Build Advanced Scoped API Key Engine and multi-document ACID Transactions
Implement an `api_keys` MongoDB collection enforcing granular application and environment role restrictions. Add Spring Boot's `MongoTransactionManager` to the bulk configuration import logic, allowing full rollback across a replica set via an optional `?atomic=true` parameter if a single validation check fails.

## Task 11: Real-Time Updates via MongoDB Change Streams and SSE Channels
Build a configuration event watch engine that hooks into MongoDB's native Change Stream (`collection.watch()`) architecture. Wire this reactive oplog framework to a Server-Sent Events (SSE) controller to push live metadata modifications directly to active client components without pulling down background network polls.

## Task 12: Implement MongoDB GridFS Binary Blob Storage Engine
Integrate MongoDB GridFS handlers into the vault storage layer. Support secure binary configuration payload uploads (e.g., TLS certificates, `.jks` keystores, `.pem` bundles) utilizing the exact same metadata schemas and temporal versioning modules created in Task 3 and Task 5.

## Task 13: Establish Envelope Encryption, Encrypted Caching, and Secret Masking
Refactor the Task 2 cryptography engine to support Envelope Encryption by encrypting individual configuration blocks with unique Data Encryption Keys (DEKs), which are managed by a parent Key Encryption Key (KEK) to enable fluid key rotation. Add in-memory ephemeral key wrappers to encrypt data sitting inside the MongoDB TTL cache collection, and add a utility to dynamically mask sensitive fields (e.g., `"password": "********"`) inside audit histories.

## Task 14: Integrate OpenAPI Swagger UI and Custom Spring Boot Client Starter
Add `springdoc-openapi-starter-webmvc-ui` to expose an interactive API dashboard at `/swagger-ui.html`. Build a separate custom Spring Boot property source locator client module equipped with an automated `PropertySourceLocator` configuration hook, allowing external microservices to bind their properties dynamically from the vault on startup.

## Task 15: Generate UI Layout and Configuration Management Console via 21st dev mcp
Call your active 21st dev mcp server tools to search for, browse, and inject a high-density, production-ready dashboard application frame layout. Build a responsive configuration data grid screen containing an interactive JSON tree data editor node, applying clean focus states and resilient label formatting guidelines extracted out of the ui ux pro max skill design catalog.

## Task 16: Build Revision History Timeline, Colorized Diff Viewer, and Telemetry Cards
Create an interactive version control pane featuring a visual Git-style timeline path. Call the connected 21st dev mcp server to source layout primitives for a colorized side-by-side diff component (rendering structural updates as green for added, red for deleted, and yellow for edited keys) combined with real-time analytics monitoring charts driven by the Task 8 MongoDB Aggregation Pipeline telemetry endpoint.
