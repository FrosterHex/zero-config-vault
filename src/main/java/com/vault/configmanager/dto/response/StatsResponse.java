package com.vault.configmanager.dto.response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for system-wide statistics and dashboard data.
 * Populated via MongoDB aggregation pipeline queries.
 */
public class StatsResponse {

    private long totalApplications;
    private long totalEnvironments;
    private long totalVersions;
    private double averageVersionsPerApp;
    private String mostRecentApp;
    private String mostRecentEnv;
    private long totalAuditEntries;
    private long cacheDocumentCount;
    private List<String> activeEnvironments;
    private Map<String, Long> versionCounts;
    private Map<String, Object> storageSizeDistributions;

    public StatsResponse() {
        this.activeEnvironments = new ArrayList<>();
        this.versionCounts = new HashMap<>();
        this.storageSizeDistributions = new HashMap<>();
    }

    public StatsResponse(long totalApplications, long totalEnvironments, long totalVersions,
                         double averageVersionsPerApp, String mostRecentApp, String mostRecentEnv,
                         long totalAuditEntries, long cacheDocumentCount,
                         List<String> activeEnvironments, Map<String, Long> versionCounts,
                         Map<String, Object> storageSizeDistributions) {
        this.totalApplications = totalApplications;
        this.totalEnvironments = totalEnvironments;
        this.totalVersions = totalVersions;
        this.averageVersionsPerApp = averageVersionsPerApp;
        this.mostRecentApp = mostRecentApp;
        this.mostRecentEnv = mostRecentEnv;
        this.totalAuditEntries = totalAuditEntries;
        this.cacheDocumentCount = cacheDocumentCount;
        this.activeEnvironments = activeEnvironments != null ? activeEnvironments : new ArrayList<>();
        this.versionCounts = versionCounts != null ? versionCounts : new HashMap<>();
        this.storageSizeDistributions = storageSizeDistributions != null ? storageSizeDistributions : new HashMap<>();
    }

    public long getTotalApplications() {
        return totalApplications;
    }

    public void setTotalApplications(long totalApplications) {
        this.totalApplications = totalApplications;
    }

    public long getTotalEnvironments() {
        return totalEnvironments;
    }

    public void setTotalEnvironments(long totalEnvironments) {
        this.totalEnvironments = totalEnvironments;
    }

    public long getTotalVersions() {
        return totalVersions;
    }

    public void setTotalVersions(long totalVersions) {
        this.totalVersions = totalVersions;
    }

    public double getAverageVersionsPerApp() {
        return averageVersionsPerApp;
    }

    public void setAverageVersionsPerApp(double averageVersionsPerApp) {
        this.averageVersionsPerApp = averageVersionsPerApp;
    }

    public String getMostRecentApp() {
        return mostRecentApp;
    }

    public void setMostRecentApp(String mostRecentApp) {
        this.mostRecentApp = mostRecentApp;
    }

    public String getMostRecentEnv() {
        return mostRecentEnv;
    }

    public void setMostRecentEnv(String mostRecentEnv) {
        this.mostRecentEnv = mostRecentEnv;
    }

    public long getTotalAuditEntries() {
        return totalAuditEntries;
    }

    public void setTotalAuditEntries(long totalAuditEntries) {
        this.totalAuditEntries = totalAuditEntries;
    }

    public long getCacheDocumentCount() {
        return cacheDocumentCount;
    }

    public void setCacheDocumentCount(long cacheDocumentCount) {
        this.cacheDocumentCount = cacheDocumentCount;
    }

    public List<String> getActiveEnvironments() {
        return activeEnvironments;
    }

    public void setActiveEnvironments(List<String> activeEnvironments) {
        this.activeEnvironments = activeEnvironments;
    }

    public Map<String, Long> getVersionCounts() {
        return versionCounts;
    }

    public void setVersionCounts(Map<String, Long> versionCounts) {
        this.versionCounts = versionCounts;
    }

    public Map<String, Object> getStorageSizeDistributions() {
        return storageSizeDistributions;
    }

    public void setStorageSizeDistributions(Map<String, Object> storageSizeDistributions) {
        this.storageSizeDistributions = storageSizeDistributions;
    }
}
