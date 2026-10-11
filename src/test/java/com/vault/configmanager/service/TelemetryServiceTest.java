package com.vault.configmanager.service;

import com.vault.configmanager.dto.response.TelemetryStatsResponse;
import com.vault.configmanager.model.AccessTelemetry;
import com.vault.configmanager.model.TelemetryMetadata;
import com.vault.configmanager.repository.TelemetryRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TelemetryServiceTest {

    private MongoTemplate mongoTemplate;
    private TelemetryRepository telemetryRepository;
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        telemetryRepository = mock(TelemetryRepository.class);
        telemetryService = new TelemetryService(mongoTemplate, telemetryRepository);
    }

    @Test
    void testGetTelemetryMetricsReturnsAggregatedStats() {
        Date now = new Date();
        List<AccessTelemetry> sampleList = List.of(
                new AccessTelemetry(now, new TelemetryMetadata("app1", "prod", "READ", "127.0.0.1"), 3, true, 200),
                new AccessTelemetry(now, new TelemetryMetadata("app1", "prod", "READ", "127.0.0.1"), 15, false, 200),
                new AccessTelemetry(now, new TelemetryMetadata("app2", "dev", "WRITE", "127.0.0.1"), 20, false, 201)
        );

        when(telemetryRepository.findByTimestampAfterOrderByTimestampAsc(any(Date.class)))
                .thenReturn(sampleList);

        AggregationResults<Document> mockOpResult = mock(AggregationResults.class);
        when(mockOpResult.getMappedResults()).thenReturn(List.of(
                new Document("_id", "READ").append("count", 2L),
                new Document("_id", "WRITE").append("count", 1L)
        ));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("config_access_telemetry"), eq(Document.class)))
                .thenReturn(mockOpResult);

        TelemetryStatsResponse stats = telemetryService.getTelemetryMetrics(60);

        assertNotNull(stats);
        assertEquals(3, stats.getTotalRequests());
        assertEquals(1, stats.getCacheHits());
        assertEquals(2, stats.getCacheMisses());
        assertEquals(33.3, stats.getCacheHitRatio());
        assertTrue(stats.getAvgLatencyMs() > 0);
    }

    @Test
    void testRecordAccessSavesToMongo() {
        assertDoesNotThrow(() ->
                telemetryService.recordAccess("app1", "prod", "READ", 5, true, 200, "127.0.0.1")
        );
    }
}
