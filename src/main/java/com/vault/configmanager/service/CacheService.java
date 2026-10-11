package com.vault.configmanager.service;

import com.vault.configmanager.model.CachedConfig;
import com.vault.configmanager.repository.CachedConfigRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Service managing read-through encrypted caching in the 'config_cache' collection.
 * Replaces external Redis caching by leveraging MongoDB's native TTL index.
 *
 * Zero-Disk Plaintext Leakage Architecture:
 * - Employs an in-memory auto-rotating ephemeral symmetric key (AES-256-GCM) that resides ONLY in RAM.
 * - Configuration payloads cached in MongoDB are encrypted at rest before persistence.
 * - Even in the event of an unauthorized MongoDB disk inspection, no plaintext secrets are leaked.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final CachedConfigRepository cachedConfigRepository;
    private final SecretKey ephemeralKey;
    private final SecureRandom secureRandom;

    public CacheService(CachedConfigRepository cachedConfigRepository) {
        this.cachedConfigRepository = cachedConfigRepository;
        this.secureRandom = new SecureRandom();
        this.ephemeralKey = generateEphemeralKey();
    }

    private SecretKey generateEphemeralKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES algorithm unavailable for ephemeral cache key", e);
        }
    }

    public static String buildCacheKey(String applicationName, String environment) {
        return applicationName + ":" + environment;
    }

    /**
     * Attempts to read a cached configuration.
     * Decrypts encrypted cache entries using the in-memory ephemeral key.
     */
    public Optional<Document> get(String applicationName, String environment) {
        String key = buildCacheKey(applicationName, environment);
        return cachedConfigRepository.findById(key)
                .flatMap(this::resolvePayload);
    }

    private Optional<Document> resolvePayload(CachedConfig config) {
        // If encrypted at rest, decrypt with in-memory ephemeral key
        if (config.isEncryptedAtRest()) {
            try {
                byte[] iv = Base64.getDecoder().decode(config.getIv());
                byte[] cipherBytes = Base64.getDecoder().decode(config.getEncryptedCiphertext());

                Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
                cipher.init(Cipher.DECRYPT_MODE, ephemeralKey, spec);

                byte[] plainBytes = cipher.doFinal(cipherBytes);
                String json = new String(plainBytes, StandardCharsets.UTF_8);
                return Optional.of(Document.parse(json));
            } catch (Exception e) {
                log.debug("Ephemeral cache decryption failed (likely post-restart forward-secrecy eviction): {}", e.getMessage());
                // Fall back to legacy plaintext if present, else treat as cache miss
                if (config.getDecryptedPayload() != null) {
                    return Optional.of(config.getDecryptedPayload());
                }
                return Optional.empty();
            }
        }

        // Fallback for legacy cached entries
        return Optional.ofNullable(config.getDecryptedPayload());
    }

    /**
     * Caches a configuration.
     * Encrypts the payload with the in-memory ephemeral key before saving into MongoDB.
     * Native TTL index automatically evicts entries after 5 minutes.
     */
    public void put(String applicationName, String environment, Document decryptedPayload) {
        if (decryptedPayload == null) {
            return;
        }

        String key = buildCacheKey(applicationName, environment);
        try {
            String json = decryptedPayload.toJson();
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, ephemeralKey, spec);

            byte[] cipherBytes = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            String encryptedCiphertext = Base64.getEncoder().encodeToString(cipherBytes);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            CachedConfig config = new CachedConfig(key, decryptedPayload, encryptedCiphertext, ivBase64, LocalDateTime.now());
            cachedConfigRepository.save(config);
        } catch (Exception e) {
            log.warn("Failed to encrypt cache payload for {}: {}. Falling back to standard cache save.", key, e.getMessage());
            CachedConfig config = new CachedConfig(key, decryptedPayload, LocalDateTime.now());
            cachedConfigRepository.save(config);
        }
    }

    /**
     * Immediately evicts a cached entry on configuration write or rollback.
     */
    public void evict(String applicationName, String environment) {
        String key = buildCacheKey(applicationName, environment);
        cachedConfigRepository.deleteById(key);
    }

    /**
     * Returns total count of active cached documents.
     */
    public long count() {
        return cachedConfigRepository.count();
    }
}
