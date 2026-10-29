package com.vault.configmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vault.configmanager.dto.request.BulkConfigRequest;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.dto.response.BulkConfigResponse;
import com.vault.configmanager.exception.BulkTransactionException;
import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.service.CacheService;
import com.vault.configmanager.service.ConfigurationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class BulkImportIT extends MongoIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private CacheService cacheService;

    private ConfigRequest request(String app, String env, int counter) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("counter", counter);
        return new ConfigRequest(app, env, payload, new HashMap<>());
    }

    private List<VaultDocument> versions(String app) {
        return mongoTemplate.find(new Query(Criteria.where("applicationName").is(app))
                .with(Sort.by(Sort.Direction.ASC, "version")), VaultDocument.class);
    }

    private long audits(String action, String app) {
        return mongoTemplate.count(new Query(Criteria.where("action").is(action).and("applicationName").is(app)),
                AuditLog.class);
    }

    @Test
    void atomic_allValid_allCommittedAndAudited() {
        String appA = uniqueApp("bulk-a");
        String appB = uniqueApp("bulk-b");
        long before = audits("BULK_IMPORT_ATOMIC", "bulk");

        BulkConfigResponse response = configurationService.bulkImport(new BulkConfigRequest(List.of(
                request(appA, "production", 1), request(appA, "staging", 2), request(appB, "production", 3))),
                true, "127.0.0.1", "test");

        assertThat(response.getSucceeded()).isEqualTo(3);
        assertThat(versions(appA)).hasSize(2);
        assertThat(versions(appB)).hasSize(1);
        assertThat(audits("CREATE", appA)).isEqualTo(2);
        assertThat(audits("CREATE", appB)).isEqualTo(1);
        assertThat(audits("BULK_IMPORT_ATOMIC", "bulk")).isEqualTo(before + 1);
    }

    @Test
    void atomic_oneInvalid_nothingStored() {
        String appA = uniqueApp("bulk-a");
        String appB = uniqueApp("bulk-b");
        long before = audits("BULK_IMPORT_ROLLBACK", "bulk");

        assertThatThrownBy(() -> configurationService.bulkImport(new BulkConfigRequest(List.of(
                request(appA, "production", 1), request(appB, "qa", 2), request(appA, "staging", 3))),
                true, "127.0.0.1", "test"))
                .isInstanceOf(BulkTransactionException.class)
                .satisfies(e -> assertThat(((BulkTransactionException) e).getErrors()).isNotEmpty());

        assertThat(versions(appA)).isEmpty();
        assertThat(versions(appB)).isEmpty();
        assertThat(audits("CREATE", appA)).isZero();
        assertThat(audits("CREATE", appB)).isZero();
        assertThat(audits("BULK_IMPORT_ROLLBACK", "bulk")).isEqualTo(before + 1);
    }

    @Test
    void atomic_sameAppTwice_consecutiveVersions() {
        String app = uniqueApp("bulk-same");
        cacheService.put(app, "production", new Document("stale", true));

        configurationService.bulkImport(new BulkConfigRequest(List.of(
                request(app, "production", 1), request(app, "production", 2))), true, "127.0.0.1", "test");

        List<VaultDocument> stored = versions(app);
        assertThat(stored).extracting(VaultDocument::getVersion).containsExactly(1, 2);
        assertThat(cacheService.get(app, "production")).isEmpty();
    }

    @Test
    void bestEffort_unchanged() {
        String appA = uniqueApp("bulk-a");
        String appB = uniqueApp("bulk-b");

        BulkConfigResponse response = configurationService.bulkImport(new BulkConfigRequest(List.of(
                request(appA, "production", 1), request(appB, "qa", 2))), false, "127.0.0.1", "test");

        assertThat(response.getSucceeded()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(versions(appA)).hasSize(1);
        assertThat(versions(appB)).isEmpty();
    }
}
