package com.vault.configmanager.service;

import com.vault.configmanager.model.CachedConfig;
import com.vault.configmanager.repository.CachedConfigRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheServiceTest {

    private CacheService cacheService;
    private CachedConfigRepository cachedConfigRepository;

    @BeforeEach
    void setUp() {
        cachedConfigRepository = mock(CachedConfigRepository.class);
        cacheService = new CacheService(cachedConfigRepository);
    }

    @Test
    void buildCacheKey_producesCompositeKey() {
        assertEquals("payment-service:production", CacheService.buildCacheKey("payment-service", "production"));
    }

    @Test
    void get_whenEntryHasNoCiphertext_treatsAsCacheMiss() {
        CachedConfig config = new CachedConfig();
        config.setCacheKey("app:prod");
        config.setCachedAt(LocalDateTime.now());

        when(cachedConfigRepository.findById("app:prod")).thenReturn(Optional.of(config));

        assertTrue(cacheService.get("app", "prod").isEmpty());
    }

    @Test
    void put_neverPersistsPlaintextPayload() throws IllegalAccessException {
        cacheService.put("auth-svc", "prod", new Document("secure_settings",
                new Document("jwt_secret", "super-secret-plaintext-value")));

        ArgumentCaptor<CachedConfig> captor = ArgumentCaptor.forClass(CachedConfig.class);
        verify(cachedConfigRepository).save(captor.capture());

        CachedConfig saved = captor.getValue();
        for (java.lang.reflect.Field field : CachedConfig.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(saved);
            assertFalse(value != null && value.toString().contains("super-secret-plaintext-value"),
                    "Field '" + field.getName() + "' must not hold the plaintext secret");
        }
    }

    @Test
    void get_whenAbsent_returnsEmpty() {
        when(cachedConfigRepository.findById("app:dev")).thenReturn(Optional.empty());

        Optional<Document> result = cacheService.get("app", "dev");
        assertTrue(result.isEmpty());
    }

    @Test
    void put_savesEncryptedAtRestConfigWithCompositeKeyAndCurrentTimestamp() {
        Document payload = new Document("port", 9090);
        cacheService.put("order-svc", "staging", payload);

        ArgumentCaptor<CachedConfig> captor = ArgumentCaptor.forClass(CachedConfig.class);
        verify(cachedConfigRepository).save(captor.capture());

        CachedConfig saved = captor.getValue();
        assertEquals("order-svc:staging", saved.getCacheKey());
        assertTrue(saved.isEncryptedAtRest(), "Cache payload must be encrypted at rest in MongoDB collection");
        assertNotNull(saved.getEncryptedCiphertext());
        assertNotNull(saved.getIv());
        assertNotNull(saved.getCachedAt());
    }

    @Test
    void putAndGet_roundTripWithEphemeralKeyRestoresDocument() {
        Document original = new Document("redis_host", "cluster.internal")
                .append("secret_token", "ephemeral-xyz-123");

        ArgumentCaptor<CachedConfig> captor = ArgumentCaptor.forClass(CachedConfig.class);
        cacheService.put("auth-svc", "prod", original);
        verify(cachedConfigRepository).save(captor.capture());

        CachedConfig saved = captor.getValue();
        when(cachedConfigRepository.findById("auth-svc:prod")).thenReturn(Optional.of(saved));

        Optional<Document> retrieved = cacheService.get("auth-svc", "prod");
        assertTrue(retrieved.isPresent());
        assertEquals("cluster.internal", retrieved.get().getString("redis_host"));
        assertEquals("ephemeral-xyz-123", retrieved.get().getString("secret_token"));
    }

    @Test
    void evict_deletesKeyById() {
        cacheService.evict("billing", "test");
        verify(cachedConfigRepository).deleteById("billing:test");
    }

    @Test
    void count_returnsRepositoryCount() {
        when(cachedConfigRepository.count()).thenReturn(15L);
        assertEquals(15L, cacheService.count());
    }
}
