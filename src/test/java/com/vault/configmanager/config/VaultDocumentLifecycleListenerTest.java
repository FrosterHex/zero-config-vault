package com.vault.configmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.security.KeyProvider;
import com.vault.configmanager.service.CryptoService;
import com.vault.configmanager.util.JsonTraverser;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultDocumentLifecycleListenerTest {

    private VaultDocumentLifecycleListener listener;
    private JsonTraverser jsonTraverser;

    @BeforeEach
    void setUp() {
        KeyProvider keyProvider = mock(KeyProvider.class);
        when(keyProvider.getMasterKey()).thenReturn("master-secret-key-32-bytes-long!");
        CryptoService cryptoService = new CryptoService(keyProvider);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        jsonTraverser = new JsonTraverser(cryptoService, objectMapper);
        listener = new VaultDocumentLifecycleListener(jsonTraverser);
    }

    @Test
    void onBeforeConvert_shouldInterceptAndEncryptSecureSettings() {
        Document payload = new Document("appName", "payment-gateway")
                .append("secure_settings", new Document("client_secret", "sk_live_998877"));

        VaultDocument doc = new VaultDocument(
                "payment-gateway",
                "production",
                1,
                "some-hash",
                payload,
                null,
                LocalDateTime.now(),
                null
        );

        BeforeConvertEvent<VaultDocument> event = new BeforeConvertEvent<>(doc, "configurations");
        listener.onBeforeConvert(event);

        Document updatedPayload = doc.getPayload();
        assertNotNull(updatedPayload);

        Document secureSettings = (Document) updatedPayload.get("secure_settings");
        assertNotNull(secureSettings);

        Document secretNode = (Document) secureSettings.get("client_secret");
        assertNotNull(secretNode, "client_secret must be transformed to an encrypted node");
        assertEquals("AES-256-GCM", secretNode.getString("encryption_algo"));
        assertNotNull(secretNode.getString("iv"));
        assertNotNull(secretNode.getString("cipher_text"));

        // Verify decryption recovers original secret
        Document decrypted = jsonTraverser.decryptDocument(updatedPayload);
        Document decryptedSecure = (Document) decrypted.get("secure_settings");
        assertEquals("sk_live_998877", decryptedSecure.getString("client_secret"));
    }
}
