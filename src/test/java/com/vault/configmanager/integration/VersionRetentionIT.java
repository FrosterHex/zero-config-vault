package com.vault.configmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoDatabase;
import com.vault.configmanager.config.MongoConfig;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.service.ConfigurationService;
import com.vault.configmanager.service.VersionRetentionService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class VersionRetentionIT extends MongoIntegrationTest {

    private static final String ENV = "production";

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private MongoConfig mongoConfig;

    @Autowired
    private com.vault.configmanager.config.RetentionStartupRunner runner;

    private void write(String app, int times) {
        for (int i = 0; i < times; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("counter", i);
            configurationService.createConfiguration(
                    new ConfigRequest(app, ENV, payload, new HashMap<>()), "127.0.0.1", "test");
        }
    }

    private List<VaultDocument> versions(String app) {
        return mongoTemplate.find(new Query(Criteria.where("applicationName").is(app).and("environment").is(ENV))
                .with(Sort.by(Sort.Direction.DESC, "version")), VaultDocument.class);
    }

    private VaultDocument version(String app, int v) {
        return versions(app).stream().filter(d -> d.getVersion() == v).findFirst().orElseThrow();
    }

    private Document ttlIndex() {
        for (Document idx : mongoTemplate.getCollection("configurations").listIndexes()) {
            Document key = idx.get("key", Document.class);
            if (key != null && key.size() == 1 && key.containsKey("expiresAt")) {
                return idx;
            }
        }
        return null;
    }

    @Test
    void eightWrites_keepNewestSixWithoutExpiry() {
        String app = uniqueApp("ret");
        write(app, 8);
        LocalDateTime lo = LocalDateTime.now().plusDays(89);
        LocalDateTime hi = LocalDateTime.now().plusDays(91);
        for (int v = 8; v >= 3; v--) {
            assertThat(version(app, v).getExpiresAt()).as("v" + v).isNull();
        }
        for (int v = 2; v >= 1; v--) {
            assertThat(version(app, v).getExpiresAt()).as("v" + v).isBetween(lo, hi);
        }
    }

    @Test
    void fewerThanWindow_noExpiry() {
        String app = uniqueApp("ret");
        write(app, 4);
        assertThat(versions(app)).hasSize(4).allMatch(d -> d.getExpiresAt() == null);
    }

    @Test
    void existingExpiryNotExtended() {
        String app = uniqueApp("ret");
        write(app, 8);
        LocalDateTime v1 = version(app, 1).getExpiresAt();
        assertThat(v1).isNotNull();
        write(app, 1);
        assertThat(version(app, 1).getExpiresAt()).isEqualTo(v1);
        assertThat(version(app, 3).getExpiresAt()).isNotNull();
    }

    @Test
    void ttlIndexIsExactDate() {
        Document idx = ttlIndex();
        assertThat(idx).isNotNull();
        assertThat(((Number) idx.get("expireAfterSeconds")).longValue()).isZero();
    }

    @Test
    void pastExpiryIsDeletedByMongo() throws Exception {
        MongoDatabase admin = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase("admin");
        admin.runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", 1));
        try {
            String app = uniqueApp("ret");
            write(app, 2);
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("applicationName").is(app).and("version").is(1)),
                    Update.update("expiresAt", LocalDateTime.now().minusMinutes(1)), VaultDocument.class);
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline
                    && versions(app).stream().anyMatch(d -> d.getVersion() == 1)) {
                Thread.sleep(500);
            }
            assertThat(versions(app)).extracting(VaultDocument::getVersion).containsExactly(2);
        } finally {
            admin.runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", 60));
        }
    }

    @Test
    void oldIndexMigratedInPlace() {
        Document idx = ttlIndex();
        if (idx != null) {
            mongoTemplate.getCollection("configurations").dropIndex(idx.getString("name"));
        }
        mongoTemplate.getCollection("configurations").createIndex(new Document("expiresAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(7776000L, java.util.concurrent.TimeUnit.SECONDS));
        try {
            mongoConfig.ensureExpiryTtlIndex(mongoTemplate.getMongoDatabaseFactory().getMongoDatabase());
            Document after = ttlIndex();
            assertThat(after).isNotNull();
            assertThat(((Number) after.get("expireAfterSeconds")).longValue()).isZero();
        } finally {
            mongoConfig.ensureExpiryTtlIndex(mongoTemplate.getMongoDatabaseFactory().getMongoDatabase());
        }
    }

    @Test
    void startupRunner_clearsProtectedPastExpiryBeforeSwitchingIndex() {
        String app = uniqueApp("ret");
        write(app, 3);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("applicationName").is(app).and("version").is(3)),
                Update.update("expiresAt", LocalDateTime.now().minusDays(1)), VaultDocument.class);
        Document idx = ttlIndex();
        if (idx != null) {
            mongoTemplate.getCollection("configurations").dropIndex(idx.getString("name"));
        }
        mongoTemplate.getCollection("configurations").createIndex(new Document("expiresAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(7776000L, java.util.concurrent.TimeUnit.SECONDS));
        try {
            runner.run(null);
            assertThat(version(app, 3).getExpiresAt()).isNull();
            assertThat(((Number) ttlIndex().get("expireAfterSeconds")).longValue()).isZero();
        } finally {
            mongoConfig.ensureExpiryTtlIndex(mongoTemplate.getMongoDatabaseFactory().getMongoDatabase());
        }
    }

    @Test
    void expiryDisabledClearsDates() {
        String app = uniqueApp("ret");
        write(app, 8);
        assertThat(versions(app)).anyMatch(d -> d.getExpiresAt() != null);
        new VersionRetentionService(mongoTemplate, 5, 0).applyRetention(app, ENV);
        assertThat(versions(app)).allMatch(d -> d.getExpiresAt() == null);
    }

    @Test
    void rollbackAppliesRetentionAfterSave() {
        String app = uniqueApp("ret");
        write(app, 7);
        configurationService.rollbackToVersion(app, ENV, 1, "127.0.0.1", "test");
        assertThat(version(app, 8)).isNotNull();
        for (int v = 8; v >= 3; v--) {
            assertThat(version(app, v).getExpiresAt()).as("v" + v).isNull();
        }
    }
}
