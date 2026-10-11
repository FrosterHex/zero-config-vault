package com.vault.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Vault Spring Boot Client Starter.
 * Prefix: vault
 */
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    /**
     * Base URL of the Zero-Config Vault backend service (e.g., http://localhost:8080).
     */
    private String uri = "http://localhost:8080";

    /**
     * Application name identifier for configuration lookup.
     * Defaults to spring.application.name if not explicitly set.
     */
    private String applicationName;

    /**
     * Environment identifier (e.g., dev, staging, production).
     * Defaults to active profile or "default".
     */
    private String environment = "default";

    /**
     * Authentication API key passed in X-API-Key header.
     */
    private String apiKey;

    /**
     * Whether failure to connect or resolve properties from the vault on startup aborts the bootstrap.
     */
    private boolean failOnError = false;

    /**
     * Whether to spawn a reactive background SSE stream listener to receive live configuration change notifications.
     */
    private boolean streamEnabled = false;

    /**
     * Connection timeout in milliseconds.
     */
    private int timeout = 5000;

    public VaultProperties() {
    }

    public VaultProperties(String uri, String applicationName, String environment, String apiKey) {
        this.uri = uri;
        this.applicationName = applicationName;
        this.environment = environment;
        this.apiKey = apiKey;
    }

    // Explicit Getters and Setters (STRICT NO LOMBOK POLICY)

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isStreamEnabled() {
        return streamEnabled;
    }

    public void setStreamEnabled(boolean streamEnabled) {
        this.streamEnabled = streamEnabled;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "VaultProperties{" +
                "uri='" + uri + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                ", streamEnabled=" + streamEnabled +
                '}';
    }
}
