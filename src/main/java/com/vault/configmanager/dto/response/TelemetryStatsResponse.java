package com.vault.configmanager.dto.response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for aggregated metrics derived from MongoDB Native Time-Series telemetry.
 */
public class TelemetryStatsResponse {

    private long totalRequests;
    private double avgLatencyMs;
    private long p95LatencyMs;
    private double cacheHitRatio;
    private long cacheHits;
    private long cacheMisses;
    private Map<String, Long> operations;
    private List<TimePointDTO> timeline;

    public TelemetryStatsResponse() {
        this.operations = new HashMap<>();
        this.timeline = new ArrayList<>();
    }

    public static class TimePointDTO {
        private String timeLabel;
        private long requestCount;
        private double avgLatency;
        private long cacheHits;

        public TimePointDTO() {
        }

        public TimePointDTO(String timeLabel, long requestCount, double avgLatency, long cacheHits) {
            this.timeLabel = timeLabel;
            this.requestCount = requestCount;
            this.avgLatency = avgLatency;
            this.cacheHits = cacheHits;
        }

        public String getTimeLabel() {
            return timeLabel;
        }

        public void setTimeLabel(String timeLabel) {
            this.timeLabel = timeLabel;
        }

        public long getRequestCount() {
            return requestCount;
        }

        public void setRequestCount(long requestCount) {
            this.requestCount = requestCount;
        }

        public double getAvgLatency() {
            return avgLatency;
        }

        public void setAvgLatency(double avgLatency) {
            this.avgLatency = avgLatency;
        }

        public long getCacheHits() {
            return cacheHits;
        }

        public void setCacheHits(long cacheHits) {
            this.cacheHits = cacheHits;
        }
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public long getP95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(long p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public double getCacheHitRatio() {
        return cacheHitRatio;
    }

    public void setCacheHitRatio(double cacheHitRatio) {
        this.cacheHitRatio = cacheHitRatio;
    }

    public long getCacheHits() {
        return cacheHits;
    }

    public void setCacheHits(long cacheHits) {
        this.cacheHits = cacheHits;
    }

    public long getCacheMisses() {
        return cacheMisses;
    }

    public void setCacheMisses(long cacheMisses) {
        this.cacheMisses = cacheMisses;
    }

    public Map<String, Long> getOperations() {
        return operations;
    }

    public void setOperations(Map<String, Long> operations) {
        this.operations = operations;
    }

    public List<TimePointDTO> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimePointDTO> timeline) {
        this.timeline = timeline;
    }
}
