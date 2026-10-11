package com.vault.configmanager.dto.response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Response DTO representing an API Key.
 * Note: rawKey is ONLY returned upon creation; for all subsequent listings/queries it is null.
 */
public class ApiKeyResponse {

    private String id;
    private String name;
    private String keyPrefix;
    private String rawKey;
    private List<String> allowedApps = new ArrayList<>();
    private List<String> allowedEnvironments = new ArrayList<>();
    private Set<String> roles = new HashSet<>();
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;

    public ApiKeyResponse() {
    }

    public ApiKeyResponse(String id, String name, String keyPrefix, String rawKey,
                          List<String> allowedApps, List<String> allowedEnvironments,
                          Set<String> roles, boolean active,
                          LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime lastUsedAt) {
        this.id = id;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.rawKey = rawKey;
        this.allowedApps = allowedApps != null ? allowedApps : new ArrayList<>();
        this.allowedEnvironments = allowedEnvironments != null ? allowedEnvironments : new ArrayList<>();
        this.roles = roles != null ? roles : new HashSet<>();
        this.active = active;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
    }

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

    public String getRawKey() {
        return rawKey;
    }

    public void setRawKey(String rawKey) {
        this.rawKey = rawKey;
    }

    public List<String> getAllowedApps() {
        return allowedApps;
    }

    public void setAllowedApps(List<String> allowedApps) {
        this.allowedApps = allowedApps != null ? allowedApps : new ArrayList<>();
    }

    public List<String> getAllowedEnvironments() {
        return allowedEnvironments;
    }

    public void setAllowedEnvironments(List<String> allowedEnvironments) {
        this.allowedEnvironments = allowedEnvironments != null ? allowedEnvironments : new ArrayList<>();
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles != null ? roles : new HashSet<>();
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
}
