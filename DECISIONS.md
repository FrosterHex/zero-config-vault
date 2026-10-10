# Secure Config Vault - Architectural Decisions

This document records the architectural constraints and technical decisions made for the Secure Config Vault project.

## 1. Exclusive Use of MongoDB
* **Decision:** MongoDB is the sole data store for the application. No external caching layers (like Redis) or secondary databases are permitted.
* **Reasoning:** Simplifies the infrastructure and demonstrates the versatility of MongoDB by utilizing native features like TTL Indexes (for caching and expiry) and Capped Collections (for audit logging).

## 2. Temporal (Immutable) Versioning
* **Decision:** Existing configuration documents in MongoDB are never updated or mutated.
* **Reasoning:** Enforces a GitOps-style audit trail. Every modification or rollback inserts a net-new document with an incremented version number, ensuring historical integrity and preventing accidental data loss.

## 3. MongoDB TTL for Caching
* **Decision:** A `config_cache` collection with a TTL index of 300 seconds (5 minutes) is used as a read-through cache for configurations.
* **Reasoning:** Replaces the need for Redis. *Tradeoff/Constraint:* Decrypted configuration payloads are temporarily stored in this collection, meaning plaintext secrets exist in MongoDB for up to 5 minutes. In a real-world production system, this cache would require secondary encryption with fast key rotation.

## 4. Capped Collections for Audit Logging
* **Decision:** Audit logs are written to a MongoDB capped collection.
* **Reasoning:** Automatically restricts the size of the audit logs to prevent database bloat over time. *Constraint:* Capped collections cannot be deleted from or have secondary indexes added post-creation. 

## 5. Canonical JSON Hashing vs. BSON
* **Decision:** The integrity fingerprint (SHA-512 hash) is calculated on a sorted, canonical JSON string representation of the payload, *not* the raw BSON.
* **Reasoning:** BSON-to-string conversions are inconsistent across different libraries and versions. Hashing the canonical JSON ensures deterministic and reliable integrity checks during retrieval.

## 6. Field-Level Encryption Logic
* **Decision:** Encryption strictly targets JSON nodes named `"secure_settings"` using AES-256-GCM with a unique 12-byte IV for every node.
* **Reasoning:** Provides localized security without encrypting the entire document, enabling the rest of the configuration to be queried or indexed while keeping sensitive credentials safe.

## 7. No Lombok
* **Decision:** Lombok is intentionally excluded from the project.
* **Reasoning:** Ensures full code transparency. All boilerplate code (getters, setters, constructors) must be explicitly written for security auditing and code review purposes.
