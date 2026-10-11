package com.vault.configmanager.model;

import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a cached configuration stored in the 'config_cache' collection.
 * Uses MongoDB's native TTL index on 'cachedAt' to auto-expire entries after 5 minutes.
 *
 * Zero-Disk Plaintext Leakage Architecture:
 * - Payloads in config_cache are encrypted at rest using an in-memory ephemeral symmetric key.
 * - Sensitive secrets never sit unencrypted on disk even in temporary cache collections.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "config_cache")
public class CachedConfig {

    @Id
    private String cacheKey;

    private Document decryptedPayload;
    private String encryptedCiphertext;
    private String iv;

    @Indexed(expireAfter = "300s")
    private LocalDateTime cachedAt;

    public CachedConfig() {
    }

    public CachedConfig(String cacheKey, Document decryptedPayload, LocalDateTime cachedAt) {
        this.cacheKey = cacheKey;
        this.decryptedPayload = decryptedPayload;
        this.cachedAt = cachedAt;
    }

    public CachedConfig(String cacheKey, String encryptedCiphertext, String iv, LocalDateTime cachedAt) {
        this.cacheKey = cacheKey;
        this.encryptedCiphertext = encryptedCiphertext;
        this.iv = iv;
        this.cachedAt = cachedAt;
    }

    public CachedConfig(String cacheKey, Document decryptedPayload, String encryptedCiphertext, String iv, LocalDateTime cachedAt) {
        this.cacheKey = cacheKey;
        this.decryptedPayload = decryptedPayload;
        this.encryptedCiphertext = encryptedCiphertext;
        this.iv = iv;
        this.cachedAt = cachedAt;
    }

    // --- Getters ---

    public String getCacheKey() {
        return cacheKey;
    }

    public Document getDecryptedPayload() {
        return decryptedPayload;
    }

    public String getEncryptedCiphertext() {
        return encryptedCiphertext;
    }

    public String getIv() {
        return iv;
    }

    public LocalDateTime getCachedAt() {
        return cachedAt;
    }

    // --- Setters ---

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public void setDecryptedPayload(Document decryptedPayload) {
        this.decryptedPayload = decryptedPayload;
    }

    public void setEncryptedCiphertext(String encryptedCiphertext) {
        this.encryptedCiphertext = encryptedCiphertext;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public void setCachedAt(LocalDateTime cachedAt) {
        this.cachedAt = cachedAt;
    }

    public boolean isEncryptedAtRest() {
        return encryptedCiphertext != null && !encryptedCiphertext.isBlank();
    }

    // --- equals, hashCode, toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedConfig that = (CachedConfig) o;
        return Objects.equals(cacheKey, that.cacheKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheKey);
    }

    @Override
    public String toString() {
        return "CachedConfig{" +
                "cacheKey='" + cacheKey + '\'' +
                ", encrypted=" + isEncryptedAtRest() +
                ", cachedAt=" + cachedAt +
                '}';
    }
}
