// MongoDB Initialization & Seed Script for Zero-Config Vault
// Executed upon container bootstrap against the rs0 replica set

const dbName = 'secure_config_vault';
const vaultDb = db.getSiblingDB(dbName);

print('>>> [Zero-Config Vault] Initializing collections and seed data on database: ' + dbName);

// 1. Establish Collections & Indexes
try {
  // Ensure Capped Collection for AuditLog
  const collections = vaultDb.getCollectionNames();
  if (!collections.includes('audit_logs')) {
    vaultDb.createCollection('audit_logs', { capped: true, size: 52428800, max: 10000 });
    print('  - Created capped collection: audit_logs (50MB, max 10,000 docs)');
  }
} catch (e) {
  print('  ! audit_logs collection notice: ' + e.message);
}

// Vault Documents compound unique index
vaultDb.vault_documents.createIndex(
  { applicationName: 1, environment: 1, version: 1 },
  { unique: true, name: 'idx_app_env_ver_unique' }
);
vaultDb.vault_documents.createIndex(
  { applicationName: 1, environment: 1 },
  { name: 'idx_app_env' }
);
print('  - Created compound unique index on vault_documents');

// Cached Config TTL index (expire documents after expiresAt timestamp)
vaultDb.cached_configs.createIndex(
  { expiresAt: 1 },
  { expireAfterSeconds: 0, name: 'idx_cached_ttl' }
);
print('  - Created TTL expiration index on cached_configs');

// 2. Note: Scoped RBAC API keys are automatically seeded and hashed by Spring Boot's ApiKeyService on boot.

// 3. Seed Starter Vault Configurations
const sampleConfigs = [
  {
    applicationName: 'payment-gateway',
    environment: 'production',
    version: 1,
    documentHash: 'd7a8fbb307d380bf9574ff7b713ffb783f6f1c4e16d418294a2b25ba8c6a267c4ec2a6b478cf36c05eb763c7eb1665a953e5b1cc82f50ee63c9b7fae29ecfd0c',
    payload: {
      server: {
        port: 8443,
        timeoutMs: 5000,
        ssl: { enabled: true, protocol: 'TLSv1.3' }
      },
      payment: {
        currency: 'USD',
        maxRetryAttempts: 3,
        rateLimitPerMinute: 1200,
        gateways: ['stripe', 'adyen', 'paypal']
      },
      database: {
        host: 'db-cluster.internal.payment.prod',
        port: 27017,
        name: 'prod_payments'
      },
      secure_settings: {
        stripe_api_secret: 'sec_live_9981248719283719238',
        webhook_signing_salt: 'whsec_7128937128941728934'
      }
    },
    tags: { tier: 'tier-1', compliance: 'PCI-DSS', owner: 'payments-team' },
    createdAt: new Date(Date.now() - 3600000 * 24).toISOString()
  },
  {
    applicationName: 'payment-gateway',
    environment: 'production',
    version: 2,
    documentHash: 'a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0',
    payload: {
      server: {
        port: 8443,
        timeoutMs: 3000,
        ssl: { enabled: true, protocol: 'TLSv1.3' }
      },
      payment: {
        currency: 'USD',
        maxRetryAttempts: 5,
        rateLimitPerMinute: 2500,
        gateways: ['stripe', 'adyen', 'paypal', 'apple_pay']
      },
      database: {
        host: 'db-cluster.internal.payment.prod',
        port: 27017,
        name: 'prod_payments'
      },
      secure_settings: {
        stripe_api_secret: 'sec_live_9981248719283719238',
        webhook_signing_salt: 'whsec_7128937128941728934'
      }
    },
    tags: { tier: 'tier-1', compliance: 'PCI-DSS', owner: 'payments-team', release: 'v2.4.0' },
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString()
  },
  {
    applicationName: 'order-service',
    environment: 'staging',
    version: 1,
    documentHash: 'b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9',
    payload: {
      server: {
        port: 8081,
        contextPath: '/orders/v1'
      },
      kafka: {
        brokers: 'kafka-broker-1:9092,kafka-broker-2:9092',
        orderTopic: 'staging.orders.created',
        groupId: 'order-processor-group'
      },
      database: {
        host: 'staging-mongo.internal',
        port: 27017,
        name: 'staging_orders'
      },
      secure_settings: {
        kafka_sasl_password: 'order_staging_secret_key_123'
      }
    },
    tags: { tier: 'tier-2', team: 'fulfillment' },
    createdAt: new Date(Date.now() - 3600000 * 12).toISOString()
  },
  {
    applicationName: 'auth-service',
    environment: 'production',
    version: 1,
    documentHash: 'fe2592b3917da1bf55bb244291137fcf8fbd14ba9f59f6962bb6de8ecea1f098',
    payload: {
      jwt: {
        issuer: 'vault.auth.corporate',
        expirationSeconds: 3600,
        algorithm: 'RS256'
      },
      cors: {
        allowedOrigins: ['https://app.corporate.io', 'https://admin.corporate.io']
      },
      secure_settings: {
        jwt_private_key_passphrase: 'passphrase-for-auth-rsa-key'
      }
    },
    tags: { tier: 'tier-0', security: 'high' },
    createdAt: new Date(Date.now() - 3600000 * 48).toISOString()
  }
];

for (const config of sampleConfigs) {
  vaultDb.vault_documents.updateOne(
    { applicationName: config.applicationName, environment: config.environment, version: config.version },
    { $set: config },
    { upsert: true }
  );
}
print('  - Seeded ' + sampleConfigs.length + ' vault documents across multiple environments');

print('>>> [Zero-Config Vault] Database seeding completed successfully.');
