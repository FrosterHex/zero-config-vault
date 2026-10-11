package com.vault.configmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for creating or updating a configuration entry.
 */
public class ConfigRequest {

    @NotBlank(message = "applicationName is required")
    private String applicationName;

    @NotBlank(message = "environment is required")
    private String environment;

    @NotNull(message = "payload is required")
    private Map<String, Object> payload;

    private Map<String, String> tags;

    public ConfigRequest() {
        this.tags = new HashMap<>();
    }

    public ConfigRequest(String applicationName, String environment,
                         Map<String, Object> payload, Map<String, String> tags) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.payload = payload;
        this.tags = tags != null ? tags : new HashMap<>();
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "ConfigRequest{" +
                "applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                '}';
    }
}
