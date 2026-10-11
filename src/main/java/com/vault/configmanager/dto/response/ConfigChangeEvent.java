package com.vault.configmanager.dto.response;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Event DTO published over Server-Sent Events (SSE) when a configuration document is updated or rolled back.
 */
public class ConfigChangeEvent {

    private String eventType;
    private String applicationName;
    private String environment;
    private int version;
    private String documentHash;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public ConfigChangeEvent() {
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    public ConfigChangeEvent(String eventType, String applicationName, String environment,
                             int version, String documentHash, LocalDateTime timestamp,
                             Map<String, Object> metadata) {
        this.eventType = eventType;
        this.applicationName = applicationName;
        this.environment = environment;
        this.version = version;
        this.documentHash = documentHash;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    @Override
    public String toString() {
        return "ConfigChangeEvent{" +
                "eventType='" + eventType + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                ", version=" + version +
                ", documentHash='" + documentHash + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
