package com.vault.configmanager.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an audit log entry stored in the 'audit_logs' capped collection.
 * Capped collections have a fixed size and auto-evict the oldest documents.
 * Delete operations are not supported on capped collections.
 */
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    private String action;

    private String applicationName;

    private String environment;

    private String requestingIp;

    private String apiKey;

    private boolean success;

    private String failureReason;

    private LocalDateTime timestamp;

    public AuditLog() {
    }

    public AuditLog(String action, String applicationName, String environment,
                    String requestingIp, String apiKey, boolean success,
                    String failureReason, LocalDateTime timestamp) {
        this.action = action;
        this.applicationName = applicationName;
        this.environment = environment;
        this.requestingIp = requestingIp;
        this.apiKey = apiKey;
        this.success = success;
        this.failureReason = failureReason;
        this.timestamp = timestamp;
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getRequestingIp() {
        return requestingIp;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // --- Setters ---

    public void setId(String id) {
        this.id = id;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setRequestingIp(String requestingIp) {
        this.requestingIp = requestingIp;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // --- equals, hashCode, toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(id, auditLog.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditLog{" +
                "id='" + id + '\'' +
                ", action='" + action + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                ", success=" + success +
                ", timestamp=" + timestamp +
                '}';
    }
}
