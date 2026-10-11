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
import static org.mockito.ArgumentMatchers.any;
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

        ApiKeyResponse response = apiKeyService.createApiKey(request);

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
}
