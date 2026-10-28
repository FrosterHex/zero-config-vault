package com.vault.configmanager.config;

import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.model.AccessTelemetry;
import com.vault.configmanager.model.TelemetryMetadata;
import com.vault.configmanager.repository.BinaryConfigRepository;
import com.vault.configmanager.repository.ConfigurationRepository;
import com.vault.configmanager.repository.TelemetryRepository;
import com.vault.configmanager.service.BinaryConfigService;
import com.vault.configmanager.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Automatically bootstraps realistic demo microservice configurations,
 * multi-version revisions, encrypted secrets, GridFS binary certs, and
 * time-series access telemetry on application startup if MongoDB is empty.
 *
 * Strict No Lombok policy enforced.
 */
@Component
public class DataBootstrapSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataBootstrapSeeder.class);

    private final ConfigurationRepository configurationRepository;
    private final ConfigurationService configurationService;
    private final TelemetryRepository telemetryRepository;
    private final BinaryConfigRepository binaryConfigRepository;
    private final BinaryConfigService binaryConfigService;

    public DataBootstrapSeeder(ConfigurationRepository configurationRepository,
                               ConfigurationService configurationService,
                               TelemetryRepository telemetryRepository,
                               BinaryConfigRepository binaryConfigRepository,
                               BinaryConfigService binaryConfigService) {
        this.configurationRepository = configurationRepository;
        this.configurationService = configurationService;
        this.telemetryRepository = telemetryRepository;
        this.binaryConfigRepository = binaryConfigRepository;
        this.binaryConfigService = binaryConfigService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedConfigurationsIfEmpty();
            seedTelemetryIfEmpty();
            seedBinaryConfigsIfEmpty();
            ensurePlayground();
        } catch (Exception e) {
            log.warn("DataBootstrapSeeder encountered a non-fatal notice during startup initialization: {}", e.getMessage());
        }
    }

    /**
     * Makes sure the guest sandbox app exists. Runs independently of the "seed if empty" check
     * so databases that already hold other data still get the playground.
     */
    void ensurePlayground() {
        if (configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("playground", "development").isPresent()) {
            return;
        }

        log.info("Seeding the playground sandbox configuration...");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("feature", Map.of("darkMode", false, "banner", "Welcome to the playground"));
        payload.put("limits", Map.of("requestsPerMinute", 60));
        payload.put("secure_settings", Map.of("apiToken", "pg_sandbox_token_123"));
        configurationService.createConfiguration(new ConfigRequest("playground", "development", payload,
                Map.of("purpose", "guest-sandbox")), "127.0.0.1", "sk-admin-key-001");
    }

    private void seedConfigurationsIfEmpty() {
        if (configurationRepository.count() > 0) {
            log.info("MongoDB configurations collection already contains data (count={}). Skipping bootstrap seeding.", configurationRepository.count());
            return;
        }

        log.info("Bootstrapping realistic multi-tenant demo configurations across applications, environments, and revisions...");

        final String ip = "127.0.0.1";
        final String key = "sk-admin-key-001";

        // ==========================================
        // 1. PAYMENT GATEWAY (prod, staging, dev)
        // ==========================================
        // Production V1
        Map<String, Object> payProdV1 = new LinkedHashMap<>();
        payProdV1.put("server", Map.of("port", 8443, "timeoutMs", 5000, "ssl", Map.of("enabled", true, "protocol", "TLSv1.3")));
        payProdV1.put("payment", Map.of(
                "currency", "USD",
                "provider", "stripe",
                "retryLimit", 3,
                "secure_settings", Map.of("apiKey", "sk_live_stripe_9921820491823", "webhookSecret", "whsec_live_99182a8bc92")
        ));
        payProdV1.put("database", Map.of(
                "host", "pg-cluster-prod.internal",
                "port", 5432,
                "poolSize", 20,
                "secure_settings", Map.of("password", "pg_prod_super_secret_pw_992")
        ));
        payProdV1.put("features", Map.of("fraudDetection", true, "instantPayouts", false, "zeroConfigVault", true));
        configurationService.createConfiguration(new ConfigRequest("payment-gateway", "production", payProdV1,
                Map.of("tier", "critical", "compliance", "pci-dss", "team", "fintech", "release", "v1.0.0")), ip, key);

        // Production V2 (Upgraded timeout, enabled instant payouts, increased pool)
        Map<String, Object> payProdV2 = new LinkedHashMap<>(payProdV1);
        payProdV2.put("server", Map.of("port", 8443, "timeoutMs", 3000, "ssl", Map.of("enabled", true, "protocol", "TLSv1.3")));
        payProdV2.put("payment", Map.of(
                "currency", "USD",
                "provider", "stripe",
                "retryLimit", 5,
                "instantPayouts", true,
                "secure_settings", Map.of("apiKey", "sk_live_stripe_9921820491823", "webhookSecret", "whsec_live_99182a8bc92")
        ));
        payProdV2.put("database", Map.of(
                "host", "pg-cluster-prod.internal",
                "port", 5432,
                "poolSize", 50,
                "secure_settings", Map.of("password", "pg_prod_super_secret_pw_992")
        ));
        payProdV2.put("features", Map.of("fraudDetection", true, "instantPayouts", true, "zeroConfigVault", true, "redisCaching", true));
        configurationService.createConfiguration(new ConfigRequest("payment-gateway", "production", payProdV2,
                Map.of("tier", "critical", "compliance", "pci-dss", "team", "fintech", "release", "v2.0.0")), ip, key);

        // Staging V1 & V2
        Map<String, Object> payStgV1 = new LinkedHashMap<>();
        payStgV1.put("server", Map.of("port", 8080, "timeoutMs", 6000));
        payStgV1.put("payment", Map.of("currency", "USD", "provider", "mock-stripe", "secure_settings", Map.of("apiKey", "sk_test_mock_secret_881")));
        payStgV1.put("database", Map.of("host", "pg-staging.internal", "port", 5432, "poolSize", 10, "secure_settings", Map.of("password", "pg_staging_pw_11")));
        configurationService.createConfiguration(new ConfigRequest("payment-gateway", "staging", payStgV1,
                Map.of("tier", "staging", "team", "fintech", "release", "v1.0.0")), ip, key);

        Map<String, Object> payStgV2 = new LinkedHashMap<>(payStgV1);
        payStgV2.put("server", Map.of("port", 8080, "timeoutMs", 4000));
        payStgV2.put("database", Map.of("host", "pg-staging.internal", "port", 5432, "poolSize", 15, "secure_settings", Map.of("password", "pg_staging_pw_11")));
        configurationService.createConfiguration(new ConfigRequest("payment-gateway", "staging", payStgV2,
                Map.of("tier", "staging", "team", "fintech", "release", "v2.0.0")), ip, key);

        // Development V1 & V2
        Map<String, Object> payDevV1 = new LinkedHashMap<>();
        payDevV1.put("server", Map.of("port", 3000, "timeoutMs", 10000));
        payDevV1.put("payment", Map.of("currency", "USD", "provider", "mock", "secure_settings", Map.of("apiKey", "sk_dev_mock_001")));
        payDevV1.put("database", Map.of("host", "localhost", "port", 5432, "poolSize", 5, "secure_settings", Map.of("password", "dev_pass_123")));
        configurationService.createConfiguration(new ConfigRequest("payment-gateway", "development", payDevV1,
                Map.of("tier", "dev", "team", "fintech", "release", "v1.0.0")), ip, key);

        Map<String, Object> payDevV2 = new LinkedHashMap<>(payDevV1);
        payDevV2.put("server", Map.of("port", 3000, "timeoutMs", 8000));
        configurationService.createConfiguration(new ConfigRequest("payment-gateway", "development", payDevV2,
                Map.of("tier", "dev", "team", "fintech", "release", "v1.1.0")), ip, key);

        // ==========================================
        // 2. AUTH SERVICE (prod, staging, dev)
        // ==========================================
        // Production V1
        Map<String, Object> authProdV1 = new LinkedHashMap<>();
        authProdV1.put("jwt", Map.of("issuer", "auth.zeroconfig.internal", "expiryMinutes", 60,
                "secure_settings", Map.of("secret", "jwt_hmac_sha256_super_secret_key_prod_01")));
        authProdV1.put("oauth", Map.of("providers", List.of("google", "github"),
                "secure_settings", Map.of("clientSecret", "gh_sec_prod_991823a")));
        authProdV1.put("rateLimit", Map.of("requestsPerMinute", 600, "burst", 1000));
        authProdV1.put("security", Map.of("mfaRequired", true, "maxFailedAttempts", 5));
        configurationService.createConfiguration(new ConfigRequest("auth-service", "production", authProdV1,
                Map.of("tier", "critical", "team", "security", "release", "v1.0.0")), ip, key);

        // Production V2
        Map<String, Object> authProdV2 = new LinkedHashMap<>(authProdV1);
        authProdV2.put("jwt", Map.of("issuer", "auth.zeroconfig.internal", "expiryMinutes", 30, "algorithm", "RS256",
                "secure_settings", Map.of("secret", "jwt_hmac_sha256_super_secret_key_prod_01")));
        authProdV2.put("oauth", Map.of("providers", List.of("google", "github", "apple"),
                "secure_settings", Map.of("clientSecret", "gh_sec_prod_991823a")));
        authProdV2.put("rateLimit", Map.of("requestsPerMinute", 1200, "burst", 2000));
        authProdV2.put("security", Map.of("mfaRequired", true, "passkeysEnabled", true, "maxFailedAttempts", 5));
        configurationService.createConfiguration(new ConfigRequest("auth-service", "production", authProdV2,
                Map.of("tier", "critical", "team", "security", "release", "v2.0.0")), ip, key);

        // Staging V1 & V2
        Map<String, Object> authStgV1 = new LinkedHashMap<>();
        authStgV1.put("jwt", Map.of("issuer", "auth-stg.internal", "expiryMinutes", 120,
                "secure_settings", Map.of("secret", "jwt_stg_secret_key_111")));
        authStgV1.put("rateLimit", Map.of("requestsPerMinute", 300));
        configurationService.createConfiguration(new ConfigRequest("auth-service", "staging", authStgV1,
                Map.of("tier", "staging", "team", "security", "release", "v1.0.0")), ip, key);

        Map<String, Object> authStgV2 = new LinkedHashMap<>(authStgV1);
        authStgV2.put("rateLimit", Map.of("requestsPerMinute", 500));
        configurationService.createConfiguration(new ConfigRequest("auth-service", "staging", authStgV2,
                Map.of("tier", "staging", "team", "security", "release", "v2.0.0")), ip, key);

        // Development V1 & V2
        Map<String, Object> authDevV1 = new LinkedHashMap<>();
        authDevV1.put("jwt", Map.of("issuer", "localhost", "expiryMinutes", 1440,
                "secure_settings", Map.of("secret", "dev_secret_jwt")));
        configurationService.createConfiguration(new ConfigRequest("auth-service", "development", authDevV1,
                Map.of("tier", "dev", "team", "security", "release", "v1.0.0")), ip, key);

        Map<String, Object> authDevV2 = new LinkedHashMap<>(authDevV1);
        authDevV2.put("jwt", Map.of("issuer", "localhost", "expiryMinutes", 720,
                "secure_settings", Map.of("secret", "dev_secret_jwt")));
        configurationService.createConfiguration(new ConfigRequest("auth-service", "development", authDevV2,
                Map.of("tier", "dev", "team", "security", "release", "v1.1.0")), ip, key);

        // ==========================================
        // 3. ORDER SERVICE (prod, staging, dev)
        // ==========================================
        // Production V1
        Map<String, Object> ordProdV1 = new LinkedHashMap<>();
        ordProdV1.put("order", Map.of("timeoutMs", 4000, "maxCartItems", 50, "autoCancelUnpaidMinutes", 15));
        ordProdV1.put("inventory", Map.of("syncIntervalSec", 60, "lowStockAlertThreshold", 5));
        ordProdV1.put("messaging", Map.of(
                "kafkaBrokers", "kafka-1.internal:9092,kafka-2.internal:9092",
                "topic", "orders.placed",
                "secure_settings", Map.of("saslPassword", "kafka_order_secret_pw_99")
        ));
        configurationService.createConfiguration(new ConfigRequest("order-service", "production", ordProdV1,
                Map.of("tier", "high", "team", "commerce", "release", "v1.0.0")), ip, key);

        // Production V2
        Map<String, Object> ordProdV2 = new LinkedHashMap<>(ordProdV1);
        ordProdV2.put("order", Map.of("timeoutMs", 3000, "maxCartItems", 100, "autoCancelUnpaidMinutes", 10, "dynamicPricing", true));
        ordProdV2.put("inventory", Map.of("syncIntervalSec", 30, "lowStockAlertThreshold", 10, "multiWarehouse", true));
        ordProdV2.put("messaging", Map.of(
                "kafkaBrokers", "kafka-1.internal:9092,kafka-2.internal:9092,kafka-3.internal:9092",
                "topic", "orders.placed.v2",
                "secure_settings", Map.of("saslPassword", "kafka_order_secret_pw_99")
        ));
        configurationService.createConfiguration(new ConfigRequest("order-service", "production", ordProdV2,
                Map.of("tier", "high", "team", "commerce", "release", "v2.0.0")), ip, key);

        // Staging V1 & V2
        Map<String, Object> ordStgV1 = new LinkedHashMap<>();
        ordStgV1.put("order", Map.of("timeoutMs", 5000, "maxCartItems", 20));
        ordStgV1.put("inventory", Map.of("syncIntervalSec", 120));
        configurationService.createConfiguration(new ConfigRequest("order-service", "staging", ordStgV1,
                Map.of("tier", "staging", "team", "commerce", "release", "v1.0.0")), ip, key);

        Map<String, Object> ordStgV2 = new LinkedHashMap<>(ordStgV1);
        ordStgV2.put("order", Map.of("timeoutMs", 4000, "maxCartItems", 30));
        configurationService.createConfiguration(new ConfigRequest("order-service", "staging", ordStgV2,
                Map.of("tier", "staging", "team", "commerce", "release", "v2.0.0")), ip, key);

        // Development V1 & V2
        Map<String, Object> ordDevV1 = new LinkedHashMap<>();
        ordDevV1.put("order", Map.of("timeoutMs", 8000, "maxCartItems", 10));
        configurationService.createConfiguration(new ConfigRequest("order-service", "development", ordDevV1,
                Map.of("tier", "dev", "team", "commerce", "release", "v1.0.0")), ip, key);

        Map<String, Object> ordDevV2 = new LinkedHashMap<>(ordDevV1);
        ordDevV2.put("order", Map.of("timeoutMs", 6000, "maxCartItems", 20));
        configurationService.createConfiguration(new ConfigRequest("order-service", "development", ordDevV2,
                Map.of("tier", "dev", "team", "commerce", "release", "v1.1.0")), ip, key);

        // ==========================================
        // 4. NOTIFICATION PIPELINE (prod & staging)
        // ==========================================
        Map<String, Object> notifProd = new LinkedHashMap<>();
        notifProd.put("email", Map.of("provider", "sendgrid", "secure_settings", Map.of("apiKey", "SG.prod_sendgrid_token_991283")));
        notifProd.put("sms", Map.of("provider", "twilio", "secure_settings", Map.of("authToken", "tw_auth_secret_991283")));
        notifProd.put("push", Map.of("apnsEnabled", true, "fcmEnabled", true));
        configurationService.createConfiguration(new ConfigRequest("notification-pipeline", "production", notifProd,
                Map.of("tier", "medium", "team", "comms", "release", "v1.0.0")), ip, key);

        Map<String, Object> notifStg = new LinkedHashMap<>();
        notifStg.put("email", Map.of("provider", "mock-smtp", "secure_settings", Map.of("apiKey", "mock_mail_key")));
        notifStg.put("push", Map.of("apnsEnabled", false, "fcmEnabled", false));
        configurationService.createConfiguration(new ConfigRequest("notification-pipeline", "staging", notifStg,
                Map.of("tier", "staging", "team", "comms", "release", "v1.0.0")), ip, key);

        log.info("Demo configurations successfully seeded into MongoDB Atlas.");
    }

    private void seedTelemetryIfEmpty() {
        if (telemetryRepository.count() > 0) {
            return;
        }

        log.info("Bootstrapping native time-series access telemetry events...");
        List<AccessTelemetry> telemetryBatch = new ArrayList<>();
        long now = System.currentTimeMillis();
        Random rnd = new Random(42); // deterministic seed

        String[] apps = {"payment-gateway", "auth-service", "order-service", "notification-pipeline"};
        String[] envs = {"production", "staging"};
        String[] ops = {"READ", "READ", "READ", "DECRYPT", "UPDATE"};

        // Generate 60 events across the past 60 minutes
        for (int i = 59; i >= 0; i--) {
            long eventTime = now - (i * 60_000L) + (rnd.nextInt(30_000) - 15_000);
            String app = apps[rnd.nextInt(apps.length)];
            String env = envs[rnd.nextInt(envs.length)];
            String op = ops[rnd.nextInt(ops.length)];
            String ip = "10.0.4." + (10 + rnd.nextInt(50));

            long latencyMs = 8 + rnd.nextInt(35);
            boolean cacheHit = rnd.nextDouble() < 0.80; // 80% cache hit
            int statusCode = rnd.nextDouble() < 0.96 ? 200 : 304;

            TelemetryMetadata meta = new TelemetryMetadata(app, env, op, ip);
            AccessTelemetry entry = new AccessTelemetry(new Date(eventTime), meta, latencyMs, cacheHit, statusCode);
            telemetryBatch.add(entry);
        }

        telemetryRepository.saveAll(telemetryBatch);
        log.info("Successfully seeded {} time-series telemetry events.", telemetryBatch.size());
    }

    private void seedBinaryConfigsIfEmpty() {
        if (binaryConfigRepository.count() > 0) {
            return;
        }

        log.info("Bootstrapping demo GridFS binary vault configurations...");
        try {
            String samplePem = """
                    -----BEGIN CERTIFICATE-----
                    MIIDXTCCAkWgAwIBAgIUQW6Z9F9k8f2aG4hJ0uL3yN5m1eAwDQYJKoZIhvcNAQEL
                    BQAwNDEyMDAGA1UEAwwpemVyb2NvbmZpZy12YXVsdC1tdGxzLWludGVybmFsLWNh
                    MB4XDTI2MDEwMTAwMDAwMFoXDTMxMDEwMTAwMDAwMFowNDEyMDAGA1UEAwwpemVy
                    b2NvbmZpZy12YXVsdC1tdGxzLWludGVybmFsLWNhMIIBIjANBgkqhkiG9w0BAQEF
                    AAOCAQ8AMIIBCgKCAQEA3gG9e...==
                    -----END CERTIFICATE-----
                    """;

            binaryConfigService.uploadBinaryConfig(
                    "payment-gateway",
                    "production",
                    "tls-cert.pem",
                    "application/x-pem-file",
                    samplePem.getBytes(StandardCharsets.UTF_8),
                    Map.of("purpose", "mTLS Inter-Service Mutual Authentication", "validDays", "365", "issuer", "Internal-CA"),
                    "127.0.0.1",
                    "sk-admin-key-001"
            );

            byte[] mockKeystore = "MOCK_JKS_BINARY_BLOB_FOR_ZERO_CONFIG_VAULT_SIGNING_KEYS".getBytes(StandardCharsets.UTF_8);
            binaryConfigService.uploadBinaryConfig(
                    "auth-service",
                    "production",
                    "signing-keystore.jks",
                    "application/octet-stream",
                    mockKeystore,
                    Map.of("algorithm", "RSA-4096", "alias", "jwt-signer", "keySize", "4096"),
                    "127.0.0.1",
                    "sk-admin-key-001"
            );

            log.info("Successfully seeded demo GridFS binary payloads.");
        } catch (Exception e) {
            log.warn("Notice: binary configuration bootstrap skipped: {}", e.getMessage());
        }
    }
}
