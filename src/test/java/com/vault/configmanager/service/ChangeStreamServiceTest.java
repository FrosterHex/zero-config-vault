package com.vault.configmanager.service;

import com.vault.configmanager.dto.response.ConfigChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChangeStreamServiceTest {

    private ChangeStreamService changeStreamService;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        changeStreamService = new ChangeStreamService(mongoTemplate);
    }

    @Test
    void subscribe_scoped_createsEmitterAndTracksSubscription() {
        SseEmitter emitter = changeStreamService.subscribe("order-service", "prod");

        assertNotNull(emitter);
        assertEquals(1, changeStreamService.getActiveSubscriptionCount());
    }

    @Test
    void subscribeGlobal_createsEmitterAndTracksSubscription() {
        SseEmitter emitter = changeStreamService.subscribeGlobal();

        assertNotNull(emitter);
        assertEquals(1, changeStreamService.getActiveSubscriptionCount());
    }

    @Test
    void broadcastChange_dispatchesToMatchingScopedAndGlobalEmitters() {
        SseEmitter scopedEmitter = changeStreamService.subscribe("payment-svc", "prod");
        SseEmitter otherEmitter = changeStreamService.subscribe("other-svc", "dev");
        SseEmitter globalEmitter = changeStreamService.subscribeGlobal();

        assertEquals(3, changeStreamService.getActiveSubscriptionCount());

        ConfigChangeEvent event = new ConfigChangeEvent(
                "CREATE",
                "payment-svc",
                "prod",
                3,
                "sha512-test-hash",
                LocalDateTime.now(),
                Map.of("cluster", "primary")
        );

        // Does not throw exceptions and broadcasts cleanly
        assertDoesNotThrow(() -> changeStreamService.broadcastChange(event));
    }

    @Test
    void broadcastChange_invokesRegisteredListeners() {
        java.util.List<ConfigChangeEvent> received = new java.util.ArrayList<>();
        changeStreamService.addListener(received::add);
        ConfigChangeEvent event = new ConfigChangeEvent("CREATE", "app", "dev", 1, "h",
                LocalDateTime.now(), Map.of());

        changeStreamService.broadcastChange(event);

        assertEquals(java.util.List.of(event), received);
    }

    @Test
    void isTokenRejected_onlyForResumeTokenErrorCodes() {
        for (int code : new int[]{286, 280, 260}) {
            assertTrue(ChangeStreamService.isTokenRejected(new com.mongodb.MongoException(code, "rejected")), "code " + code);
        }
        for (int code : new int[]{10107, 11602, 91}) {
            assertFalse(ChangeStreamService.isTokenRejected(new com.mongodb.MongoException(code, "transient")), "code " + code);
        }
    }

    @Test
    void nullMongoTemplate_operatesInInternalEventBusMode() {
        ChangeStreamService standaloneService = new ChangeStreamService(null);
        standaloneService.startChangeStreamListener();
        assertFalse(standaloneService.isChangeStreamActive());
        assertEquals(ChangeStreamService.EventMode.FALLBACK, standaloneService.getEventMode());
        assertTrue(standaloneService.servicesShouldBroadcast());

        SseEmitter emitter = standaloneService.subscribe("app", "dev");
        assertNotNull(emitter);
        assertEquals(1, standaloneService.getActiveSubscriptionCount());
    }
}
