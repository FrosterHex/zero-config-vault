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
    void isSensitivePath_detectsSecureSettingsSegment() {
        assertTrue(SecretMaskingUtil.isSensitivePath("database.secure_settings.password"));
        assertFalse(SecretMaskingUtil.isSensitivePath("database.host"));
        assertTrue(SecretMaskingUtil.isSensitivePath("auth.password"));
    }

    @Test
    void maskDiff_masksSecretValuesButKeepsPaths() {
        Map<String, Object> added = new java.util.LinkedHashMap<>();
        added.put("payment.secure_settings.apiKey", "sk_x");
        added.put("payment.currency", "USD");
        Map<String, Map<String, Object>> modified = new java.util.LinkedHashMap<>();
        modified.put("database.secure_settings.password", Map.of("oldValue", "a", "newValue", "b"));
        com.vault.configmanager.dto.response.DiffResponse diff =
                new com.vault.configmanager.dto.response.DiffResponse(1, 2, added, Map.of(), modified, 0);

        com.vault.configmanager.dto.response.DiffResponse masked = SecretMaskingUtil.maskDiff(diff);

        assertEquals("********", masked.getAdded().get("payment.secure_settings.apiKey"));
        assertEquals("USD", masked.getAdded().get("payment.currency"));
        assertEquals("********", masked.getModified().get("database.secure_settings.password").get("oldValue"));
        assertEquals("********", masked.getModified().get("database.secure_settings.password").get("newValue"));
        assertEquals(added.keySet(), masked.getAdded().keySet());
        assertEquals(modified.keySet(), masked.getModified().keySet());
        assertEquals("sk_x", diff.getAdded().get("payment.secure_settings.apiKey"));
    }

    @Test
    void maskDiff_masksSecretsNestedInListValues() {
        Map<String, Object> added = new java.util.LinkedHashMap<>();
        added.put("servers", List.of(Map.of("secure_settings", Map.of("k", "v"), "host", "h")));
        com.vault.configmanager.dto.response.DiffResponse diff =
                new com.vault.configmanager.dto.response.DiffResponse(1, 2, added, Map.of(), Map.of(), 0);

        com.vault.configmanager.dto.response.DiffResponse masked = SecretMaskingUtil.maskDiff(diff);

        @SuppressWarnings("unchecked")
        Map<String, Object> element = ((List<Map<String, Object>>) masked.getAdded().get("servers")).get(0);
        assertEquals(Map.of("k", "********"), element.get("secure_settings"));
        assertEquals("h", element.get("host"));
    }

    @Test
    void nullHandling_returnsNullOrOriginal() {
        assertNull(SecretMaskingUtil.maskDocument(null));
        assertNull(SecretMaskingUtil.maskMap(null));
        assertNull(SecretMaskingUtil.maskAuditText(null));
        assertEquals("", SecretMaskingUtil.maskAuditText(""));
    }
}
