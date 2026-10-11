package com.vault.configmanager.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Summary DTO for version listing endpoints.
 * Excludes the full payload for performance.
 */
public class VersionSummary {

    private int version;
    private String documentHash;
    private Map<String, String> tags;
    private LocalDateTime createdAt;

    public VersionSummary() {}

    public VersionSummary(int version, String documentHash,
                          Map<String, String> tags, LocalDateTime createdAt) {
        this.version = version;
        this.documentHash = documentHash;
        this.tags = tags;
        this.createdAt = createdAt;
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "VersionSummary{version=" + version + ", hash='" + documentHash + "'}";
    }
}
