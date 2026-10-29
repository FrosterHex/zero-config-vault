package com.vault.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VaultSseRefreshListenerTest {

    private ApplicationEventPublisher publisher;
    private VaultSseRefreshListener listener;

    @BeforeEach
    void setUp() {
        VaultProperties properties = new VaultProperties();
        properties.setApplicationName("payment-gateway");
        properties.setEnvironment("production");
        publisher = mock(ApplicationEventPublisher.class);
        listener = new VaultSseRefreshListener(properties, publisher);
    }

    @Test
    void initEvent_publishesNothing() {
        listener.handleLine("event:INIT");
        listener.handleLine("data:{\"status\":\"CONNECTED\",\"applicationName\":\"payment-gateway\"}");
        listener.handleLine("");

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void configChangeEvent_publishesVersionAndEventType() {
        listener.handleLine("event:CONFIG_CHANGE");
        listener.handleLine("data:{\"eventType\":\"ROLLBACK\",\"applicationName\":\"payment-gateway\","
                + "\"environment\":\"production\",\"version\":7}");
        listener.handleLine("");

        ArgumentCaptor<VaultConfigRefreshedEvent> captor = ArgumentCaptor.forClass(VaultConfigRefreshedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        VaultConfigRefreshedEvent event = captor.getValue();
        assertEquals("payment-gateway", event.getApplicationName());
        assertEquals("production", event.getEnvironment());
        assertEquals(7, event.getVersion());
        assertEquals("ROLLBACK", event.getAction());
    }

    @Test
    void eventNameResetsAfterEachMessage() {
        listener.handleLine("event:CONFIG_CHANGE");
        listener.handleLine("data:{\"eventType\":\"CREATE\",\"version\":2}");
        listener.handleLine("");
        listener.handleLine("data:{\"status\":\"CONNECTED\"}");
        listener.handleLine("");

        verify(publisher).publishEvent(any(VaultConfigRefreshedEvent.class));
    }
}
