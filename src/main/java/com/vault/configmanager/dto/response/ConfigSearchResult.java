package com.vault.configmanager.dto.response;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Result item returned by the MongoDB Full-Text Search endpoint.
 */
public class ConfigSearchResult {

    private String applicationName;
    private String environment;
    private int version;
    private String documentHash;
    private LocalDateTime createdAt;
    private float score;
    private Map<String, Object> matchingSnippets;

    public ConfigSearchResult() {
        this.matchingSnippets = new HashMap<>();
    }

    public ConfigSearchResult(String applicationName, String environment, int version,
                              String documentHash, LocalDateTime createdAt, float score,
                              Map<String, Object> matchingSnippets) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.version = version;
        this.documentHash = documentHash;
        this.createdAt = createdAt;
        this.score = score;
        this.matchingSnippets = matchingSnippets != null ? matchingSnippets : new HashMap<>();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public Map<String, Object> getMatchingSnippets() {
        return matchingSnippets;
    }

    public void setMatchingSnippets(Map<String, Object> matchingSnippets) {
        this.matchingSnippets = matchingSnippets;
    }
}
