package com.vault.configmanager.service;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.vault.configmanager.dto.response.ConfigChangeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Service managing real-time MongoDB Change Streams (collection.watch()) and
 * active Server-Sent Events (SSE) subscribers.
 *
 * Architecture:
 * - Subscribes to the native MongoDB oplog via collection.watch() across replica sets.
 * - Broadcasts live ConfigChangeEvent payloads directly to connected microservices and dashboards.
 * - Gracefully provides dual fallback for standalone development environments via internal event bus.
 */
@Service
public class ChangeStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChangeStreamService.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final MongoTemplate mongoTemplate;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> scopedEmitters = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    private final ExecutorService changeStreamExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mongo-change-stream-listener");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean changeStreamActive = false;

    public ChangeStreamService(@Autowired(required = false) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void startChangeStreamListener() {
        if (mongoTemplate == null) {
            log.info("MongoTemplate not injected; running ChangeStreamService in internal event-bus mode.");
            return;
        }

        changeStreamExecutor.submit(() -> {
            try {
                MongoCollection<Document> collection = mongoTemplate.getCollection("configurations");
                log.info("Attempting connection to MongoDB Change Stream (collection.watch()) on 'configurations'...");
                ChangeStreamIterable<Document> stream = collection.watch();
                changeStreamActive = true;
                log.info("Connected to MongoDB Change Stream oplog successfully. Real-time change events active.");

                for (ChangeStreamDocument<Document> change : stream) {
                    processOplogChange(change);
                }
            } catch (Exception e) {
                changeStreamActive = false;
                log.warn("MongoDB Change Stream could not be initialized on current instance ({}: {}). " +
                                "Operating in internal event-bus mode (ideal for single-node standalone dev environments).",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        });
    }

    private void processOplogChange(ChangeStreamDocument<Document> change) {
        try {
            OperationType opType = change.getOperationType();
            Document fullDoc = change.getFullDocument();
            if (fullDoc != null) {
                String app = fullDoc.getString("applicationName");
                String env = fullDoc.getString("environment");
                Integer version = fullDoc.getInteger("version", 1);
                String hash = fullDoc.getString("documentHash");

                ConfigChangeEvent event = new ConfigChangeEvent(
                        opType != null ? opType.getValue() : "OPLOG_EVENT",
                        app,
                        env,
                        version != null ? version : 1,
                        hash,
                        LocalDateTime.now(),
                        Map.of("source", "MONGODB_OPLOG_CHANGE_STREAM")
                );

                broadcastChange(event);
            }
        } catch (Exception e) {
            log.error("Error processing Change Stream event: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribes a client to real-time events for a specific application and environment.
     */
    public SseEmitter subscribe(String app, String env) {
        String key = buildScopeKey(app, env);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        scopedEmitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeScopedEmitter(key, emitter));
        emitter.onTimeout(() -> removeScopedEmitter(key, emitter));
        emitter.onError(e -> removeScopedEmitter(key, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "scope", key,
                            "app", app,
                            "env", env,
                            "changeStreamsActive", changeStreamActive,
                            "timestamp", LocalDateTime.now().toString()
                    )));
        } catch (IOException e) {
            removeScopedEmitter(key, emitter);
        }

        return emitter;
    }

    /**
     * Subscribes an administrative client to real-time events across all applications.
     */
    public SseEmitter subscribeGlobal() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        globalEmitters.add(emitter);

        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(e -> globalEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "scope", "GLOBAL",
                            "changeStreamsActive", changeStreamActive,
                            "timestamp", LocalDateTime.now().toString()
                    )));
        } catch (IOException e) {
            globalEmitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts a configuration change event to matching scoped emitters and all global emitters.
     */
    public void broadcastChange(ConfigChangeEvent event) {
        String key = buildScopeKey(event.getApplicationName(), event.getEnvironment());

        // 1. Broadcast to scoped subscribers
        List<SseEmitter> scoped = scopedEmitters.get(key);
        if (scoped != null && !scoped.isEmpty()) {
            List<SseEmitter> deadScoped = new ArrayList<>();
            for (SseEmitter emitter : scoped) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("CONFIG_CHANGE")
                            .data(event));
                } catch (Exception e) {
                    deadScoped.add(emitter);
                }
            }
            scoped.removeAll(deadScoped);
        }

        // 2. Broadcast to global subscribers
        if (!globalEmitters.isEmpty()) {
            List<SseEmitter> deadGlobal = new ArrayList<>();
            for (SseEmitter emitter : globalEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("CONFIG_CHANGE")
                            .data(event));
                } catch (Exception e) {
                    deadGlobal.add(emitter);
                }
            }
            globalEmitters.removeAll(deadGlobal);
        }
    }

    private void removeScopedEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> list = scopedEmitters.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                scopedEmitters.remove(key);
            }
        }
    }

    private String buildScopeKey(String app, String env) {
        return (app != null ? app : "*") + ":" + (env != null ? env : "*");
    }

    public boolean isChangeStreamActive() {
        return changeStreamActive;
    }

    public int getActiveSubscriptionCount() {
        int total = globalEmitters.size();
        for (List<SseEmitter> list : scopedEmitters.values()) {
            total += list.size();
        }
        return total;
    }

    @PreDestroy
    public void shutdown() {
        changeStreamExecutor.shutdownNow();
    }
}
