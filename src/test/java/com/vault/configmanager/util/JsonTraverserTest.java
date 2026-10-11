package com.vault.configmanager.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.security.KeyProvider;
import com.vault.configmanager.service.CryptoService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JsonTraverserTest {

    private JsonTraverser jsonTraverser;
    private CryptoService cryptoService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        KeyProvider keyProvider = mock(KeyProvider.class);
        when(keyProvider.getMasterKey()).thenReturn("test-master-key-32-chars-long!");
        cryptoService = new CryptoService(keyProvider);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        jsonTraverser = new JsonTraverser(cryptoService, objectMapper);
    }

    @Test
    void encryptAndDecrypt_singleLevelSecureSettings() throws Exception {
        String json = "{\n" +
                "  \"appName\": \"order-service\",\n" +
                "  \"port\": 8080,\n" +
                "  \"secure_settings\": {\n" +
                "    \"api_key\": \"secret-stripe-key-99\",\n" +
                "    \"db_password\": \"superSecretPass123\"\n" +
                "  }\n" +
                "}";

        JsonNode root = objectMapper.readTree(json);
        JsonNode encrypted = jsonTraverser.encrypt(root);

        // Verify non-secure settings untouched
        assertEquals("order-service", encrypted.get("appName").asText());
        assertEquals(8080, encrypted.get("port").asInt());

        // Verify secure settings were encrypted
        JsonNode secure = encrypted.get("secure_settings");
        assertNotNull(secure);

        JsonNode apiKeyNode = secure.get("api_key");
        assertTrue(apiKeyNode.isObject(), "Encrypted node must be a JSON object");
        assertEquals("AES-256-GCM", apiKeyNode.get("encryption_algo").asText());
        assertNotNull(apiKeyNode.get("iv").asText());
        assertNotNull(apiKeyNode.get("cipher_text").asText());

        JsonNode dbPassNode = secure.get("db_password");
        assertEquals("AES-256-GCM", dbPassNode.get("encryption_algo").asText());

        // Now test decryption restores exact plaintext
        JsonNode decrypted = jsonTraverser.decrypt(encrypted);
        assertEquals("secret-stripe-key-99", decrypted.get("secure_settings").get("api_key").asText());
        assertEquals("superSecretPass123", decrypted.get("secure_settings").get("db_password").asText());
    }

    @Test
    void encrypt_deeplyNestedSecureSettings() throws Exception {
        String json = "{\n" +
                "  \"cluster\": {\n" +
                "    \"region\": \"us-east-1\",\n" +
                "    \"database\": {\n" +
                "      \"host\": \"db.internal\",\n" +
                "      \"secure_settings\": {\n" +
                "        \"token\": \"vault-nested-token-xyz\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JsonNode root = objectMapper.readTree(json);
        JsonNode encrypted = jsonTraverser.encrypt(root);

        JsonNode nestedSecure = encrypted.get("cluster").get("database").get("secure_settings");
        assertNotNull(nestedSecure);
        assertTrue(nestedSecure.get("token").isObject());
        assertEquals("AES-256-GCM", nestedSecure.get("token").get("encryption_algo").asText());

        JsonNode decrypted = jsonTraverser.decrypt(encrypted);
        assertEquals("vault-nested-token-xyz",
                decrypted.get("cluster").get("database").get("secure_settings").get("token").asText());
    }

    @Test
    void encrypt_idempotence_doesNotDoubleEncrypt() throws Exception {
        String json = "{\n" +
                "  \"secure_settings\": {\n" +
                "    \"secret\": \"my-pass\"\n" +
                "  }\n" +
                "}";

        JsonNode root = objectMapper.readTree(json);
        JsonNode encryptedOnce = jsonTraverser.encrypt(root);
        String firstIv = encryptedOnce.get("secure_settings").get("secret").get("iv").asText();

        // Encrypting an already-encrypted tree must not change it
        JsonNode encryptedTwice = jsonTraverser.encrypt(encryptedOnce);
        String secondIv = encryptedTwice.get("secure_settings").get("secret").get("iv").asText();

        assertEquals(firstIv, secondIv, "Already encrypted node must not be re-encrypted");
    }

    @Test
    void documentAndMapOverloads_workSeamlessly() {
        Document doc = new Document("service", "billing")
                .append("secure_settings", new Document("private_key", "rsa-priv-key-data"));

        Document encryptedDoc = jsonTraverser.encryptDocument(doc);
        assertNotNull(encryptedDoc);
        Document encryptedSettings = (Document) encryptedDoc.get("secure_settings");
        Document encryptedKey = (Document) encryptedSettings.get("private_key");
        assertEquals("AES-256-GCM", encryptedKey.getString("encryption_algo"));

        Document decryptedDoc = jsonTraverser.decryptDocument(encryptedDoc);
        Document decryptedSettings = (Document) decryptedDoc.get("secure_settings");
        assertEquals("rsa-priv-key-data", decryptedSettings.getString("private_key"));
    }
}
