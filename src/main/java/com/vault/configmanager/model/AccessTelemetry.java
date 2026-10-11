package com.vault.configmanager.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TimeSeries;
import org.springframework.data.mongodb.core.timeseries.Granularity;

import java.util.Date;
import java.util.Objects;

/**
 * MongoDB Native Time-Series Entity.
 * Stored in the 'config_access_telemetry' collection.
 * Uses native timeField 'timestamp' and metaField 'metadata'.
 */
@Document(collection = "config_access_telemetry")
@TimeSeries(timeField = "timestamp", metaField = "metadata", granularity = Granularity.SECONDS)
public class AccessTelemetry {

    @Id
    private String id;

    private Date timestamp;

    private TelemetryMetadata metadata;

    private long latencyMs;

    private boolean cacheHit;

    private int statusCode;

    public AccessTelemetry() {
        this.timestamp = new Date();
    }

    public AccessTelemetry(Date timestamp, TelemetryMetadata metadata, long latencyMs, boolean cacheHit, int statusCode) {
        this.timestamp = timestamp != null ? timestamp : new Date();
        this.metadata = metadata;
        this.latencyMs = latencyMs;
        this.cacheHit = cacheHit;
        this.statusCode = statusCode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public TelemetryMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(TelemetryMetadata metadata) {
        this.metadata = metadata;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessTelemetry that = (AccessTelemetry) o;
        return latencyMs == that.latencyMs &&
                cacheHit == that.cacheHit &&
                statusCode == that.statusCode &&
                Objects.equals(id, that.id) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp, metadata, latencyMs, cacheHit, statusCode);
    }

    @Override
    public String toString() {
        return "AccessTelemetry{" +
                "id='" + id + '\'' +
                ", timestamp=" + timestamp +
                ", metadata=" + metadata +
                ", latencyMs=" + latencyMs +
                ", cacheHit=" + cacheHit +
                ", statusCode=" + statusCode +
                '}';
    }
}
