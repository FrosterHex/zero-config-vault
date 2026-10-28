package com.vault.configmanager.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MongoDB document entity representing a Scoped API Key in the 'api_keys' collection.
 * Secrets are never stored in plaintext; only the SHA-256 hash of the secret is retained.
 * Enforces fine-grained application, environment, and role-based permissions.
 */
@Document(collection = "api_keys")
public class ApiKey {

    @Id
    private String id;

    private String name;

    // Display-only label shared by many keys (e.g. "sk_live_"); identity is hashedSecret
    private String keyPrefix;

    @Indexed(unique = true)
    private String hashedSecret;

    private List<String> allowedApps = new ArrayList<>();
    private List<String> allowedEnvironments = new ArrayList<>();
    private Set<String> roles = new HashSet<>();

    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;

    public ApiKey() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public ApiKey(String name, String keyPrefix, String hashedSecret,
                  List<String> allowedApps, List<String> allowedEnvironments,
                  Set<String> roles, LocalDateTime expiresAt) {
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.hashedSecret = hashedSecret;
        this.allowedApps = allowedApps != null ? new ArrayList<>(allowedApps) : new ArrayList<>();
        this.allowedEnvironments = allowedEnvironments != null ? new ArrayList<>(allowedEnvironments) : new ArrayList<>();
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    /**
     * Checks if the key possesses the required permission.
     * ADMIN grant automatically satisfies any action.
     */
    public boolean hasPermission(String requiredRole) {
        if (roles.contains("ADMIN") || roles.contains(ApiKeyRole.ADMIN.name())) {
            return true;
        }
        return roles.contains(requiredRole);
    }

    /**
     * Checks if target application is allowed. Wildcard '*' satisfies all apps.
     */
    public boolean isAppAllowed(String app) {
        if (app == null || allowedApps.contains("*")) {
            return true;
        }
        return allowedApps.contains(app);
    }

    /**
     * Checks if target environment is allowed. Wildcard '*' satisfies all environments.
     */
    public boolean isEnvAllowed(String env) {
        if (env == null || allowedEnvironments.contains("*")) {
            return true;
        }
        return allowedEnvironments.contains(env);
    }

    /**
     * Evaluates expiration timestamp.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    // Explicit Getters and Setters (STRICT NO LOMBOK POLICY)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getHashedSecret() {
        return hashedSecret;
    }

    public void setHashedSecret(String hashedSecret) {
        this.hashedSecret = hashedSecret;
    }

    public List<String> getAllowedApps() {
        return allowedApps;
    }

    public void setAllowedApps(List<String> allowedApps) {
        this.allowedApps = allowedApps != null ? new ArrayList<>(allowedApps) : new ArrayList<>();
    }

    public List<String> getAllowedEnvironments() {
        return allowedEnvironments;
    }

    public void setAllowedEnvironments(List<String> allowedEnvironments) {
        this.allowedEnvironments = allowedEnvironments != null ? new ArrayList<>(allowedEnvironments) : new ArrayList<>();
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    @Override
    public String toString() {
        return "ApiKey{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", keyPrefix='" + keyPrefix + '\'' +
                ", allowedApps=" + allowedApps +
                ", allowedEnvironments=" + allowedEnvironments +
                ", roles=" + roles +
                ", active=" + active +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
