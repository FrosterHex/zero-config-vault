package com.vault.configmanager.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO returned after configuration CRUD operations.
 */
public class ConfigResponse {

    private String id;
    private String applicationName;
    private String environment;
    private int version;
    private String documentHash;
    private Map<String, Object> payload;
    private Map<String, String> tags;
    private LocalDateTime createdAt;

    public ConfigResponse() {
    }

    public ConfigResponse(String id, String applicationName, String environment,
                          int version, String documentHash, Map<String, Object> payload,
                          Map<String, String> tags, LocalDateTime createdAt) {
        this.id = id;
        this.applicationName = applicationName;
        this.environment = environment;
        this.version = version;
        this.documentHash = documentHash;
        this.payload = payload;
        this.tags = tags;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ConfigResponse{id='" + id + "', app='" + applicationName +
               "', env='" + environment + "', version=" + version + '}';
    }
}
