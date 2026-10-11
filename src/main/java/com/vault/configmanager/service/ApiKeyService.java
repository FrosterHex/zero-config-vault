package com.vault.configmanager.service;

import com.vault.configmanager.dto.request.CreateApiKeyRequest;
import com.vault.configmanager.dto.response.ApiKeyResponse;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.model.ApiKeyRole;
import com.vault.configmanager.repository.ApiKeyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service managing scoped API keys, cryptographic token hashing,
 * RBAC authorization checks, and bootstrap credential seeding.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Seeds initial bootstrap development and admin keys if the collection is empty.
     */
    @PostConstruct
    public void seedDefaultKeys() {
        try {
            if (apiKeyRepository.count() == 0) {
                log.info("No API keys found in 'api_keys' collection. Seeding initial bootstrap keys...");
                seedKey("sk-dev-key-001", "Default Dev Master Key", "sk-dev-", List.of("*"), List.of("*"), Set.of(ApiKeyRole.ADMIN.name()));
                seedKey("sk-dev-key-002", "Default Dev Read-Only Key", "sk-read-", List.of("*"), List.of("dev", "staging"), Set.of(ApiKeyRole.READ.name()));
                seedKey("sk-admin-key-001", "Default Admin Platform Key", "sk-admin-", List.of("*"), List.of("*"), Set.of(ApiKeyRole.ADMIN.name()));
                log.info("Default bootstrap API keys successfully initialized in MongoDB.");
            }
        } catch (Exception e) {
            log.warn("Could not seed default API keys into MongoDB: {}. Continuing with fallback configuration.", e.getMessage());
        }
    }

    private void seedKey(String rawKey, String name, String prefix, List<String> apps, List<String> envs, Set<String> roles) {
        String hash = sha256Hex(rawKey);
        ApiKey key = new ApiKey(name, prefix, hash, apps, envs, roles, null);
        apiKeyRepository.save(key);
    }

    /**
     * Creates a new scoped API key with high-entropy cryptographic token and SHA-256 hash.
     * The raw plaintext key is returned ONLY in this response and never persisted.
     */
    public ApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        String prefix = (request.getPrefix() != null && !request.getPrefix().isBlank())
                ? request.getPrefix()
                : "sk_live_";

        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        String token = sb.toString();
        String rawKey = prefix + token;
        String hash = sha256Hex(rawKey);

        LocalDateTime expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        ApiKey apiKey = new ApiKey(
                request.getName(),
                prefix,
                hash,
                request.getAllowedApps(),
                request.getAllowedEnvironments(),
                request.getRoles(),
                expiresAt
        );

        ApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyResponse(
                saved.getId(),
                saved.getName(),
                saved.getKeyPrefix(),
                rawKey,
                saved.getAllowedApps(),
                saved.getAllowedEnvironments(),
                saved.getRoles(),
                saved.isActive(),
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                saved.getLastUsedAt()
        );
    }

    /**
     * Authenticates a raw API key token by comparing its SHA-256 digest with the database.
     */
    public Optional<ApiKey> validateAndAuthenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256Hex(rawKey.trim());
        Optional<ApiKey> keyOpt = apiKeyRepository.findByHashedSecret(hash);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            if (key.isActive() && !key.isExpired()) {
                try {
                    key.setLastUsedAt(LocalDateTime.now());
                    apiKeyRepository.save(key);
                } catch (Exception ignored) {
                }
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Evaluates granular authorization permissions for the given key, action role, application, and environment.
     */
    public boolean authorize(ApiKey apiKey, String requiredRole, String app, String env) {
        if (apiKey == null) {
            return false;
        }
        if (!apiKey.hasPermission(requiredRole)) {
            return false;
        }
        if (app != null && !apiKey.isAppAllowed(app)) {
            return false;
        }
        if (env != null && !apiKey.isEnvAllowed(env)) {
            return false;
        }
        return true;
    }

    /**
     * Lists all registered API keys. Raw secrets are omitted.
     */
    public List<ApiKeyResponse> listKeys() {
        return apiKeyRepository.findAll().stream()
                .map(k -> new ApiKeyResponse(
                        k.getId(),
                        k.getName(),
                        k.getKeyPrefix(),
                        null,
                        k.getAllowedApps(),
                        k.getAllowedEnvironments(),
                        k.getRoles(),
                        k.isActive(),
                        k.getCreatedAt(),
                        k.getExpiresAt(),
                        k.getLastUsedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Revokes an API key by setting active = false.
     */
    public boolean revokeKey(String id) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(id);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            key.setActive(false);
            apiKeyRepository.save(key);
            return true;
        }
        return false;
    }

    /**
     * Deletes an API key document completely.
     */
    public boolean deleteKey(String id) {
        if (apiKeyRepository.existsById(id)) {
            apiKeyRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Computes the SHA-256 hexadecimal digest of an input string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in runtime environment", e);
        }
    }
}
