package com.vault.configmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Request DTO for creating a new Scoped API Key.
 */
public class CreateApiKeyRequest {

    @NotBlank(message = "Key name is required")
    private String name;

    private String prefix;

    @NotEmpty(message = "At least one allowed application is required (use '*' for all)")
    private List<String> allowedApps = new ArrayList<>();

    @NotEmpty(message = "At least one allowed environment is required (use '*' for all)")
    private List<String> allowedEnvironments = new ArrayList<>();

    @NotEmpty(message = "At least one role or permission is required (READ, WRITE, ROLLBACK, EXPORT, ADMIN)")
    private Set<String> roles = new HashSet<>();

    private Integer expiresInDays;

    public CreateApiKeyRequest() {
    }

    public CreateApiKeyRequest(String name, String prefix, List<String> allowedApps,
                               List<String> allowedEnvironments, Set<String> roles,
                               Integer expiresInDays) {
        this.name = name;
        this.prefix = prefix;
        this.allowedApps = allowedApps != null ? allowedApps : new ArrayList<>();
        this.allowedEnvironments = allowedEnvironments != null ? allowedEnvironments : new ArrayList<>();
        this.roles = roles != null ? roles : new HashSet<>();
        this.expiresInDays = expiresInDays;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
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

    public Integer getExpiresInDays() {
        return expiresInDays;
    }

    public void setExpiresInDays(Integer expiresInDays) {
        this.expiresInDays = expiresInDays;
    }
}
