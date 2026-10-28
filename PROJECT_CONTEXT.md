# 🛡️ Zero-Config Vault — Project Context & Handoff Briefing

> **Purpose of this Document**:  
> This file is a comprehensive state, architecture, and handoff guide for any developer or AI session picking up work on **Zero-Config Vault**. It outlines everything that has been built, what was in progress at the time of session switch, what is left to do, and the exact commands, schemas, and credentials needed to continue without interruption.

---

## 📌 Executive Summary & Quick Links

- **Repository**: [`https://github.com/FrosterHex/zero-config-vault.git`](https://github.com/FrosterHex/zero-config-vault.git) (`main` branch)
- **Live Frontend Dashboard (Vercel)**: [`https://zeroconfig-one.vercel.app`](https://zeroconfig-one.vercel.app)
- **Live Backend API (Render)**: [`https://zeroconfig-0s8m.onrender.com`](https://zeroconfig-0s8m.onrender.com)
  - **Interactive Swagger / OpenAPI Docs**: [`https://zeroconfig-0s8m.onrender.com/swagger-ui.html`](https://zeroconfig-0s8m.onrender.com/swagger-ui.html)
  - **OpenAPI JSON**: [`https://zeroconfig-0s8m.onrender.com/v3/api-docs`](https://zeroconfig-0s8m.onrender.com/v3/api-docs)
- **Database**: **MongoDB 7.0 Replica Set** (Remote: MongoDB Atlas M0 Free Tier / Local: Docker 3-node replica set `rs0`)
- **Academic Context**: Sem 7 NoSQL Databases Course Project. Specifically designed to showcase **extreme MongoDB mastery** without relying on secondary databases (no Redis, no ZooKeeper, no Consul).

---

## 🏗️ Tech Stack & Directory Structure

```
Zero-Config Vault/
├── .agents/                                # Agent custom skills & configurations
├── docker/
│   └── init-mongo.js                       # Replica set init, capped collection, TTL, and index bootstrapping
├── frontend/                               # Next.js 15.5.27 App Router + Turbopack
│   ├── src/
│   │   ├── app/
│   │   │   ├── layout.tsx                  # Root layout with dark theme & header
│   │   │   ├── page.tsx                    # Main dashboard (config explorer, diff viewer, audit logs, binaries)
│   │   │   └── nosql/
│   │   │       └── page.tsx                # Dedicated NoSQL Deep-Dive & Telemetry visualization page
│   │   ├── components/
│   │   │   ├── dashboard/                  # UI widgets (Sidebar, ConfigList, DiffViewer, CommandPalette, etc.)
│   │   │   └── ui/                         # Three.js 3D background canvas, buttons, modal dialogs
│   │   └── lib/
│   │       ├── api.ts                      # Frontend API client library & typed fetchers
│   │       └── types.ts                    # TypeScript interfaces matching backend DTOs
│   └── package.json                        # Next.js 15, React 19, Tailwind CSS v4, Lucide, Three.js
├── src/main/java/com/vault/configmanager/  # Spring Boot 3.2.5 (Java 17 LTS)
│   ├── config/
│   │   ├── MongoConfig.java                # Capped collections, Time-Series collections, $jsonSchema, full-text indexes
│   │   ├── DataBootstrapSeeder.java        # Startup seeder for multi-tenant demo configs, telemetry, & binaries
│   │   ├── OpenApiConfig.java              # Swagger / SpringDoc OpenAPI configuration
│   │   └── SecurityConfig.java             # Scoped API key filter registration & CORS configuration
│   ├── controller/                         # REST & SSE Web Layer
│   │   ├── ConfigurationController.java    # CRUD, versioning, rollback, full-text search (/search)
│   │   ├── ExportController.java           # JSON/YAML canonical exports
│   │   ├── DiffController.java             # JSON structural diff calculation
│   │   ├── BinaryConfigController.java     # GridFS binary certificate/keystore management
│   │   ├── StreamController.java           # Real-time SSE configuration updates via MongoDB Change Streams
│   │   └── StatsController.java            # System stats, time-series telemetry, & $jsonSchema validation info
│   ├── dto/                                # Request & Response data transfer objects
│   ├── model/                              # MongoDB Document Entities (NO LOMBOK)
│   │   ├── VaultDocument.java              # Configuration document collection (`configurations`)
│   │   ├── CachedConfig.java               # Ephemeral cache collection with native TTL (`cached_configs`)
│   │   ├── AuditLog.java                   # Tamper-proof capped collection (`audit_logs`)
│   │   ├── AccessTelemetry.java            # Native time-series telemetry collection (`config_access_telemetry`)
│   │   ├── ApiKey.java                     # Scoped RBAC credential collection (`api_keys`)
│   │   └── BinaryConfigMetadata.java       # GridFS metadata document (`binary_configs`)
│   ├── repository/                         # Spring Data MongoDB Repositories
│   ├── security/                           # ApiKeyAuthFilter & Scoped API key evaluation
│   ├── service/                            # Business logic, Crypto (AES-256-GCM), Canonical Hashing (SHA-512)
│   └── util/                               # ConfigDiffUtil, SecretMaskingUtil, JsonTraverser
├── starter/                                # Spring Boot Client Starter (vault-spring-boot-starter)
├── Dockerfile                              # Multi-stage production container build (Maven -> Eclipse Temurin 17 JRE)
├── docker-compose.yml                      # 3-node Mongo replica set + backend + frontend service orchestration
├── pom.xml                                 # Maven build configuration (Java 17, Spring Boot 3.2.5)
├── README.md                               # Comprehensive documentation & architecture diagram
├── SHARDING.md                             # Production horizontal sharding & distributed topology guide
├── SPEC.md & PRD.md                        # Functional & non-functional requirements specifications
├── verify-vault.ps1 & verify-vault.sh      # Automated 8-phase end-to-end integration test harnesses (untracked from git)
└── PROJECT_CONTEXT.md                      # THIS HANDOFF DOCUMENT
```

---

## 📜 Key Architectural Directives & Rules

1. **Zero Lombok Policy (STRICT)**:
   - Lombok is 100% excluded from `pom.xml`.
   - Every single Java model, DTO, entity, and service has explicit, handwritten constructors, getters, setters, and `toString` methods. Do NOT add Lombok annotations.
2. **MongoDB as the Sole Engine**:
   - No external cache dependencies (no Redis, Memcached). MongoDB TTL collections act as the cache.
   - No external messaging queues (no Kafka/RabbitMQ for config updates). MongoDB Change Streams (`collection.watch()`) back the Server-Sent Events (SSE) streaming engine.
   - No external blob store (no AWS S3). MongoDB GridFS (`vault_binaries` bucket) stores TLS certificates and `.jks` keystores.
   - No external time-series DB (no InfluxDB/Prometheus). MongoDB Native Time-Series Collections store access metrics.
3. **Hardware-Grade Cryptography & Immutability**:
   - Field-level Envelope Encryption: Nested properties inside `"secure_settings"` are automatically encrypted with AES-256-GCM using deterministic initialization vectors and stored as ciphertexts.
   - Canonical SHA-512 Fingerprint: Key ordering is normalized deterministically before hashing. Any change produces a new immutable revision document ($v_1 \to v_2 \to v_3$). Existing versions can never be mutated or overwritten in-place.
4. **Secret Sanitization**:
   - Audit logs (`audit_logs`) and search results automatically mask passwords, tokens, and keys with `[REDACTED]`.

---

## ⏱️ What Happened (Chronological Milestone Log)

### Milestone 1: Core Engine Build & Test Suite
- Built full Spring Boot 3.2.5 backend with 116 passing unit and integration tests (`BUILD SUCCESS`).
- Created Next.js 15.5.27 dashboard with dark mode, Three.js 3D vault background, live config editor, JSON/YAML toggle, diff viewer, audit logs, and binary file manager.
- Authored `vault-spring-boot-starter` for microservice property source injection with dynamic SSE reload.

### Milestone 2: Advanced NoSQL & MongoDB Capabilities
To maximize marks for the Sem 7 NoSQL course, implemented 6 enterprise MongoDB capabilities:
1. **MongoDB Native Time-Series Collection (`config_access_telemetry`)**:
   - Configured with `timeField: "timestamp"`, `metaField: "metadata"`, and `granularity: SECONDS`.
   - Records latency, status codes, operation types, and cache hit/miss flags.
   - Built aggregation pipelines for percentiles, throughput over time, and hourly breakdown.
2. **Engine-Level `$jsonSchema` Validation**:
   - Registered database-level schema validator on the `configurations` collection via `collMod` / `createCollection`.
   - Enforces field types, regex patterns on `applicationName`, valid environment enums (`development`, `staging`, `production`, `dr`), and SHA-512 hash lengths at the MongoDB engine layer.
3. **MongoDB Full-Text Search Engine**:
   - Created compound text index `idx_configurations_text_search` across `applicationName`, `searchableText`, and `environment`.
   - Implemented `/api/v1/configurations/search?q={query}` endpoint with text scoring.
4. **Horizontal Sharding & Distributed Scalability Blueprint (`SHARDING.md`)**:
   - Documented compound shard key strategy (`{ applicationName: 1, environment: 1, version: 1 }`), chunk range splitting, zone-tier routing (SSD vs HDD), and zero-downtime migration steps.
5. **Frontend NoSQL Deep-Dive Dashboard (`/nosql`)**:
   - Added interactive page displaying time-series telemetry graphs, `$jsonSchema` engine rule viewer, live aggregation pipeline runner, GridFS chunk explorer, and capped collection storage stats.
6. **Global Command Palette (`Cmd+K` / `Ctrl+K`)**:
   - Added instant global keyboard shortcut for full-text search across all configs.

### Milestone 3: Security & Git Cleanup
- The verification scripts `verify-vault.ps1` and `verify-vault.sh` contained sample API keys and test tokens.
- Safely removed them from git tracking using `git rm --cached` without deleting them from the local filesystem, and added them to `.gitignore`.
- Kept `Antigravity_Vault_Build_Spec.md` and agent scratch files out of git.

### Milestone 4: Deployment Bugfixes
1. **Vercel Next.js Build Fix**:
   - Next.js build failed during production deployment due to missing `setLoading` identifier and unmemoized `fetchData` in `frontend/src/app/nosql/page.tsx`.
   - Resolved by adding `loading` state, wrapping `fetchData` in `useCallback`, and verifying type safety.
2. **Render Spring Boot MongoTemplate Startup Fix**:
   - Render deployment threw `UnsatisfiedDependencyException: Cannot resolve reference to bean 'mongoTemplate'` during `ApiKeyAuthFilter` initialization.
   - **Root Cause**: `VaultDocument` had an `@TextIndexed` annotation while `MongoConfig` was simultaneously creating a programmatic text index `idx_configurations_text_search`. MongoDB strictly limits collections to at most ONE text index. The duplicate creation threw an `IndexOptionsConflict` exception during Spring startup.
   - **Fix**: Removed `@TextIndexed` annotation from `VaultDocument` and added safe index inspection (`listIndexes()`) in `MongoConfig.java` to prevent duplicate index attempts.

### Milestone 5: Automatic Multi-Tenant Demo Data Seeder (`DataBootstrapSeeder.java`)
- Created `DataBootstrapSeeder` implementing Spring Boot `ApplicationRunner`.
- When the application starts and discovers an empty MongoDB database (e.g. fresh MongoDB Atlas cluster), it automatically seeds:
  - **4 Microservices**: `payment-gateway`, `auth-service`, `order-service`, `notification-pipeline`.
  - **3 Environments**: `production`, `staging`, `development`.
  - **Multiple Revisions**: $v_1$ and $v_2$ with encrypted `"secure_settings"` (Stripe keys, database passwords, JWT secrets, Kafka credentials, Twilio tokens).
  - **Time-Series Telemetry**: 60 realistic access events with varying latencies, status codes, and cache hit ratios.
  - **GridFS Binaries**: Uploads a sample TLS Certificate (`tls-cert.pem`) and signing keystore (`signing-keystore.jks`).
- Pushed commit `cee628a` directly to `origin/main`.

---

## 🔄 What Was Happening (Current State at Handoff)

1. **Git State**:
   - Active branch: `main`
   - Working tree: Clean (`nothing to commit, working tree clean`)
   - Synchronized with `origin/main` (latest commit `cee628a8a2765c4e3a79ccdd4aa507a02eac98f5`).
2. **Backend Compilation & Tests**:
   - `mvn test`: **116 Tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS` in 17.9s).
3. **Frontend Compilation**:
   - `frontend/src/app/nosql/page.tsx` was fixed and is compiling with Next.js 15 Turbopack.
4. **Cloud Deployments**:
   - Render and Vercel both monitor `origin/main` for automatic deploys.
   - Pushed commit `cee628a` triggers automatic build and deployment on Render (`https://zeroconfig-0s8m.onrender.com`) and Vercel (`https://zeroconfig-one.vercel.app`).

---

## 📋 What Is Left (Actionable Next Steps)

1. **Verify Live Render Service**:
   - Test the live Render URL once the automatic build finishes:
     ```bash
     curl -s https://zeroconfig-0s8m.onrender.com/v3/api-docs
     ```
   - Verify Swagger UI loads: `https://zeroconfig-0s8m.onrender.com/swagger-ui.html`.
   - Verify that `DataBootstrapSeeder` ran on MongoDB Atlas by querying configurations:
     ```bash
     curl -H "X-API-Key: sk-admin-key-001" https://zeroconfig-0s8m.onrender.com/api/v1/configurations
     ```
2. **Verify Live Vercel Dashboard**:
   - Visit `https://zeroconfig-one.vercel.app`.
   - Test navigation to the new NoSQL Deep-Dive page: `https://zeroconfig-one.vercel.app/nosql`.
   - Test the Command Palette (`Cmd+K` or `Ctrl+K`) to search for `"payment"`.
3. **Run End-to-End Test Harness Locally (Optional)**:
   - Run the local PowerShell or Bash verification script against either local or remote target:
     ```powershell
     # Against remote Render
     .\verify-vault.ps1 -BaseUrl "https://zeroconfig-0s8m.onrender.com"
     
     # Or against local Docker / Spring Boot
     .\verify-vault.ps1 -BaseUrl "http://localhost:8080"
     ```
4. **Optional Project Enhancements for Extra Course Marks**:
   - Add a simulated microservice demo in `starter/` demonstrating real-time configuration hot-reload without restarting the JVM when an SSE event fires.
   - Add a multi-region active-active read-replica failover simulation script.

---

## 🗄️ MongoDB Database Specifications

### 1. Collections & Index Topology

| Collection Name | Type | Purpose | Primary Indexes |
| :--- | :--- | :--- | :--- |
| `configurations` | Standard Document | Canonical versioned configurations | `{ applicationName: 1, environment: 1, version: -1 }` (Unique compound)<br>`idx_configurations_text_search` (Text index on `applicationName`, `searchableText`, `environment`) |
| `cached_configs` | TTL Collection | High-speed 5-minute ephemeral read cache | `{ expireAt: 1 }` (ExpireAfterSeconds: 0)<br>`{ applicationName: 1, environment: 1 }` |
| `audit_logs` | Capped Collection | Immutable circular audit log (50MB, max 10,000 docs) | Natural insertion order (`$natural`) |
| `config_access_telemetry` | Time-Series | Access events & latency tracking | TimeField: `timestamp`, MetaField: `metadata`, Granularity: `seconds` |
| `api_keys` | Standard Document | Scoped RBAC credentials | `{ keyHash: 1 }` (Unique) |
| `fs.files` / `fs.chunks` | GridFS Bucket (`vault_binaries`) | Binary config payloads (mTLS certs, .jks files) | GridFS default compound indexes |

### 2. Default API Keys & Scopes

The application seeds or validates the following API keys via `X-API-Key` HTTP header:

| API Key | Scope | Permissions |
| :--- | :--- | :--- |
| `sk-admin-key-001` | `admin` | Full read, write, rollback, export, binary upload, telemetry access |
| `sk-dev-key-001` | `write` | Read, write, export in development & staging |
| `sk-dev-key-002` | `write` | Read, write in development |
| `sk-read-key-001` | `read` | Read-only access to configurations & exports |

### 3. Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/secure_config_vault` | MongoDB connection URI (Atlas or replica set) |
| `VAULT_MASTER_KEY` | `0123456789abcdef0123456789abcdef` | 256-bit AES master key for envelope decryption |
| `SERVER_PORT` | `8080` | Backend HTTP listening port |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` / `https://zeroconfig-0s8m.onrender.com` | Base URL used by Next.js frontend |
| `NEXT_PUBLIC_API_KEY` | `sk-admin-key-001` | Default API key used by frontend client |

---

## 🛠️ Common Operations & Developer Commands

### Running Locally with Docker Compose
```bash
# Starts 3-node MongoDB Replica Set (rs0), Spring Boot Backend, and Next.js Frontend
docker-compose up -d

# Check replica set status
docker exec -it mongo1 mongosh --eval "rs.status()"

# Stop all services
docker-compose down
```

### Running Backend Locally
```bash
# Run tests
mvn test

# Run Spring Boot application locally
mvn spring-boot:run
```

### Running Frontend Locally
```bash
cd frontend
npm install
npm run dev
# Dashboard opens on http://localhost:3000
```

### Testing MongoDB Commands Directly
```javascript
// Connect via mongosh
use secure_config_vault;

// Check configurations
db.configurations.find({ applicationName: "payment-gateway" }).pretty();

// Check time-series telemetry aggregation
db.config_access_telemetry.aggregate([
  { $group: { _id: "$metadata.applicationName", avgLatency: { $avg: "$latencyMs" }, totalOps: { $sum: 1 } } }
]);

// Check engine-level $jsonSchema validation
db.getCollectionInfos({ name: "configurations" })[0].options.validator;
```

---

*Handoff context generated for Zero-Config Vault. All tests passing and repository synced with origin/main.*
