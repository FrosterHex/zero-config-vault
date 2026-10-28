package com.vault.configmanager.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CachedConfigTest {

    @Test
    void modelAnnotations_shouldIncludeTtlIndex() throws NoSuchFieldException {
        org.springframework.data.mongodb.core.mapping.Document docAnnotation =
                CachedConfig.class.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);
        assertNotNull(docAnnotation);
        assertEquals("config_cache", docAnnotation.collection());

        Field cacheKeyField = CachedConfig.class.getDeclaredField("cacheKey");
        assertNotNull(cacheKeyField.getAnnotation(Id.class), "@Id must be on cacheKey");

        Field cachedAtField = CachedConfig.class.getDeclaredField("cachedAt");
        Indexed ttlIndexed = cachedAtField.getAnnotation(Indexed.class);
        assertNotNull(ttlIndexed, "@Indexed must be on cachedAt for native TTL expiry");
        assertEquals("300s", ttlIndexed.expireAfter(), "TTL must be set to 300s (5 minutes)");
    }

    @Test
    void gettersSettersAndEquals() {
        LocalDateTime now = LocalDateTime.now();

        CachedConfig config1 = new CachedConfig("app:prod", "cipher", "iv", now);
        assertEquals("app:prod", config1.getCacheKey());
        assertEquals("cipher", config1.getEncryptedCiphertext());
        assertTrue(config1.isEncryptedAtRest());
        assertEquals(now, config1.getCachedAt());

        CachedConfig config2 = new CachedConfig();
        config2.setCacheKey("app:prod");
        config2.setEncryptedCiphertext("cipher");
        config2.setIv("iv");
        config2.setCachedAt(now);

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertTrue(config1.toString().contains("app:prod"));
    }

    @Test
    void model_hasNoPlaintextPayloadField() {
        for (Field field : CachedConfig.class.getDeclaredFields()) {
            assertNotEquals(org.bson.Document.class, field.getType(),
                    "CachedConfig must not carry a plaintext payload field: " + field.getName());
        }
    }
}
