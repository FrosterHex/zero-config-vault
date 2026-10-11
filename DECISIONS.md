# Secure Config Vault - Architectural Decisions

This document records the architectural constraints, design patterns, and technical decisions made for the Secure Config Vault project.

---

## 1. Exclusive Use of MongoDB
* **Decision:** MongoDB is the sole data store for the entire application ecosystem—handling relational-style configs, caching (TTL), auditing (Capped), change notifications (Change Streams), and binary blobs (GridFS). No external cache (Redis), message broker (Kafka/RabbitMQ), or object store (AWS S3/MinIO) is permitted.
* **Reasoning:** Eliminates external infrastructure dependencies and demonstrates the full versatility of MongoDB as a multi-model data platform.

---

## 2. Temporal (Immutable) Versioning
* **Decision:** Configuration documents in MongoDB are strictly append-only and never updated in place.
* **Reasoning:** Enforces a GitOps-style audit trail. Every modification, partial patch, or rollback queries the latest version, increments it, and writes a net-new document. Historical integrity is guaranteed, and accidental data loss is mathematically prevented.

---

## 3. MongoDB TTL with Encrypted In-Memory Caching
* **Decision:** A `config_cache` collection with a 300-second (5 min) TTL index serves as a high-speed read cache. However, cached payloads are encrypted using an in-memory, auto-rotating ephemeral symmetric key before writing to the collection.
* **Reasoning:** Replaces the need for a Redis cluster while resolving the risk of plaintext secrets lingering on disk in MongoDB. Even if an attacker inspects MongoDB storage files on disk, cached secrets remain encrypted.

---

## 4. Capped Collections for High-Throughput Auditing
* **Decision:** Audit logs are written exclusively to a MongoDB capped collection (`audit_logs`) fixed at 50MB and 10,000 documents.
* **Reasoning:** Restricts storage bloat automatically via MongoDB's native circular ring-buffer mechanism. Capped collections offer high-performance append operations and auto-evict the oldest records without costly garbage collection or manual purge scripts.

---

## 5. Canonical JSON Hashing vs. BSON
* **Decision:** Cryptographic SHA-512 fingerprints are computed against a deterministically sorted, canonical JSON string representation rather than raw BSON.
* **Reasoning:** BSON serialization order and field typing can vary across MongoDB driver versions and language runtimes. Generating the SHA-512 digest from canonical JSON guarantees deterministic, repeatable integrity validation across heterogeneous environments.

---

## 6. Cryptographic Envelope Encryption & Automated Key Rotation
* **Decision:** Implement two-tiered Envelope Encryption. Each configuration document is encrypted using a unique, ephemeral 256-bit AES Data Encryption Key (DEK). The DEK is itself encrypted by a Master Key Encryption Key (KEK) provided by `KeyProvider` and stored in the document as `encryptedDek`.
* **Reasoning:** Solves the key rotation bottleneck. Rotating the master key (KEK) only requires re-encrypting the 32-byte DEKs across documents, without needing to decrypt and re-encrypt massive configuration payloads across the database.

---

## 7. Strict "No Lombok" Policy
* **Decision:** Project Lombok is strictly banned from all source code across backend and client libraries.
* **Reasoning:** Ensures absolute code transparency for academic viva examinations and security audits. All constructors, accessors, mutators, and serialization annotations are explicitly declared and auditable.

---

## 8. Reactive Change Streams & Server-Sent Events (SSE) vs. WebSockets / Polling
* **Decision:** Implement live configuration propagation using MongoDB Change Streams (`collection.watch()`) connected to Server-Sent Events (SSE) over HTTP (`GET /api/v1/vault/stream/{app}/{env}`).
* **Reasoning:** 
  1. Configuration distribution is naturally unidirectional (vault $\rightarrow$ microservices).
  2. SSE operates over standard HTTP/1.1 and HTTP/2, requires no bidirectional WebSocket framing or custom keep-alives, and seamlessly traverses enterprise firewalls and load balancers.
  3. Change Streams tap directly into MongoDB's internal replica set oplog with near-zero latency and minimal CPU overhead compared to constant polling.

---

## 9. Multi-Document ACID Transactions vs. Eventual Consistency
* **Decision:** Provide an optional `?atomic=true` parameter on the bulk import endpoint backed by Spring's `MongoTransactionManager`. If `atomic=false` (default), execute in best-effort mode.
* **Reasoning:** Demonstrates MongoDB's multi-document ACID transactional guarantees on replica sets while preserving graceful backward compatibility for standalone single-node developer environments.

---

## 10. Binary Configuration Storage via MongoDB GridFS
* **Decision:** Store binary configuration assets (TLS certificates, `.pem` bundles, `.jks` stores, license files) in MongoDB GridFS (`vault_binaries` bucket) rather than external S3 or local filesystem storage.
* **Reasoning:** 
  1. BSON documents are hard-capped at 16MB. Large certificates and keystores can exceed single-document limits.
  2. GridFS splits files into 255KB chunks (`fs.chunks`) and coordinates metadata (`fs.files`), maintaining our zero-external-dependency requirement while preserving full versioning and SHA-512 hashing.

---

## 11. Scoped API Keys & RBAC via Prefix Hashing
* **Decision:** Authenticate clients via scoped API keys stored in an `api_keys` collection. Keys use a public prefix (e.g. `sk_prod_pay_...`) and are verified by comparing the SHA-256 hash of the secret portion against the stored hash.
* **Reasoning:**
  1. Prevents credential leakage even in the event of a raw database dump.
  2. Enables granular Role-Based Access Control (RBAC): restrict keys to specific applications (`allowedApps`), environments (`allowedEnvironments`), and actions (`READ`, `WRITE`, `ROLLBACK`, `EXPORT`, `ADMIN`).

---

## 12. Full-Stack Architecture & Design System (Next.js 15 + ui-ux-pro-max + 21st.dev)
* **Decision:** Implement the administrative dashboard using Next.js 15 (App Router), Tailwind CSS, shadcn/ui, and a strict typography pairing of *Cormorant Garamond* (luxury/security display headings) and *Montserrat* (high-density telemetry and body copy), with *JetBrains Mono* for code.
* **Reasoning:** Elevates the vault into a high-density, accessible, luxury/cybersecurity-grade visual experience compliant with the UI/UX Pro Max guidelines. Component blocks and patterns are accelerated using `21st.dev` MCP tooling for modern, production-grade aesthetics.

---

## 13. Framer Motion Integration with Strict Motion Accessibility Constraints
* **Decision:** Formally integrate Framer Motion into the core frontend stack (alongside Next.js 15, Tailwind CSS, and shadcn/ui) to govern all micro-interactions, page transitions, and telemetry reveals, enforcing two mandatory architectural constraints:
  1. **Unified UI/UX Stack:** All interface elements must be designed using a combination of the global `ui-ux-pro-max` skill design system catalog (enforcing *Cormorant Garamond* and *Montserrat* typography pairings), responsive layout templates generated via the `21st.dev` MCP server tool, and fluid micro-interactions handled by Framer Motion.
  2. **Motion Accessibility Constraint:** Every Framer Motion component (transitions, grid reveals, panel slides, badges, counters) must strictly query and honor the user's system `prefers-reduced-motion` media query (via `useReducedMotion()`), automatically falling back to static state modifications with zero layout shifting or unprompted motion if active.
* **Reasoning:**
  1. Micro-interactions and subtle telemetry animations enhance the perceived responsiveness and aesthetic quality of the dashboard, fulfilling the high-density luxury/cybersecurity expectations of the UI/UX Pro Max system.
  2. Accessibility compliance (WCAG 2.1 Success Criterion 2.3.3 Animation from Interactions) is mandatory: users with vestibular disorders or motion sensitivities must not experience disorienting animations.
  3. Automatic graceful fallback provides full functional parity without performance overhead or visual degradation.

