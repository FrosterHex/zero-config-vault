package com.vault.configmanager.service;

import com.vault.configmanager.dto.response.StatsResponse;
import com.vault.configmanager.repository.AuditLogRepository;
import com.vault.configmanager.repository.CachedConfigRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StatsServiceTest {

    private StatsService statsService;
    private MongoTemplate mongoTemplate;
    private AuditLogRepository auditLogRepository;
    private CachedConfigRepository cachedConfigRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        auditLogRepository = mock(AuditLogRepository.class);
        cachedConfigRepository = mock(CachedConfigRepository.class);

        when(auditLogRepository.count()).thenReturn(142L);
        when(cachedConfigRepository.count()).thenReturn(12L);

        statsService = new StatsService(mongoTemplate, auditLogRepository, cachedConfigRepository);
    }

    @Test
    void testGetSystemStatsSuccessfulAggregation() {
        // Prepare facet aggregation result
        Document env1 = new Document("_id", "production").append("count", 10);
        Document env2 = new Document("_id", "staging").append("count", 5);

        Document appEnv1 = new Document("_id", new Document("app", "auth-service").append("env", "production"))
                .append("versions", 5)
                .append("latestVersion", 5)
                .append("latestCreatedAt", LocalDateTime.now());

        Document appEnv2 = new Document("_id", new Document("app", "order-service").append("env", "production"))
                .append("versions", 3)
                .append("latestVersion", 3)
                .append("latestCreatedAt", LocalDateTime.now().minusDays(1));

        Document storageApp = new Document("_id", "auth-service")
                .append("totalBytes", 15000L)
                .append("avgBytes", 3000.0)
                .append("maxBytes", 4000L)
                .append("minBytes", 2000L)
                .append("documentCount", 5L);

        Document systemTotal = new Document("_id", null)
                .append("totalDocuments", 8L)
                .append("totalBytes", 25000L)
                .append("avgBytes", 3125.0);

        Document facetResult = new Document()
                .append("envStats", List.of(env1, env2))
                .append("appEnvStats", List.of(appEnv1, appEnv2))
                .append("storageStats", List.of(storageApp))
                .append("systemTotals", List.of(systemTotal));

        @SuppressWarnings("unchecked")
        AggregationResults<Document> aggregationResults = mock(AggregationResults.class);
        when(aggregationResults.getUniqueMappedResult()).thenReturn(facetResult);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("configurations"), eq(Document.class)))
                .thenReturn(aggregationResults);

        StatsResponse stats = statsService.getSystemStats();

        assertNotNull(stats);
        assertEquals(142L, stats.getTotalAuditEntries());
        assertEquals(12L, stats.getCacheDocumentCount());
        assertEquals(2, stats.getTotalEnvironments());
        assertTrue(stats.getActiveEnvironments().contains("production"));
        assertTrue(stats.getActiveEnvironments().contains("staging"));

        assertEquals(2, stats.getTotalApplications());
        assertEquals(8L, stats.getTotalVersions());
        assertEquals(4.0, stats.getAverageVersionsPerApp());
        assertEquals("auth-service", stats.getMostRecentApp());
        assertEquals("production", stats.getMostRecentEnv());

        assertEquals(5L, stats.getVersionCounts().get("auth-service:production"));
        assertEquals(3L, stats.getVersionCounts().get("order-service:production"));

        assertNotNull(stats.getStorageSizeDistributions());
        assertEquals(25000L, stats.getStorageSizeDistributions().get("totalStorageBytes"));
        assertEquals(8L, stats.getStorageSizeDistributions().get("totalDocuments"));
    }

    @Test
    void testGetSystemStatsEmptyOrFallback() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("configurations"), eq(Document.class)))
                .thenThrow(new RuntimeException("Collection offline"));

        StatsResponse stats = statsService.getSystemStats();

        assertNotNull(stats);
        assertEquals(0, stats.getTotalApplications());
        assertEquals(0, stats.getTotalEnvironments());
        assertEquals(0L, stats.getTotalVersions());
        assertNotNull(stats.getActiveEnvironments());
        assertTrue(stats.getActiveEnvironments().isEmpty());
        assertNotNull(stats.getVersionCounts());
        assertNotNull(stats.getStorageSizeDistributions());
    }
}
