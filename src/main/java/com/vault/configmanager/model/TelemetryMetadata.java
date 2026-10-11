package com.vault.configmanager.model;

import java.util.Objects;

/**
 * Metadata object for MongoDB Native Time-Series collection 'config_access_telemetry'.
 * Acts as the metaField for time-series grouping and indexing.
 */
public class TelemetryMetadata {

    private String applicationName;
    private String environment;
    private String operation;
    private String clientIp;

    public TelemetryMetadata() {
    }

    public TelemetryMetadata(String applicationName, String environment, String operation, String clientIp) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.operation = operation;
        this.clientIp = clientIp;
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

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TelemetryMetadata that = (TelemetryMetadata) o;
        return Objects.equals(applicationName, that.applicationName) &&
                Objects.equals(environment, that.environment) &&
                Objects.equals(operation, that.operation) &&
                Objects.equals(clientIp, that.clientIp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationName, environment, operation, clientIp);
    }

    @Override
    public String toString() {
        return "TelemetryMetadata{" +
                "applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                ", operation='" + operation + '\'' +
                ", clientIp='" + clientIp + '\'' +
                '}';
    }
}
