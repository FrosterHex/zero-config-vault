package com.vault.configmanager.service;

import com.vault.configmanager.dto.request.CreateApiKeyRequest;
import com.vault.configmanager.dto.response.ApiKeyResponse;
import com.vault.configmanager.exception.ScopeViolationException;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.model.ApiKeyRole;
import com.vault.configmanager.repository.ApiKeyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
     * Well-known development keys that are published in the repository. They are only
     * seeded in dev mode, and are deactivated on startup outside dev mode.
     */
    static final List<String> WELL_KNOWN_DEV_KEYS = List.of("sk-dev-key-001", "sk-dev-key-002", "sk-admin-key-001");

    @Value("${vault.dev-mode:false}")
    private boolean devMode;

    @Value("${vault.bootstrap.admin-key:}")
    private String bootstrapAdminKey;

    /**
     * Seeds bootstrap keys if the collection is empty. In dev mode the well-known development
     * keys are seeded; otherwise only the admin key supplied via VAULT_BOOTSTRAP_ADMIN_KEY is.
     * Outside dev mode, any well-known development keys already present are deactivated.
     */
    @PostConstruct
    public void seedDefaultKeys() {
        try {
            if (devMode) {
                if (apiKeyRepository.count() == 0) {
                    log.info("Dev mode: seeding well-known development API keys...");
                    seedKey("sk-dev-key-001", "Default Dev Master Key", "sk-dev-", List.of("*"), List.of("*"), Set.of(ApiKeyRole.ADMIN.name()));
                    seedKey("sk-dev-key-002", "Default Dev Read-Only Key", "sk-read-", List.of("*"), List.of("development", "staging"), Set.of(ApiKeyRole.READ.name()));
                    seedKey("sk-admin-key-001", "Default Admin Platform Key", "sk-admin-", List.of("*"), List.of("*"), Set.of(ApiKeyRole.ADMIN.name()));
                }
                return;
            }

            deactivateWellKnownDevKeys();

            if (bootstrapAdminKey != null && !bootstrapAdminKey.isBlank()
                    && apiKeyRepository.findByHashedSecret(sha256Hex(bootstrapAdminKey.trim())).isEmpty()) {
                if (WELL_KNOWN_DEV_KEYS.contains(bootstrapAdminKey.trim())) {
                    log.error("VAULT_BOOTSTRAP_ADMIN_KEY is a well-known development key and was ignored.");
                } else {
                    log.info("Seeding bootstrap admin API key from VAULT_BOOTSTRAP_ADMIN_KEY.");
                    seedKey(bootstrapAdminKey.trim(), "Bootstrap Admin Key", "sk-boot-", List.of("*"), List.of("*"), Set.of(ApiKeyRole.ADMIN.name()));
                }
            }
        } catch (Exception e) {
            log.warn("Could not seed bootstrap API keys into MongoDB: {}", e.getMessage());
        }
    }

    private void deactivateWellKnownDevKeys() {
        for (String rawKey : WELL_KNOWN_DEV_KEYS) {
            apiKeyRepository.findByHashedSecret(sha256Hex(rawKey)).ifPresent(key -> {
                if (key.isActive()) {
                    key.setActive(false);
                    apiKeyRepository.save(key);
                    log.warn("Deactivated well-known development API key '{}' (not running in dev mode).", key.getName());
                }
            });
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
    public ApiKeyResponse createApiKey(CreateApiKeyRequest request, ApiKey creator) {
        requireCanGrant(creator, request);

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
     * Throws ScopeViolationException unless the key holds the role for the given app and environment.
     * Used where the target app/env is only known from the request body.
     */
    public void requireScope(ApiKey apiKey, String requiredRole, String app, String env) {
        if (app == null || env == null || !authorize(apiKey, requiredRole, app, env)) {
            throw new ScopeViolationException(requiredRole, app, env);
        }
    }

    /**
     * Prevents privilege escalation: a key may only mint keys whose roles, apps and
     * environments it already holds itself.
     */
    private void requireCanGrant(ApiKey creator, CreateApiKeyRequest request) {
        if (creator == null) {
            throw new ScopeViolationException("Key creation requires an authenticated ADMIN key.");
        }
        if (!isSubset(request.getAllowedApps(), creator.getAllowedApps())
                || !isSubset(request.getAllowedEnvironments(), creator.getAllowedEnvironments())) {
            throw new ScopeViolationException("Cannot create a key with broader app or environment scope than the creating key.");
        }
        if (request.getRoles() != null) {
            for (String role : request.getRoles()) {
                if (!creator.hasPermission(role)) {
                    throw new ScopeViolationException("Cannot grant role '" + role + "' that the creating key does not hold.");
                }
            }
        }
    }

    private static boolean isSubset(List<String> requested, List<String> held) {
        if (held != null && held.contains("*")) {
            return true;
        }
        if (requested == null || requested.isEmpty()) {
            return true; // an empty scope grants nothing
        }
        if (requested.contains("*")) {
            return false;
        }
        return held != null && held.containsAll(requested);
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
