package com.vault.configmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.service.ConfigurationService;
import com.vault.configmanager.dto.request.ConfigRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

class SecretStorageIT extends MongoIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    private long storedCount(String app) {
        return mongoTemplate.getCollection("configurations")
                .countDocuments(new Document("applicationName", app));
    }

    private Map<String, Object> envelope() {
        return Map.of("iv", "aXY=", "cipher_text", "Y3Q=", "encryption_algo", "AES-256-GCM");
    }

    private Map<String, Object> entry(String app, Object secret) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("applicationName", app);
        entry.put("environment", "development");
        entry.put("payload", Map.of("secure_settings", Map.of("token", secret)));
        entry.put("tags", Map.of());
        return entry;
    }

    private ResponseEntity<String> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-API-Key", "sk-dev-key-001");
        RestTemplate rest = new RestTemplate();
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rest.postForEntity("http://localhost:" + port + path, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void storedDocumentNeverHoldsPlaintext() {
        String app = uniqueApp("secret");
        Map<String, Object> payload = new HashMap<>();
        payload.put("secure_settings", new HashMap<>(Map.of("token", "plain-xyz-123")));

        configurationService.createConfiguration(
                new ConfigRequest(app, "development", payload, new HashMap<>()), "127.0.0.1", "test");

        Document raw = mongoTemplate.getCollection("configurations")
                .find(new Document("applicationName", app)).first();
        assertThat(raw).isNotNull();
        assertThat(raw.toJson()).doesNotContain("plain-xyz-123");

        ConfigResponse read = configurationService.getLatestConfiguration(app, "development", "127.0.0.1", "test");
        assertThat(read.getPayload().toString()).contains("plain-xyz-123");
    }

    @Test
    void envelopeRejectedOverHttp() {
        String app = uniqueApp("envelope");

        ResponseEntity<String> response = post("/api/v1/vault/config", entry(app, envelope()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(storedCount(app)).isZero();
    }

    @Test
    void bulkEnvelope_perEntryError() {
        String okAtomic = uniqueApp("ok");
        String badAtomic = uniqueApp("bad");

        ResponseEntity<String> atomic = post("/api/v1/vault/config/bulk?atomic=true",
                Map.of("configs", List.of(entry(okAtomic, "fine"), entry(badAtomic, envelope()))));

        assertThat(atomic.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(storedCount(okAtomic)).isZero();
        assertThat(storedCount(badAtomic)).isZero();

        String okBest = uniqueApp("ok");
        String badBest = uniqueApp("bad");

        post("/api/v1/vault/config/bulk?atomic=false",
                Map.of("configs", List.of(entry(okBest, "fine"), entry(badBest, envelope()))));

        assertThat(storedCount(okBest)).isEqualTo(1);
        assertThat(storedCount(badBest)).isZero();
    }
}
