# Secure Config Vault - System Specification

## 1. Overview
The **Secure Config Vault** (Zero-Config Vault) is an enterprise-grade, centralized configuration and secret management platform built exclusively on **MongoDB**. It delivers GitOps-style temporal versioning, field-level and envelope encryption, cryptographic integrity verification, native database caching, change data capture, and high-performance audit logging.

The system features a multi-tiered architecture comprising:
1. **Core Vault Engine:** Spring Boot 3.x REST & SSE backend powered by MongoDB native features.
2. **Integration Starter:** `vault-spring-boot-starter` for zero-boilerplate microservice client auto-configuration.
3. **Enterprise Dashboard:** A full-stack Next.js 15 (App Router) Single Page Application styled with Tailwind CSS, shadcn/ui, custom editorial typography, and UI blocks curated via `21st.dev`.

---

## 2. Technology Stack & Full-Stack Architecture

### 2.1 Backend Services
* **Language & Runtime:** Java 17 LTS
* **Framework:** Spring Boot 3.2.x (Web, Reactive MongoDB Streams, Validation)
* **Database & Driver:** MongoDB 7.x (`spring-boot-starter-data-mongodb`)
* **Serialization & Parsing:** `jackson-databind`, `jackson-dataformat-yaml`, `snakeyaml`
* **Cryptography:** Java Cryptography Architecture (JCA) - AES-256-GCM, SHA-512, SHA-256
* **API Documentation:** Springdoc OpenAPI 3 (`springdoc-openapi-starter-webmvc-ui`)

### 2.2 Client Integration Layer (`vault-spring-boot-starter`)
* **Framework:** Spring Boot Auto-Configuration & Spring Cloud Config abstraction
* **Bootstrap Hook:** Custom `PropertySourceLocator` injecting vault configurations into Spring's `Environment`
* **Live Refresh:** Background SSE listener triggering dynamic `@RefreshScope` reloads

### 2.3 Frontend Dashboard System
* **Framework:** Next.js 15 (App Router, React 19, Server & Client Components)
* **Styling & Components:** Tailwind CSS, shadcn/ui, Radix UI primitives, Lucide Icons
* **Animation & Micro-Interactions:** Framer Motion (with strict prefers-reduced-motion accessibility compliance)
* **UI/UX Pro Max Guidelines:** High-density, accessible layout metrics (8pt grid, WCAG AAA contrast ratios)
* **Typography:**
  * **Display / Editorial Headings:** *Cormorant Garamond* (Google Fonts)
  * **Body / Telemetry Labels:** *Montserrat* (Google Fonts)
  * **Code / Diff / Hashes:** *JetBrains Mono* (Google Fonts)
* **Component Acceleration:** Component architectures and UI templates sourced via `21st.dev` MCP tools

---

## 3. Core Architectural Modules & Features

### 3.1 Advanced NoSQL Architecture (MongoDB Superpowers)
* **Temporal Versioning & Immutability:** Configurations are append-only. Updates and rollbacks calculate the highest existing revision number, increment it, and insert a new immutable document.
* **Reactive Change Streams via Server-Sent Events (SSE):**
  * Subscribes to MongoDB Change Streams (`collection.watch()`) on the `configurations` collection.
  * Exposes `GET /api/v1/vault/stream/{app}/{env}` via Spring `SseEmitter`.
  * Pushes live `ConfigChangeEvent` notifications to connected microservices and dashboards with zero polling overhead.
* **Multi-Document ACID Transactions:**
  * Bulk ingestion endpoint `POST /api/v1/vault/config/bulk?atomic=true` backed by Spring's `MongoTransactionManager`.
  * Guarantees atomic all-or-nothing rollback across replica sets if any single configuration entry in the payload fails validation or schema constraints.
* **MongoDB GridFS Binary Configuration Store:**
  * Dedicated GridFS bucket (`vault_binaries`) for assets exceeding the 16MB BSON limit (e.g., TLS certificates, `.jks` keystores, `.pem` bundles, and enterprise license keys).
  * Stores binary chunks in `fs.chunks` and associates versioned metadata in `binary_configurations` with SHA-512 file integrity fingerprints.
* **Native TTL Caching:**
  * `config_cache` collection equipped with a 300-second (5 min) TTL index for sub-millisecond read hits without Redis.
* **Capped Collection Auditing:**
  * Fixed-size 50MB `audit_logs` capped collection with automatic oldest-document eviction, preventing unbounded disk growth.
* **Aggregation Pipeline Telemetry:**
  * Multi-faceted `$facet` pipeline computing system-wide telemetry: active environments, per-app version counts, and storage size distributions via `$bsonSize: "$$ROOT"`.

### 3.2 Security & Zero-Trust Architecture
* **Cryptographic Envelope Encryption:**
  * Each document is protected by an ephemeral 256-bit AES **Data Encryption Key (DEK)** generated at runtime.
  * The DEK is encrypted using a Master **Key Encryption Key (KEK)** derived from `KeyProvider` and stored alongside the document (`encryptedDek`).
  * Enables seamless, zero-downtime key rotation: rotating the master KEK only requires re-encrypting the 32-byte DEKs, never the underlying configuration payloads.
* **Automated Field-Level Encryption (FLE):**
  * Spring Data MongoDB `BeforeConvertEvent` listener and `JsonTraverser` recursively intercept any JSON key named `"secure_settings"`.
  * Values are encrypted via AES-256-GCM (12-byte random IV, 128-bit authentication tag) before persistence and decrypted upon retrieval.
* **Encrypted Cache Layer (Zero-Disk Plaintext Leakage):**
  * Cache entries in `config_cache` are encrypted using an in-memory, auto-rotating ephemeral symmetric key, ensuring sensitive secrets never sit unencrypted on disk even in temporary TTL collections.
* **Deterministic Canonical SHA-512 Hashing:**
  * Payloads are transformed into alphabetically sorted, deterministic canonical JSON prior to SHA-512 fingerprinting, avoiding BSON serialization discrepancies.
  * Every read verifies hash integrity, rejecting tampered records with `409 Conflict`.
* **Scoped API Keys & Role-Based Access Control (RBAC):**
  * Backed by an `api_keys` collection storing prefix identifiers, SHA-256 token digests, allowed applications, allowed environments (`dev`, `staging`, `prod`), and fine-grained permissions (`READ`, `WRITE`, `ROLLBACK`, `EXPORT`, `ADMIN`).
  * `ApiKeyAuthFilter` validates credentials and enforces least-privilege authorization rules per request.
* **Dynamic Secret Masking Filter:**
  * Endpoints support `?masked=true` (or enforce masking for non-admin tokens) to replace sensitive values inside `"secure_settings"` with `"********"` across diff responses, history views, and exports.

### 3.3 Developer Experience (DX) & Integrations
* **OpenAPI 3 / Swagger Documentation:**
  * Interactive documentation exposed at `/swagger-ui.html` and `/v3/api-docs` configured with `X-API-Key` security scheme.
* **Multi-Format Configuration Exporter:**
  * Transforms stored configurations into pretty JSON, YAML, or flat `.env` format (normalizing nested paths to uppercase snake_case).
  * Direct file download endpoints (`/api/v1/vault/export/{app}/{env}`).
* **Spring Boot Client Auto-Configuration Starter:**
  * Microservices simply include `vault-spring-boot-starter` and set `vault.url`, `vault.app-name`, and `vault.api-key`.
  * Implements `PropertySourceLocator` to auto-populate Spring's `@Value` and `@ConfigurationProperties` at boot time, and connects to the SSE change stream for live reload.

---

## 4. Frontend Dashboard System Specification

### 4.1 Architecture & Tech Stack
* **Framework:** Next.js 15 (App Router, Server Actions, Client Components)
* **Styling:** Tailwind CSS with CSS Variables for theme tokens
* **Component Primitives:** shadcn/ui & Radix UI
* **Animation & Fluid Micro-Interactions:** Framer Motion
* **Design Acceleration:** Components and templates sourced via `21st.dev` MCP server
* **Data Visualization:** Recharts / Tremor for telemetry charts and storage distributions
* **Code & Diff Editor:** Monaco Editor or PrismJS for JSON/YAML editing and colorized diffing

### 4.2 Typography & Aesthetics (UI/UX Pro Max Standard)
* **Font Pairing:**
  * **Headings / Hero:** *Cormorant Garamond* (serif, luxury, editorial weight 600/700)
  * **UI Elements / Body:** *Montserrat* (clean geometric sans-serif, weights 400/500/600)
  * **Code / Hash / Diffs:** *JetBrains Mono* (monospace tabular figures)
* **Color Palette (Dark Cyber-Editorial Theme):**
  * `Background`: Deep Obsidian (`#090D16`)
  * `Surface / Card`: Slate Charcoal (`#111827`) with 1px glassmorphic borders (`rgba(255, 255, 255, 0.08)`)
  * `Primary Accent`: Electric Indigo (`#6366F1`)
  * `Security / Success`: Cyber Emerald (`#10B981`)
  * `Diff / Warning`: Amber (`#F59E0B`)
  * `Destructive / Danger`: Rose Crimson (`#EF4444`)

### 4.3 Key Dashboard Views
1. **Telemetry & Stats Hub (`/dashboard`):** Real-time aggregation cards, storage distribution bar charts, environment breakdown badges, and version count heatmaps.
2. **Config Explorer & Time Machine (`/configs`):** Tree browser for applications and environments, live payload viewer with "Reveal Secrets" toggle, SHA-512 verification shield, and one-click rollback.
3. **Interactive Diff Visualizer (`/diff`):** Side-by-side or unified GitHub-style diff comparator highlighting added, removed, and modified configuration keys.
4. **Binary Asset Manager (`/binaries`):** GridFS-backed file manager for uploading, downloading, and versioning TLS certificates, `.jks` stores, and license files.
5. **Export & Transformation Studio (`/export`):** Format switcher (JSON, YAML, .env) with live syntax highlighting and copy-to-clipboard / file download actions.
6. **Live Audit Stream (`/audit`):** SSE-backed stream rendering live access attempts, IPs, actions, and security alerts from the capped collection.
7. **Access & API Key Manager (`/access`):** Scoped key generator with role assignments, environment restrictions, and rate limit controls.

### 4.4 Motion & Accessibility Architectural Constraints
1. **Unified UI/UX Stack:** All interface elements must be designed using a combination of the global `ui-ux-pro-max` skill design system catalog (enforcing *Cormorant Garamond* and *Montserrat* typography pairings), responsive layout templates generated via the `21st.dev` MCP server tool, and fluid micro-interactions handled by Framer Motion.
2. **Motion Accessibility Constraint:** Every Framer Motion component (transitions, grid reveals, panel slides, badges, counters) must strictly query and honor the user's system `prefers-reduced-motion` media query (via `useReducedMotion()`), automatically falling back to static state modifications with zero layout shifting or unprompted motion if active.
3. **Strict No-Lombok Constraint:** Project Lombok remains completely excluded from all source code across backend and client libraries to preserve complete transparency.

---

## 5. Data Models & MongoDB Schema Design

```
+---------------------------------------------------------------------------------------------------+
|                                        MongoDB Collections                                        |
+--------------------------+-----------------------+-----------------------+------------------------+
| configurations           | binary_configurations | config_cache          | audit_logs             |
| (Normal Collection)      | (GridFS Metadata)     | (TTL 300s Collection) | (Capped Collection)    |
| - id: ObjectId           | - id: ObjectId        | - id: "app:env"       | - id: ObjectId         |
| - applicationName: String| - applicationName: Str| - encryptedPayload:Doc| - action: String       |
| - environment: String    | - environment: String | - cachedAt: Date      | - applicationName: Str |
| - version: Integer       | - version: Integer    |                       | - environment: String  |
| - documentHash: String   | - filename: String    |                       | - requestingIp: String |
| - encryptedDek: String   | - contentType: String |                       | - apiKey: String       |
| - payload: Document      | - gridFsFileId: ObjId |                       | - success: Boolean     |
| - tags: Map<String,Str>  | - fileHash: String    |                       | - failureReason: Str   |
| - createdAt: Date        | - createdAt: Date     |                       | - timestamp: Date      |
| - expiresAt: Date (TTL)  |                       |                       |                        |
+--------------------------+-----------------------+-----------------------+------------------------+
| api_keys (Scoped RBAC Collection)                                                                 |
| - id: ObjectId, keyPrefix: String, hashedKey: String, allowedApps: [String],                     |
|   allowedEnvironments: [String], permissions: [String], active: Boolean, createdAt: Date          |
+---------------------------------------------------------------------------------------------------+
```

---

## 6. Full-Stack Directory Structure

```
zero-config-vault/
├── pom.xml                                   # Root Maven reactor build
├── SPEC.md                                   # Active system specification
├── DECISIONS.md                              # Architectural decisions log
│
├── backend/                                  # Spring Boot 3.x Backend Application
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/vault/configmanager/
│       │   │   ├── VaultApplication.java
│       │   │   ├── config/                   # MongoConfig, SSEConfig, OpenApiConfig
│       │   │   ├── controller/               # REST & SSE controllers
│       │   │   │   ├── ConfigurationController.java
│       │   │   │   ├── BinaryConfigController.java
│       │   │   │   ├── ExportController.java
│       │   │   │   ├── StatsController.java
│       │   │   │   ├── StreamController.java
│       │   │   │   └── ApiKeyController.java
│       │   │   ├── dto/                      # Request & Response DTOs
│       │   │   ├── exception/                # Exception handlers & custom errors
│       │   │   ├── model/                    # VaultDocument, BinaryConfig, AuditLog, ApiKey
│       │   │   ├── repository/               # Mongo repositories & GridFSTemplate
│       │   │   ├── security/                 # ApiKeyAuthFilter, ScopedAccessManager, KeyProvider
│       │   │   ├── service/                  # Business logic services
│       │   │   │   ├── ConfigurationService.java
│       │   │   │   ├── BinaryConfigService.java
│       │   │   │   ├── CryptoService.java
│       │   │   │   ├── EnvelopeEncryptionService.java
│       │   │   │   ├── HashingService.java
│       │   │   │   ├── CacheService.java
│       │   │   │   ├── AuditService.java
│       │   │   │   ├── ExportService.java
│       │   │   │   ├── ChangeStreamService.java
│       │   │   │   └── StatsService.java
│       │   │   └── util/                     # JsonTraverser, ConfigDiffUtil, MaskingUtil
│       │   └── resources/
│       │       └── application.yml
│       └── test/                             # Offline slice & unit tests (76+ tests)
│
├── starter/                                  # Client Auto-Configuration Library
│   ├── pom.xml
│   └── src/main/java/com/vault/starter/
│       ├── VaultAutoConfiguration.java
│       ├── VaultPropertySourceLocator.java
│       ├── VaultClient.java
│       └── VaultSseRefreshListener.java
│
└── frontend/                                 # Next.js 15 Full-Stack Dashboard
    ├── package.json
    ├── next.config.ts
    ├── tailwind.config.ts
    ├── tsconfig.json
    └── src/
        ├── app/
        │   ├── layout.tsx                    # Cormorant Garamond & Montserrat font setup
        │   ├── page.tsx                      # Landing / redirect to /dashboard
        │   ├── dashboard/page.tsx            # Aggregation telemetry charts
        │   ├── configs/page.tsx              # Interactive tree & time machine
        │   ├── diff/page.tsx                 # Structural diff comparator
        │   ├── binaries/page.tsx             # GridFS certificate & asset manager
        │   ├── export/page.tsx               # JSON/YAML/.env exporter
        │   ├── audit/page.tsx                # Live SSE audit stream table
        │   └── access/page.tsx               # Scoped API key administration
        ├── components/
        │   ├── ui/                           # shadcn/ui primitives (button, dialog, card, tabs)
        │   ├── 21st/                         # Components generated via 21st.dev MCP
        │   ├── diff-viewer.tsx               # Colorized GitHub-style diff view
        │   ├── secret-toggle.tsx             # Mask / Reveal encryption toggle
        │   ├── integrity-badge.tsx           # SHA-512 cryptographic verification shield
        │   └── telemetry-cards.tsx           # Live aggregation metric widgets
        ├── lib/
        │   ├── api.ts                        # Vault backend HTTP & SSE client
        │   └── utils.ts
        └── styles/
            └── globals.css                   # Obsidian dark cyber-editorial theme
