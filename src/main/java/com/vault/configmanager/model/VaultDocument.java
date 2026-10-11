package com.vault.configmanager.model;

import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a versioned configuration document stored in MongoDB.
 * Maps to the 'configurations' collection.
 *
 * Temporal versioning is enforced: documents are never updated in place.
 * Every change produces a new document with an incremented version number.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "configurations")
@CompoundIndex(name = "app_env_ver_unique_idx", def = "{'applicationName': 1, 'environment': 1, 'version': 1}", unique = true)
public class VaultDocument {

    @Id
    private String id;

    @Indexed
    private String applicationName;

    @Indexed
    private String environment;

    private int version;

    private String documentHash;

    private Document payload;

    private Map<String, String> tags;

    private LocalDateTime createdAt;

    @Indexed(expireAfter = "90d")
    private LocalDateTime expiresAt;

    private String searchableText;

    public VaultDocument() {
        this.tags = new HashMap<>();
        this.payload = new Document();
    }

    public VaultDocument(String applicationName, String environment, int version,
                         String documentHash, Document payload, Map<String, String> tags,
                         LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.version = version;
        this.documentHash = documentHash;
        this.payload = payload != null ? payload : new Document();
        this.tags = tags != null ? tags : new HashMap<>();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public VaultDocument(String applicationName, String environment, int version,
                         String documentHash, Map<String, Object> payload, Map<String, String> tags,
                         LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.version = version;
        this.documentHash = documentHash;
        this.payload = payload != null ? (payload instanceof Document ? (Document) payload : new Document(payload)) : new Document();
        this.tags = tags != null ? tags : new HashMap<>();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public int getVersion() {
        return version;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public Document getPayload() {
        return payload;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    // --- Setters ---

    public void setId(String id) {
        this.id = id;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public void setPayload(Document payload) {
        this.payload = payload;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSearchableText() {
        return searchableText;
    }

    public void setSearchableText(String searchableText) {
        this.searchableText = searchableText;
    }

    // --- equals, hashCode, toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VaultDocument that = (VaultDocument) o;
        return version == that.version &&
                Objects.equals(id, that.id) &&
                Objects.equals(applicationName, that.applicationName) &&
                Objects.equals(environment, that.environment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, applicationName, environment, version);
    }

    @Override
    public String toString() {
        return "VaultDocument{" +
                "id='" + id + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                ", version=" + version +
                ", documentHash='" + documentHash + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
