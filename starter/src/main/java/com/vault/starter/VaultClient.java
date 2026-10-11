package com.vault.starter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Lightweight HTTP client for interacting with the Zero-Config Vault API.
 * Uses standard Java 17 HttpClient with zero third-party client library dependencies.
 */
public class VaultClient {

    private static final Logger log = LoggerFactory.getLogger(VaultClient.class);

    private final String baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public VaultClient(String baseUri) {
        this(baseUri, 5000);
    }

    public VaultClient(String baseUri, int timeoutMillis) {
        this.baseUri = baseUri.endsWith("/") ? baseUri.substring(0, baseUri.length() - 1) : baseUri;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public VaultClient(String baseUri, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUri = baseUri.endsWith("/") ? baseUri.substring(0, baseUri.length() - 1) : baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the latest configuration document from the vault, extracts the payload,
     * and flattens it into a dot-notation property map.
     */
    public Map<String, Object> fetchFlattenedProperties(String app, String env, String apiKey) {
        Map<String, Object> rawConfig = fetchConfiguration(app, env, apiKey);
        if (rawConfig == null || rawConfig.isEmpty()) {
            return Collections.emptyMap();
        }

        Object payloadObj = rawConfig.get("payload");
        if (payloadObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) payloadObj;
            return flattenMap("", payloadMap);
        }

        return flattenMap("", rawConfig);
    }

    /**
     * Executes GET /api/v1/vault/config/{app}/{env} with X-API-Key header.
     */
    public Map<String, Object> fetchConfiguration(String app, String env, String apiKey) {
        String endpoint = String.format("%s/api/v1/vault/config/%s/%s", baseUri, app, env);
        log.info("Fetching dynamic properties from Zero-Config Vault: {}", endpoint);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("Accept", "application/json");

            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("X-API-Key", apiKey);
            }

            HttpResponse<InputStream> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                try (InputStream body = response.body()) {
                    return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                }
            } else if (response.statusCode() == 404) {
                log.warn("Vault configuration not found for app='{}', env='{}' (HTTP 404)", app, env);
                return Collections.emptyMap();
            } else {
                throw new IllegalStateException("Vault returned unexpected HTTP status " + response.statusCode() + " from " + endpoint);
            }
        } catch (Exception e) {
            log.error("Failed to query Zero-Config Vault at {}: {}", endpoint, e.getMessage());
            throw new IllegalStateException("Failed to query Zero-Config Vault: " + e.getMessage(), e);
        }
    }

    /**
     * Flattens a nested Map into dot-notation properties suitable for Spring Environment.
     * Example: {"database": {"host": "localhost"}} -> "database.host" = "localhost"
     */
    public Map<String, Object> flattenMap(String prefix, Map<String, Object> map) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        if (map == null) {
            return flattened;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) value;
                flattened.putAll(flattenMap(fullPath, childMap));
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String itemPath = fullPath + "[" + i + "]";
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> childMap = (Map<String, Object>) item;
                        flattened.putAll(flattenMap(itemPath, childMap));
                    } else {
                        flattened.put(itemPath, item);
                    }
                }
            } else {
                flattened.put(fullPath, value);
            }
        }
        return flattened;
    }

    public String getBaseUri() {
        return baseUri;
    }
}
