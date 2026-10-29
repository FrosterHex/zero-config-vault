package com.vault.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background SSE client that subscribes to live MongoDB Change Stream notifications
 * from GET /api/v1/vault/stream/{app}/{env}.
 * Emits VaultConfigRefreshedEvent to the Spring application context on live updates.
 */
public class VaultSseRefreshListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(VaultSseRefreshListener.class);
    private static final String CONFIG_CHANGE_EVENT = "CONFIG_CHANGE";

    private final VaultProperties vaultProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread listenerThread;
    private HttpClient httpClient;
    // Name of the SSE event being read; only touched by the listener thread
    private String currentEventName;

    public VaultSseRefreshListener(VaultProperties vaultProperties,
                                   ApplicationEventPublisher eventPublisher) {
        this(vaultProperties, eventPublisher, new ObjectMapper());
    }

    public VaultSseRefreshListener(VaultProperties vaultProperties,
                                   ApplicationEventPublisher eventPublisher,
                                   ObjectMapper objectMapper) {
        this.vaultProperties = vaultProperties;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            String app = vaultProperties.getApplicationName();
            String env = vaultProperties.getEnvironment();
            log.info("Starting Vault SSE Refresh Listener for app='{}', env='{}'", app, env);

            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            this.listenerThread = new Thread(this::listenToStream, "vault-sse-listener");
            this.listenerThread.setDaemon(true);
            this.listenerThread.start();
        }
    }

    private void listenToStream() {
        String base = vaultProperties.getUri().endsWith("/")
                ? vaultProperties.getUri().substring(0, vaultProperties.getUri().length() - 1)
                : vaultProperties.getUri();
        String streamUrl = String.format("%s/api/v1/vault/stream/%s/%s",
                base, vaultProperties.getApplicationName(), vaultProperties.getEnvironment());

        while (running.get()) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(streamUrl))
                        .header("Accept", "text/event-stream")
                        .GET();

                if (vaultProperties.getApiKey() != null && !vaultProperties.getApiKey().isBlank()) {
                    requestBuilder.header("X-API-Key", vaultProperties.getApiKey());
                }

                HttpResponse<InputStream> response = httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                        currentEventName = null;
                        String line;
                        while (running.get() && (line = reader.readLine()) != null) {
                            handleLine(line);
                        }
                    }
                } else {
                    log.warn("Vault SSE stream returned status {}. Retrying in 5 seconds...", response.statusCode());
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    log.debug("Vault SSE connection interrupted: {}. Reconnecting in 5 seconds...", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Processes one line of the SSE stream. Only CONFIG_CHANGE events describe a new configuration
     * version; the INIT event sent on connect is ignored. A blank line ends the current event.
     */
    void handleLine(String line) {
        if (line.isEmpty()) {
            currentEventName = null;
        } else if (line.startsWith("event:")) {
            currentEventName = line.substring(6).trim();
        } else if (line.startsWith("data:") && CONFIG_CHANGE_EVENT.equals(currentEventName)) {
            handleSseMessage(line.substring(5).trim());
        }
    }

    private void handleSseMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String app = root.has("applicationName") ? root.get("applicationName").asText() : vaultProperties.getApplicationName();
            String env = root.has("environment") ? root.get("environment").asText() : vaultProperties.getEnvironment();
            int version = root.has("version") ? root.get("version").asInt() : 0;
            String action = root.has("eventType") ? root.get("eventType").asText() : "UPDATE";

            log.info("Received live configuration update from Vault via MongoDB Change Stream: app={}, env={}, v={}, action={}",
                    app, env, version, action);

            VaultConfigRefreshedEvent event = new VaultConfigRefreshedEvent(this, app, env, version, action);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to parse SSE payload from Vault: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping Vault SSE Refresh Listener");
            if (listenerThread != null) {
                listenerThread.interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
