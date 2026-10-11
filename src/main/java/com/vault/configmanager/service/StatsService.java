package com.vault.configmanager.service;

import com.vault.configmanager.dto.response.StatsResponse;
import com.vault.configmanager.repository.AuditLogRepository;
import com.vault.configmanager.repository.CachedConfigRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that collects system-wide telemetry and dashboard metrics
 * utilizing MongoDB Aggregation Pipelines on the 'configurations' collection.
 */
@Service
public class StatsService {

    private static final String CONFIGURATIONS_COLLECTION = "configurations";

    private final MongoTemplate mongoTemplate;
    private final AuditLogRepository auditLogRepository;
    private final CachedConfigRepository cachedConfigRepository;

    public StatsService(MongoTemplate mongoTemplate,
                        AuditLogRepository auditLogRepository,
                        CachedConfigRepository cachedConfigRepository) {
        this.mongoTemplate = mongoTemplate;
        this.auditLogRepository = auditLogRepository;
        this.cachedConfigRepository = cachedConfigRepository;
    }

    /**
     * Executes the MongoDB Aggregation Pipeline and returns aggregated system telemetry.
     */
    public StatsResponse getSystemStats() {
        StatsResponse stats = new StatsResponse();

        // 1. Audit log and cache metrics
        try {
            stats.setTotalAuditEntries(auditLogRepository.count());
        } catch (Exception e) {
            stats.setTotalAuditEntries(0L);
        }

        try {
            stats.setCacheDocumentCount(cachedConfigRepository.count());
        } catch (Exception e) {
            stats.setCacheDocumentCount(0L);
        }

        // 2. Execute Aggregation Pipeline on 'configurations' collection
            try {
                executeTelemetryAggregation(stats);
            } catch (Exception e) {
                // Safe fallback ensuring endpoint never fails if collection is fresh/empty
                populateDefaultsIfEmpty(stats);
            }

        return stats;
    }

    private void executeTelemetryAggregation(StatsResponse stats) {
        // Multi-faceted aggregation pipeline
        Document facetDoc = new Document("$facet", new Document()
                .append("envStats", List.of(
                        new Document("$group", new Document("_id", "$environment").append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1))
                ))
                .append("appEnvStats", List.of(
                        new Document("$group", new Document("_id", new Document("app", "$applicationName").append("env", "$environment"))
                                .append("versions", new Document("$sum", 1))
                                .append("latestVersion", new Document("$max", "$version"))
                                .append("latestCreatedAt", new Document("$max", "$createdAt"))),
                        new Document("$sort", new Document("latestCreatedAt", -1))
                ))
                .append("storageStats", List.of(
                        new Document("$project", new Document("applicationName", 1)
                                .append("environment", 1)
                                .append("sizeBytes", new Document("$bsonSize", "$$ROOT"))),
                        new Document("$group", new Document("_id", "$applicationName")
                                .append("totalBytes", new Document("$sum", "$sizeBytes"))
                                .append("avgBytes", new Document("$avg", "$sizeBytes"))
                                .append("maxBytes", new Document("$max", "$sizeBytes"))
                                .append("minBytes", new Document("$min", "$sizeBytes"))
                                .append("documentCount", new Document("$sum", 1)))
                ))
                .append("systemTotals", List.of(
                        new Document("$group", new Document("_id", null)
                                .append("totalDocuments", new Document("$sum", 1))
                                .append("totalBytes", new Document("$sum", new Document("$bsonSize", "$$ROOT")))
                                .append("avgBytes", new Document("$avg", new Document("$bsonSize", "$$ROOT"))))
                ))
        );

        AggregationOperation facetOp = context -> facetDoc;
        Aggregation aggregation = Aggregation.newAggregation(facetOp);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, CONFIGURATIONS_COLLECTION, Document.class);

        Document resultDoc = results.getUniqueMappedResult();
        if (resultDoc == null) {
            populateDefaultsIfEmpty(stats);
            return;
        }

        // Parse envStats -> activeEnvironments
        @SuppressWarnings("unchecked")
        List<Document> envStats = (List<Document>) resultDoc.get("envStats");
        List<String> environments = new ArrayList<>();
        if (envStats != null) {
            for (Document d : envStats) {
                Object idObj = d.get("_id");
                if (idObj != null) {
                    environments.add(idObj.toString());
                }
            }
        }
        stats.setActiveEnvironments(environments);
        stats.setTotalEnvironments(environments.size());

        // Parse appEnvStats -> versionCounts, totalApplications, mostRecent
        @SuppressWarnings("unchecked")
        List<Document> appEnvStats = (List<Document>) resultDoc.get("appEnvStats");
        Map<String, Long> versionCounts = new HashMap<>();
        Set<String> uniqueApps = new HashSet<>();
        long totalVersions = 0;

        if (appEnvStats != null && !appEnvStats.isEmpty()) {
            // First item is most recent due to sort on latestCreatedAt desc
            Document mostRecent = appEnvStats.get(0);
            Document mostRecentId = (Document) mostRecent.get("_id");
            if (mostRecentId != null) {
                stats.setMostRecentApp(mostRecentId.getString("app"));
                stats.setMostRecentEnv(mostRecentId.getString("env"));
            }

            for (Document d : appEnvStats) {
                Document idObj = (Document) d.get("_id");
                Number versionsNum = (Number) d.get("versions");
                long versions = versionsNum != null ? versionsNum.longValue() : 0L;
                totalVersions += versions;

                if (idObj != null) {
                    String app = idObj.getString("app");
                    String env = idObj.getString("env");
                    if (app != null) {
                        uniqueApps.add(app);
                        String key = env != null ? app + ":" + env : app;
                        versionCounts.put(key, versions);
                    }
                }
            }
        }

        stats.setTotalApplications(uniqueApps.size());
        stats.setTotalVersions(totalVersions);
        stats.setVersionCounts(versionCounts);
        stats.setAverageVersionsPerApp(
                !uniqueApps.isEmpty() ? (double) totalVersions / uniqueApps.size() : 0.0);

        // Parse storageStats & systemTotals -> storageSizeDistributions
        Map<String, Object> storageDistributions = new HashMap<>();

        @SuppressWarnings("unchecked")
        List<Document> storageStats = (List<Document>) resultDoc.get("storageStats");
        Map<String, Object> perAppStorage = new HashMap<>();
        if (storageStats != null) {
            for (Document d : storageStats) {
                String appName = d.getString("_id");
                if (appName != null) {
                    Map<String, Object> appStatsMap = new HashMap<>();
                    appStatsMap.put("totalBytes", d.get("totalBytes"));
                    appStatsMap.put("avgBytes", d.get("avgBytes"));
                    appStatsMap.put("maxBytes", d.get("maxBytes"));
                    appStatsMap.put("minBytes", d.get("minBytes"));
                    appStatsMap.put("documentCount", d.get("documentCount"));
                    perAppStorage.put(appName, appStatsMap);
                }
            }
        }
        storageDistributions.put("byApplication", perAppStorage);

        @SuppressWarnings("unchecked")
        List<Document> systemTotals = (List<Document>) resultDoc.get("systemTotals");
        if (systemTotals != null && !systemTotals.isEmpty()) {
            Document totalDoc = systemTotals.get(0);
            storageDistributions.put("totalStorageBytes", totalDoc.get("totalBytes"));
            storageDistributions.put("averageDocumentSizeBytes", totalDoc.get("avgBytes"));
            storageDistributions.put("totalDocuments", totalDoc.get("totalDocuments"));
        } else {
            storageDistributions.put("totalStorageBytes", 0L);
            storageDistributions.put("averageDocumentSizeBytes", 0.0);
            storageDistributions.put("totalDocuments", 0L);
        }

        stats.setStorageSizeDistributions(storageDistributions);
    }

    private void populateDefaultsIfEmpty(StatsResponse stats) {
        if (stats.getActiveEnvironments() == null) {
            stats.setActiveEnvironments(new ArrayList<>());
        }
        if (stats.getVersionCounts() == null) {
            stats.setVersionCounts(new HashMap<>());
        }
        if (stats.getStorageSizeDistributions() == null) {
            Map<String, Object> defaults = new HashMap<>();
            defaults.put("byApplication", new HashMap<>());
            defaults.put("totalStorageBytes", 0L);
            defaults.put("averageDocumentSizeBytes", 0.0);
            defaults.put("totalDocuments", 0L);
            stats.setStorageSizeDistributions(defaults);
        }
    }
}
