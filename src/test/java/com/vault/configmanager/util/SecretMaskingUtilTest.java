package com.vault.configmanager.util;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecretMaskingUtilTest {

    @Test
    void maskDocument_masksSensitiveKeysRecursively() {
        Document inner = new Document("username", "admin")
                .append("password", "super-secret-pass")
                .append("token", "jwt-token-xyz");

        Document doc = new Document("appName", "order-service")
                .append("credentials", inner)
                .append("secure_settings", new Document("api_key", "sec-12345"))
                .append("tags", List.of("prod", "v1"));

        Document masked = SecretMaskingUtil.maskDocument(doc);

        assertNotNull(masked);
        assertEquals("order-service", masked.getString("appName"));
        assertEquals(List.of("prod", "v1"), masked.get("tags"));

        Document maskedCreds = (Document) masked.get("credentials");
        assertEquals(SecretMaskingUtil.MASKED_PLACEHOLDER, maskedCreds.get("password"));
        assertEquals(SecretMaskingUtil.MASKED_PLACEHOLDER, maskedCreds.get("token"));
        assertEquals("admin", maskedCreds.getString("username"));

        Document maskedSecure = (Document) masked.get("secure_settings");
        assertEquals(SecretMaskingUtil.MASKED_PLACEHOLDER, maskedSecure.get("api_key"));
    }

    @Test
    void maskMap_masksSensitiveKeysInStandardMap() {
        Map<String, Object> map = Map.of(
                "host", "localhost",
                "client_secret", "oauth-secret-999",
                "nested", Map.of("private_key", "-----BEGIN PRIVATE KEY-----")
        );

        Map<String, Object> masked = SecretMaskingUtil.maskMap(map);

        assertNotNull(masked);
        assertEquals("localhost", masked.get("host"));
        assertEquals(SecretMaskingUtil.MASKED_PLACEHOLDER, masked.get("client_secret"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) masked.get("nested");
        assertEquals(SecretMaskingUtil.MASKED_PLACEHOLDER, nested.get("private_key"));
    }

    @Test
    void maskAuditText_replacesSensitiveTokensInStrings() {
        String logMessage = "Failed authentication for user admin with password=cleartext-password-123 and token: eyJhbGciOi...";
        String sanitized = SecretMaskingUtil.maskAuditText(logMessage);

        assertFalse(sanitized.contains("cleartext-password-123"));
        assertFalse(sanitized.contains("eyJhbGciOi..."));
        assertTrue(sanitized.contains(SecretMaskingUtil.MASKED_PLACEHOLDER));
    }

    @Test
    void isSensitiveKey_detectsVariousNamingConventions() {
        assertTrue(SecretMaskingUtil.isSensitiveKey("password"));
        assertTrue(SecretMaskingUtil.isSensitiveKey("api_key"));
        assertTrue(SecretMaskingUtil.isSensitiveKey("apiKey"));
        assertTrue(SecretMaskingUtil.isSensitiveKey("access_token"));
        assertTrue(SecretMaskingUtil.isSensitiveKey("secure_settings"));
        assertTrue(SecretMaskingUtil.isSensitiveKey("client_secret"));

        assertFalse(SecretMaskingUtil.isSensitiveKey("username"));
        assertFalse(SecretMaskingUtil.isSensitiveKey("host"));
        assertFalse(SecretMaskingUtil.isSensitiveKey("port"));
        assertFalse(SecretMaskingUtil.isSensitiveKey(null));
    }

    @Test
    void nullHandling_returnsNullOrOriginal() {
        assertNull(SecretMaskingUtil.maskDocument(null));
        assertNull(SecretMaskingUtil.maskMap(null));
        assertNull(SecretMaskingUtil.maskAuditText(null));
        assertEquals("", SecretMaskingUtil.maskAuditText(""));
    }
}
