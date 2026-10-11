package com.vault.configmanager.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for config diff operations.
 * Shows structural differences between two configuration versions.
 */
public class DiffResponse {

    private int versionFrom;
    private int versionTo;
    private Map<String, Object> added;
    private Map<String, Object> removed;
    private Map<String, Map<String, Object>> modified;
    private int unchangedCount;

    public DiffResponse() {}

    public DiffResponse(int versionFrom, int versionTo,
                        Map<String, Object> added, Map<String, Object> removed,
                        Map<String, Map<String, Object>> modified, int unchangedCount) {
        this.versionFrom = versionFrom;
        this.versionTo = versionTo;
        this.added = added;
        this.removed = removed;
        this.modified = modified;
        this.unchangedCount = unchangedCount;
    }

    public int getVersionFrom() { return versionFrom; }
    public void setVersionFrom(int versionFrom) { this.versionFrom = versionFrom; }
    public int getVersionTo() { return versionTo; }
    public void setVersionTo(int versionTo) { this.versionTo = versionTo; }
    public Map<String, Object> getAdded() { return added; }
    public void setAdded(Map<String, Object> added) { this.added = added; }
    public Map<String, Object> getRemoved() { return removed; }
    public void setRemoved(Map<String, Object> removed) { this.removed = removed; }
    public Map<String, Map<String, Object>> getModified() { return modified; }
    public void setModified(Map<String, Map<String, Object>> modified) { this.modified = modified; }
    public int getUnchangedCount() { return unchangedCount; }
    public void setUnchangedCount(int unchangedCount) { this.unchangedCount = unchangedCount; }
}
