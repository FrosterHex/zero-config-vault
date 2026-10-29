// MongoDB initialization for Zero-Config Vault (docker-compose only).
//
// Only creates the two collections that need special options at creation time.
// Everything else (indexes, $jsonSchema validator, demo data, API keys) is owned by the
// Spring Boot backend on startup (MongoConfig, DataBootstrapSeeder, ApiKeyService), so demo
// secrets are always encrypted with the vault's master key and never seeded in plaintext.
// Safe to run on every `docker compose up`.

const dbName = 'secure_config_vault';
const vaultDb = db.getSiblingDB(dbName);
const existing = vaultDb.getCollectionNames();

print('>>> [Zero-Config Vault] Ensuring collections on database: ' + dbName);

if (!existing.includes('audit_logs')) {
  vaultDb.createCollection('audit_logs', { capped: true, size: 52428800, max: 10000 });
  print('  - Created capped collection: audit_logs (50MB, max 10,000 docs)');
}

if (!existing.includes('config_access_telemetry')) {
  vaultDb.createCollection('config_access_telemetry', {
    timeseries: { timeField: 'timestamp', metaField: 'metadata', granularity: 'seconds' }
  });
  print('  - Created native time-series collection: config_access_telemetry');
}

print('>>> [Zero-Config Vault] Initialization complete.');
