package com.vault.configmanager.service;

import com.mongodb.MongoCommandException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.vault.configmanager.dto.response.ConfigChangeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Service managing real-time MongoDB Change Streams and active Server-Sent Events (SSE) subscribers.
 *
 * Architecture:
 * - The event mode is decided once at startup. On a replica set a database-level change stream over
 *   inserts into {@code configurations} and {@code binary_configurations} is the single source of live
 *   events (CHANGE_STREAM mode). Without change stream support the write services broadcast directly
 *   (FALLBACK mode).
 * - In CHANGE_STREAM mode the read loop reconnects after any error, resuming from the last resume token.
 * - Broadcasts live ConfigChangeEvent payloads to connected microservices and dashboards.
 */
@Service
public class ChangeStreamService {

    /** Where live events come from. Decided once at startup and never changed. */
    public enum EventMode { CHANGE_STREAM, FALLBACK }

    private static final Logger log = LoggerFactory.getLogger(ChangeStreamService.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes
    private static final String CONFIG_COLLECTION = "configurations";
    private static final String BINARY_COLLECTION = "binary_configurations";
    private static final String ROLLBACK_MARKER = "rollback_from_version";
    private static final int STARTUP_ATTEMPTS = 3;
    private static final long STARTUP_RETRY_DELAY_MS = 1000L;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final List<Bson> PIPELINE = List.of(Aggregates.match(Filters.and(
            Filters.eq("operationType", "insert"),
            Filters.in("ns.coll", CONFIG_COLLECTION, BINARY_COLLECTION))));

    private final MongoTemplate mongoTemplate;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> scopedEmitters = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ConfigChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    private final ExecutorService changeStreamExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mongo-change-stream-listener");
        t.setDaemon(true);
        return t;
    });

    private volatile EventMode eventMode = EventMode.FALLBACK;
    private volatile MongoChangeStreamCursor<ChangeStreamDocument<Document>> currentCursor;
    private volatile BsonDocument resumeToken;
    private volatile boolean shuttingDown = false;

    public ChangeStreamService(@Autowired(required = false) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Decides the event mode. Opens the change stream synchronously (up to three attempts, one second apart)
     * so the mode is known before the application serves traffic.
     */
    @PostConstruct
    public void startChangeStreamListener() {
        if (mongoTemplate == null) {
            eventMode = EventMode.FALLBACK;
            log.info("MongoTemplate not injected; live events run in FALLBACK mode (services broadcast directly).");
            return;
        }

        MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = null;
        for (int attempt = 1; attempt <= STARTUP_ATTEMPTS && cursor == null; attempt++) {
            try {
                cursor = openCursor(null);
            } catch (Exception e) {
                log.warn("Change stream open attempt {}/{} failed ({}: {})",
                        attempt, STARTUP_ATTEMPTS, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < STARTUP_ATTEMPTS && !sleepQuietly(STARTUP_RETRY_DELAY_MS)) {
                    break;
                }
            }
        }

        if (cursor == null) {
            eventMode = EventMode.FALLBACK;
            log.warn("MongoDB Change Streams unavailable; live events run in FALLBACK mode (services broadcast directly).");
            return;
        }

        currentCursor = cursor;
        resumeToken = cursor.getResumeToken();
        eventMode = EventMode.CHANGE_STREAM;
        log.info("Live events run in CHANGE_STREAM mode (database-level watch on {} and {}).",
                CONFIG_COLLECTION, BINARY_COLLECTION);
        changeStreamExecutor.submit(this::readLoop);
    }

    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> openCursor(BsonDocument token) {
        ChangeStreamIterable<Document> stream = mongoTemplate.getDb().watch(PIPELINE);
        if (token != null) {
            stream = stream.resumeAfter(token);
        }
        return stream.cursor();
    }

    /** Reopens after an error: from the resume token when possible, otherwise from now. */
    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> reopenCursor() {
        BsonDocument token = resumeToken;
        if (token != null) {
            try {
                return openCursor(token);
            } catch (MongoCommandException e) {
                log.warn("Change stream resume token rejected ({}); resuming from now, events in the gap are lost.",
                        e.getErrorCodeName());
            }
        } else {
            log.warn("No change stream resume token; resuming from now, events in the gap are lost.");
        }
        return openCursor(null);
    }

    /** Reads events until shutdown, reconnecting with exponential backoff after any error. */
    private void readLoop() {
        long backoff = INITIAL_BACKOFF_MS;
        while (!shuttingDown) {
            MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = currentCursor;
            try {
                if (cursor == null) {
                    cursor = reopenCursor();
                    currentCursor = cursor;
                    log.info("MongoDB Change Stream reconnected.");
                }
                while (!shuttingDown) {
                    // tryNext waits up to the server's await time, so this loop does not spin.
                    ChangeStreamDocument<Document> change = cursor.tryNext();
                    if (change != null) {
                        processOplogChange(change);
                    }
                    BsonDocument token = cursor.getResumeToken();
                    if (token != null) {
                        resumeToken = token;
                    }
                    backoff = INITIAL_BACKOFF_MS;
                }
            } catch (Exception e) {
                if (shuttingDown) {
                    break;
                }
                log.warn("MongoDB Change Stream interrupted ({}: {}); reconnecting in {} ms.",
                        e.getClass().getSimpleName(), e.getMessage(), backoff);
                closeQuietly(cursor);
                currentCursor = null;
                if (!sleepQuietly(backoff)) {
                    break;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
        closeQuietly(currentCursor);
    }

    private void processOplogChange(ChangeStreamDocument<Document> change) {
        try {
            Document doc = change.getFullDocument();
            if (doc == null || change.getNamespace() == null) {
                return;
            }
            boolean binary = BINARY_COLLECTION.equals(change.getNamespace().getCollectionName());
            Document markers = doc.get(binary ? "metadata" : "tags", Document.class);
            boolean rollback = markers != null && markers.containsKey(ROLLBACK_MARKER);

            Map<String, Object> metadata = markers != null ? new HashMap<>(markers) : new HashMap<>();
            String eventType;
            if (binary) {
                metadata.put("isBinary", true);
                eventType = rollback ? "BINARY_ROLLBACK" : "BINARY_CREATE";
            } else {
                eventType = rollback ? "ROLLBACK" : "CREATE";
            }

            broadcastChange(new ConfigChangeEvent(
                    eventType,
                    doc.getString("applicationName"),
                    doc.getString("environment"),
                    doc.getInteger("version", 1),
                    doc.getString("documentHash"),
                    LocalDateTime.now(),
                    metadata
            ));
        } catch (Exception e) {
            log.error("Error processing Change Stream event: {}", e.getMessage(), e);
        }
    }

    private static void closeQuietly(MongoChangeStreamCursor<?> cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception ignored) {
                // The cursor may already be closed or its connection gone.
            }
        }
    }

    /** Sleeps; returns false when interrupted (shutdown). */
    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Closes the active cursor so the read loop must reconnect. For tests only. */
    void closeCurrentCursorForTest() {
        closeQuietly(currentCursor);
    }

    public EventMode getEventMode() {
        return eventMode;
    }

    /** True when the write services must broadcast events themselves, i.e. in FALLBACK mode. */
    public boolean servicesShouldBroadcast() {
        return getEventMode() == EventMode.FALLBACK;
    }

    /** Registers a callback invoked by {@link #broadcastChange} for every event. */
    public void addListener(Consumer<ConfigChangeEvent> listener) {
        listeners.add(listener);
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
                            "changeStreamsActive", isChangeStreamActive(),
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
                            "changeStreamsActive", isChangeStreamActive(),
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
        for (Consumer<ConfigChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Change event listener failed: {}", e.getMessage());
            }
        }

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
        return eventMode == EventMode.CHANGE_STREAM;
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
        shuttingDown = true;
        closeQuietly(currentCursor);
        changeStreamExecutor.shutdownNow();
        try {
            if (!changeStreamExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Change stream listener did not stop within 5 s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
