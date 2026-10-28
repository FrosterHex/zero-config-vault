package com.vault.configmanager.security;

import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.repository.ApiKeyRepository;
import com.vault.configmanager.service.ApiKeyService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helper: a real ApiKeyService (real RBAC and scope checks) over a mocked repository
 * holding the keys registered through {@link #register}.
 */
public final class TestApiKeys {

    private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
    private final ApiKeyService service = new ApiKeyService(repository);

    public TestApiKeys() {
        when(repository.findByHashedSecret(anyString())).thenReturn(Optional.empty());
    }

    public static TestApiKeys withAdmin(String rawKey) {
        return new TestApiKeys().register(rawKey, List.of("*"), List.of("*"), Set.of("ADMIN"));
    }

    public TestApiKeys register(String rawKey, List<String> apps, List<String> envs, Set<String> roles) {
        ApiKey key = new ApiKey("test-" + rawKey, "sk-", ApiKeyService.sha256Hex(rawKey), apps, envs, roles, null);
        when(repository.findByHashedSecret(ApiKeyService.sha256Hex(rawKey))).thenReturn(Optional.of(key));
        return this;
    }

    public ApiKeyService service() {
        return service;
    }

    public ApiKeyRepository repository() {
        return repository;
    }
}
