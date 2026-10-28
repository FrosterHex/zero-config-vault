package com.vault.configmanager.service;

import com.vault.configmanager.dto.request.CreateApiKeyRequest;
import com.vault.configmanager.dto.response.ApiKeyResponse;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.model.ApiKeyRole;
import com.vault.configmanager.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    private ApiKeyRepository apiKeyRepository;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        apiKeyService = new ApiKeyService(apiKeyRepository);
    }

    @Test
    void createApiKey_generatesHighEntropyKeyAndSavesHashedSecret() {
        CreateApiKeyRequest request = new CreateApiKeyRequest(
                "Payment Service Key",
                "sk_live_pay_",
                List.of("payment-svc"),
                List.of("prod"),
                Set.of(ApiKeyRole.WRITE.name()),
                30
        );

        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId("key-id-123");
            return key;
        });

        ApiKey creator = new ApiKey("Admin", "sk-admin-", "hash", List.of("*"), List.of("*"), Set.of("ADMIN"), null);
        ApiKeyResponse response = apiKeyService.createApiKey(request, creator);

        assertNotNull(response);
        assertEquals("key-id-123", response.getId());
        assertEquals("Payment Service Key", response.getName());
        assertEquals("sk_live_pay_", response.getKeyPrefix());
        assertNotNull(response.getRawKey());
        assertTrue(response.getRawKey().startsWith("sk_live_pay_"));
        assertEquals(List.of("payment-svc"), response.getAllowedApps());
        assertEquals(List.of("prod"), response.getAllowedEnvironments());
        assertTrue(response.getRoles().contains(ApiKeyRole.WRITE.name()));
        assertTrue(response.isActive());
        assertNotNull(response.getExpiresAt());

        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void validateAndAuthenticate_withValidKey_returnsApiKey() {
        String rawKey = "sk_live_1234567890abcdef";
        String hash = ApiKeyService.sha256Hex(rawKey);

        ApiKey key = new ApiKey(
                "Test Key",
                "sk_live_",
                hash,
                List.of("*"),
                List.of("*"),
                Set.of("ADMIN"),
                LocalDateTime.now().plusDays(10)
        );

        when(apiKeyRepository.findByHashedSecret(hash)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(key);

        Optional<ApiKey> result = apiKeyService.validateAndAuthenticate(rawKey);

        assertTrue(result.isPresent());
        assertEquals("Test Key", result.get().getName());
        assertNotNull(result.get().getLastUsedAt());
        verify(apiKeyRepository).save(key);
    }

    @Test
    void validateAndAuthenticate_withExpiredKey_returnsEmpty() {
        String rawKey = "sk_live_expired123456";
        String hash = ApiKeyService.sha256Hex(rawKey);

        ApiKey key = new ApiKey(
                "Expired Key",
                "sk_live_",
                hash,
                List.of("*"),
                List.of("*"),
                Set.of("READ"),
                LocalDateTime.now().minusHours(1)
        );

        when(apiKeyRepository.findByHashedSecret(hash)).thenReturn(Optional.of(key));

        Optional<ApiKey> result = apiKeyService.validateAndAuthenticate(rawKey);

        assertTrue(result.isEmpty());
    }

    @Test
    void validateAndAuthenticate_withRevokedKey_returnsEmpty() {
        String rawKey = "sk_live_revoked123456";
        String hash = ApiKeyService.sha256Hex(rawKey);

        ApiKey key = new ApiKey(
                "Revoked Key",
                "sk_live_",
                hash,
                List.of("*"),
                List.of("*"),
                Set.of("READ"),
                null
        );
        key.setActive(false);

        when(apiKeyRepository.findByHashedSecret(hash)).thenReturn(Optional.of(key));

        Optional<ApiKey> result = apiKeyService.validateAndAuthenticate(rawKey);

        assertTrue(result.isEmpty());
    }

    @Test
    void authorize_checksRoleAppAndEnvironmentAccurately() {
        ApiKey key = new ApiKey(
                "Scoped Key",
                "sk_live_",
                "hash",
                List.of("auth-svc"),
                List.of("dev", "staging"),
                Set.of("READ", "WRITE"),
                null
        );

        // Allowed role, app, and env
        assertTrue(apiKeyService.authorize(key, "READ", "auth-svc", "dev"));
        assertTrue(apiKeyService.authorize(key, "WRITE", "auth-svc", "staging"));

        // Disallowed role
        assertFalse(apiKeyService.authorize(key, "ADMIN", "auth-svc", "dev"));
        assertFalse(apiKeyService.authorize(key, "ROLLBACK", "auth-svc", "dev"));

        // Disallowed app
        assertFalse(apiKeyService.authorize(key, "READ", "payment-svc", "dev"));

        // Disallowed env
        assertFalse(apiKeyService.authorize(key, "READ", "auth-svc", "prod"));
    }

    @Test
    void authorize_adminRole_satisfiesAnyAction() {
        ApiKey adminKey = new ApiKey(
                "Admin Master Key",
                "sk-admin-",
                "hash",
                List.of("*"),
                List.of("*"),
                Set.of("ADMIN"),
                null
        );

        assertTrue(apiKeyService.authorize(adminKey, "READ", "any-app", "prod"));
        assertTrue(apiKeyService.authorize(adminKey, "WRITE", "any-app", "prod"));
        assertTrue(apiKeyService.authorize(adminKey, "ROLLBACK", "any-app", "prod"));
        assertTrue(apiKeyService.authorize(adminKey, "EXPORT", "any-app", "prod"));
    }

    @Test
    void revokeKey_setsActiveToFalse() {
        ApiKey key = new ApiKey("Target", "sk_", "hash", List.of("*"), List.of("*"), Set.of("READ"), null);
        key.setId("key-target");

        when(apiKeyRepository.findById("key-target")).thenReturn(Optional.of(key));

        boolean revoked = apiKeyService.revokeKey("key-target");

        assertTrue(revoked);
        assertFalse(key.isActive());
        verify(apiKeyRepository).save(key);
    }

    @Test
    void createApiKey_withBroaderScopeThanCreator_isRejected() {
        ApiKey scopedAdmin = new ApiKey("Scoped Admin", "sk-", "hash", List.of("payment-svc"), List.of("*"), Set.of("ADMIN"), null);
        CreateApiKeyRequest wildcard = new CreateApiKeyRequest("Escalated", "sk_", List.of("*"), List.of("prod"), Set.of("WRITE"), null);
        CreateApiKeyRequest otherApp = new CreateApiKeyRequest("Other", "sk_", List.of("billing-svc"), List.of("prod"), Set.of("WRITE"), null);

        assertThrows(com.vault.configmanager.exception.ScopeViolationException.class,
                () -> apiKeyService.createApiKey(wildcard, scopedAdmin));
        assertThrows(com.vault.configmanager.exception.ScopeViolationException.class,
                () -> apiKeyService.createApiKey(otherApp, scopedAdmin));
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    void createApiKey_withRoleCreatorLacks_isRejected() {
        ApiKey writer = new ApiKey("Writer", "sk-", "hash", List.of("*"), List.of("*"), Set.of("WRITE"), null);
        CreateApiKeyRequest adminRequest = new CreateApiKeyRequest("Admin", "sk_", List.of("a"), List.of("prod"), Set.of("ADMIN"), null);

        assertThrows(com.vault.configmanager.exception.ScopeViolationException.class,
                () -> apiKeyService.createApiKey(adminRequest, writer));
    }

    @Test
    void requireScope_rejectsMissingOrOutOfScopeTargets() {
        ApiKey writer = new ApiKey("Writer", "sk-", "hash", List.of("payment-svc"), List.of("prod"), Set.of("WRITE"), null);

        assertDoesNotThrow(() -> apiKeyService.requireScope(writer, "WRITE", "payment-svc", "prod"));
        assertThrows(com.vault.configmanager.exception.ScopeViolationException.class,
                () -> apiKeyService.requireScope(writer, "WRITE", "billing-svc", "prod"));
        assertThrows(com.vault.configmanager.exception.ScopeViolationException.class,
                () -> apiKeyService.requireScope(writer, "WRITE", null, "prod"));
        assertThrows(com.vault.configmanager.exception.ScopeViolationException.class,
                () -> apiKeyService.requireScope(null, "WRITE", "payment-svc", "prod"));
    }

    @Test
    void seedDefaultKeys_outsideDevMode_deactivatesWellKnownKeysAndSeedsNone() {
        ApiKey published = new ApiKey("Default Dev Master Key", "sk-dev-", ApiKeyService.sha256Hex("sk-dev-key-001"),
                List.of("*"), List.of("*"), Set.of("ADMIN"), null);
        when(apiKeyRepository.findByHashedSecret(anyString())).thenReturn(java.util.Optional.empty());
        when(apiKeyRepository.findByHashedSecret(ApiKeyService.sha256Hex("sk-dev-key-001")))
                .thenReturn(java.util.Optional.of(published));

        apiKeyService.seedDefaultKeys();

        assertFalse(published.isActive());
        verify(apiKeyRepository).save(published);
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    @Test
    void seedDefaultKeys_outsideDevMode_seedsBootstrapAdminKeyFromConfig() {
        org.springframework.test.util.ReflectionTestUtils.setField(apiKeyService, "bootstrapAdminKey", "sk-boot-7f3a9c2e5d1b8a4f");
        when(apiKeyRepository.findByHashedSecret(anyString())).thenReturn(java.util.Optional.empty());

        apiKeyService.seedDefaultKeys();

        org.mockito.ArgumentCaptor<ApiKey> captor = org.mockito.ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        assertEquals(ApiKeyService.sha256Hex("sk-boot-7f3a9c2e5d1b8a4f"), captor.getValue().getHashedSecret());
        assertTrue(captor.getValue().getRoles().contains("ADMIN"));
    }

    @Test
    void seedDefaultKeys_inDevMode_seedsWellKnownKeysIntoEmptyCollection() {
        org.springframework.test.util.ReflectionTestUtils.setField(apiKeyService, "devMode", true);
        when(apiKeyRepository.count()).thenReturn(0L);

        apiKeyService.seedDefaultKeys();

        verify(apiKeyRepository, times(3)).save(any(ApiKey.class));
    }
}
