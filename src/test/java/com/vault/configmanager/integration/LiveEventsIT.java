package com.vault.configmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoDatabase;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.dto.response.ConfigChangeEvent;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.service.BinaryConfigService;
import com.vault.configmanager.service.ChangeStreamService;
import com.vault.configmanager.service.ChangeStreamServiceTestAccess;
import com.vault.configmanager.service.ConfigurationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Verifies that every write produces exactly one live event, sourced from the MongoDB change stream.
 */
class LiveEventsIT extends MongoIntegrationTest {

    private static final String ENV = "production";

    @Autowired
    private ChangeStreamService changeStreamService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private BinaryConfigService binaryConfigService;

    /** Registers a listener that keeps only the events of the given app. */
    private List<ConfigChangeEvent> listen(String app) {
        List<ConfigChangeEvent> events = new CopyOnWriteArrayList<>();
        changeStreamService.addListener(e -> {
            if (app.equals(e.getApplicationName())) {
                events.add(e);
            }
        });
        return events;
    }

    private static void awaitCount(List<ConfigChangeEvent> events, int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && events.size() < expected) {
            Thread.sleep(100);
        }
        assertThat(events).as("events after waiting").hasSizeGreaterThanOrEqualTo(expected);
    }

    private ConfigResponse write(String app, int counter) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("counter", counter);
        return configurationService.createConfiguration(
                new ConfigRequest(app, ENV, payload, new HashMap<>()), "127.0.0.1", "test");
    }

    @Test
    void modeIsChangeStream_onReplicaSet() {
        assertThat(changeStreamService.getEventMode()).isEqualTo(ChangeStreamService.EventMode.CHANGE_STREAM);
        assertThat(changeStreamService.servicesShouldBroadcast()).isFalse();
        assertThat(changeStreamService.isChangeStreamActive()).isTrue();
    }

    @Test
    void oneWrite_exactlyOneEvent() throws Exception {
        String app = uniqueApp("live");
        List<ConfigChangeEvent> events = listen(app);
        ConfigResponse saved = write(app, 1);

        awaitCount(events, 1, 10_000);
        Thread.sleep(2_000);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("CREATE");
        assertThat(events.get(0).getVersion()).isEqualTo(saved.getVersion());
        assertThat(events.get(0).getEnvironment()).isEqualTo(ENV);
        assertThat(events.get(0).getDocumentHash()).isEqualTo(saved.getDocumentHash());
    }

    @Test
    void twoQuickWrites_twoEventsInOrder() throws Exception {
        String app = uniqueApp("live");
        List<ConfigChangeEvent> events = listen(app);
        int first = write(app, 1).getVersion();
        write(app, 2);

        awaitCount(events, 2, 10_000);
        Thread.sleep(1_000);

        assertThat(events).extracting(ConfigChangeEvent::getVersion).containsExactly(first, first + 1);
    }

    @Test
    void retentionAndTtl_produceNoEvents() throws Exception {
        MongoDatabase admin = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase("admin");
        admin.runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", 1));
        try {
            String app = uniqueApp("live");
            List<ConfigChangeEvent> events = listen(app);
            for (int i = 0; i < 8; i++) {
                write(app, i);
            }
            awaitCount(events, 8, 10_000);

            mongoTemplate.updateFirst(
                    new Query(Criteria.where("applicationName").is(app).and("environment").is(ENV).and("version").is(1)),
                    Update.update("expiresAt", LocalDateTime.now().minusMinutes(1)), VaultDocument.class);
            Query v1 = new Query(Criteria.where("applicationName").is(app).and("environment").is(ENV).and("version").is(1));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline && mongoTemplate.exists(v1, VaultDocument.class)) {
                Thread.sleep(500);
            }
            assertThat(mongoTemplate.exists(v1, VaultDocument.class)).as("v1 removed by TTL").isFalse();
            Thread.sleep(2_000);

            assertThat(events).hasSize(8);
            assertThat(events).allMatch(e -> "CREATE".equals(e.getEventType()));
        } finally {
            admin.runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", 60));
        }
    }

    @Test
    void rollback_isRollbackEvent() throws Exception {
        String app = uniqueApp("live");
        List<ConfigChangeEvent> events = listen(app);
        write(app, 1);
        write(app, 2);
        ConfigResponse rolledBack = configurationService.rollbackToVersion(app, ENV, 1, "127.0.0.1", "test");

        awaitCount(events, 3, 10_000);
        Thread.sleep(1_000);

        assertThat(events).hasSize(3);
        assertThat(events).filteredOn(e -> "ROLLBACK".equals(e.getEventType())).hasSize(1);
        ConfigChangeEvent rollback = events.get(2);
        assertThat(rollback.getEventType()).isEqualTo("ROLLBACK");
        assertThat(rollback.getVersion()).isEqualTo(rolledBack.getVersion());
    }

    @Test
    void binaryUpload_oneBinaryEvent() throws Exception {
        String app = uniqueApp("live");
        List<ConfigChangeEvent> events = listen(app);
        binaryConfigService.uploadBinaryConfig(app, ENV, "cert.pem", "application/x-pem-file",
                "binary-live-event".getBytes(), new HashMap<>(), "127.0.0.1", "test");

        awaitCount(events, 1, 10_000);
        Thread.sleep(1_000);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("BINARY_CREATE");
        assertThat(events.get(0).getVersion()).isEqualTo(1);
        assertThat(events.get(0).getMetadata()).containsEntry("isBinary", true);
    }

    @Test
    void reconnectAfterCursorClosed_noDuplicates() throws Exception {
        String app = uniqueApp("live");
        List<ConfigChangeEvent> events = listen(app);
        int reconnectsBefore = ChangeStreamServiceTestAccess.reconnectCount(changeStreamService);
        ChangeStreamServiceTestAccess.closeCurrentCursor(changeStreamService);
        ConfigResponse saved = write(app, 1);

        awaitCount(events, 1, 15_000);
        Thread.sleep(2_000);

        List<Integer> versions = new ArrayList<>();
        events.forEach(e -> versions.add(e.getVersion()));
        assertThat(versions).containsExactly(saved.getVersion());
        assertThat(ChangeStreamServiceTestAccess.reconnectCount(changeStreamService))
                .as("loop reconnected after the cursor was closed").isGreaterThan(reconnectsBefore);
    }

    @Test
    void binaryRollback_oneBinaryRollbackEvent() throws Exception {
        String app = uniqueApp("live");
        List<ConfigChangeEvent> events = listen(app);
        binaryConfigService.uploadBinaryConfig(app, ENV, "cert.pem", "application/x-pem-file",
                "binary-v1".getBytes(), new HashMap<>(), "127.0.0.1", "test");
        binaryConfigService.uploadBinaryConfig(app, ENV, "cert.pem", "application/x-pem-file",
                "binary-v2".getBytes(), new HashMap<>(), "127.0.0.1", "test");
        binaryConfigService.rollbackBinaryConfig(app, ENV, "cert.pem", 1, "127.0.0.1", "test");

        awaitCount(events, 3, 10_000);
        Thread.sleep(1_000);

        assertThat(events).hasSize(3);
        assertThat(events).filteredOn(e -> "BINARY_ROLLBACK".equals(e.getEventType())).hasSize(1);
        ConfigChangeEvent rollback = events.get(2);
        assertThat(rollback.getEventType()).isEqualTo("BINARY_ROLLBACK");
        assertThat(rollback.getVersion()).isEqualTo(3);
        assertThat(rollback.getMetadata()).containsEntry("isBinary", true);
    }
}
