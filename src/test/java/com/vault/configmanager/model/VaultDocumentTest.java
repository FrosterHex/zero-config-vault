package com.vault.configmanager.model;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaultDocumentTest {

    @Test
    void classLevelAnnotations_shouldMatchNoSqlRequirements() {
        // Verify MongoDB @Document collection name
        org.springframework.data.mongodb.core.mapping.Document docAnnotation =
                VaultDocument.class.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);
        assertNotNull(docAnnotation, "@Document annotation must be present");
        assertEquals("configurations", docAnnotation.collection(), "Collection must be named 'configurations'");

        // Verify Compound Unique Index for temporal immutability
        CompoundIndex compoundIndex = VaultDocument.class.getAnnotation(CompoundIndex.class);
        assertNotNull(compoundIndex, "@CompoundIndex annotation must be present");
        assertTrue(compoundIndex.unique(), "Compound index must be unique to prevent version collisions");
        assertTrue(compoundIndex.def().contains("'applicationName': 1"));
        assertTrue(compoundIndex.def().contains("'environment': 1"));
        assertTrue(compoundIndex.def().contains("'version': 1"));
    }

    @Test
    void fieldLevelAnnotations_shouldBeConfigured() throws NoSuchFieldException {
        Field idField = VaultDocument.class.getDeclaredField("id");
        assertNotNull(idField.getAnnotation(Id.class), "@Id must be present on id field");

        Field appField = VaultDocument.class.getDeclaredField("applicationName");
        assertNotNull(appField.getAnnotation(Indexed.class), "@Indexed must be present on applicationName");

        Field envField = VaultDocument.class.getDeclaredField("environment");
        assertNotNull(envField.getAnnotation(Indexed.class), "@Indexed must be present on environment");

        Field expiresAtField = VaultDocument.class.getDeclaredField("expiresAt");
        Indexed ttlIndex = expiresAtField.getAnnotation(Indexed.class);
        assertNotNull(ttlIndex, "@Indexed must be present on expiresAt");
        assertEquals("90d", ttlIndex.expireAfter(), "TTL index must be configured for 90d auto-expiry");
    }

    @Test
    void constructorAndGetters_shouldHandleDynamicPayload() {
        Document payload = new Document("database", new Document("host", "db.internal").append("port", 27017))
                .append("features", java.util.List.of("auth", "audit"));
        Map<String, String> tags = Map.of("release", "v1.0.0", "author", "security-team");
        LocalDateTime now = LocalDateTime.now();

        VaultDocument doc = new VaultDocument(
                "payment-service",
                "production",
                1,
                "a1b2c3d4e5f6",
                payload,
                tags,
                now,
                null
        );

        assertEquals("payment-service", doc.getApplicationName());
        assertEquals("production", doc.getEnvironment());
        assertEquals(1, doc.getVersion());
        assertEquals("a1b2c3d4e5f6", doc.getDocumentHash());
        assertEquals(payload, doc.getPayload());
        assertEquals("v1.0.0", doc.getTags().get("release"));
        assertEquals(now, doc.getCreatedAt());
        assertNull(doc.getExpiresAt());
    }

    @Test
    void mapConstructor_shouldConvertMapToBsonDocument() {
        Map<String, Object> mapPayload = Map.of("timeoutMs", 5000, "retryCount", 3);
        VaultDocument doc = new VaultDocument(
                "auth-service",
                "staging",
                2,
                "hash123",
                mapPayload,
                Map.of(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(90)
        );

        assertNotNull(doc.getPayload());
        assertEquals(5000, doc.getPayload().get("timeoutMs"));
        assertEquals(3, doc.getPayload().get("retryCount"));
        assertNotNull(doc.getExpiresAt());
    }

    @Test
    void equalsAndHashCode_shouldReflectEntityIdentity() {
        VaultDocument doc1 = new VaultDocument();
        doc1.setId("doc-1");
        doc1.setApplicationName("app-a");
        doc1.setEnvironment("prod");
        doc1.setVersion(1);

        VaultDocument doc2 = new VaultDocument();
        doc2.setId("doc-1");
        doc2.setApplicationName("app-a");
        doc2.setEnvironment("prod");
        doc2.setVersion(1);

        VaultDocument doc3 = new VaultDocument();
        doc3.setId("doc-2");
        doc3.setApplicationName("app-a");
        doc3.setEnvironment("prod");
        doc3.setVersion(2);

        assertEquals(doc1, doc2);
        assertEquals(doc1.hashCode(), doc2.hashCode());
        assertNotEquals(doc1, doc3);
    }
}
