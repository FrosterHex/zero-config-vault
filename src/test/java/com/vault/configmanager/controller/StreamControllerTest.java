package com.vault.configmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.exception.GlobalExceptionHandler;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.AuditService;
import com.vault.configmanager.service.ChangeStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StreamControllerTest {

    private MockMvc mockMvc;
    private ChangeStreamService changeStreamService;
    private AuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        changeStreamService = mock(ChangeStreamService.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        StreamController controller = new StreamController(changeStreamService, auditService);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("sk-dev-key-001", objectMapper);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(auditService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(filter)
                .build();
    }

    @Test
    void streamConfigChanges_scoped_returns200AndSseEmitter() throws Exception {
        when(changeStreamService.subscribe("payment-svc", "prod"))
                .thenReturn(new SseEmitter());

        MvcResult result = mockMvc.perform(get("/api/v1/vault/stream/payment-svc/prod")
                        .header("X-API-Key", "sk-dev-key-001")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(result.getResponse());
        verify(changeStreamService).subscribe("payment-svc", "prod");
        verify(auditService).log(eq("SSE_STREAM_SUBSCRIBE"), eq("payment-svc"), eq("prod"), anyString(), anyString(), eq(true), anyString());
    }

    @Test
    void streamAllConfigChanges_global_returns200AndSseEmitter() throws Exception {
        when(changeStreamService.subscribeGlobal())
                .thenReturn(new SseEmitter());

        MvcResult result = mockMvc.perform(get("/api/v1/vault/stream")
                        .header("X-API-Key", "sk-dev-key-001")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(result.getResponse());
        verify(changeStreamService).subscribeGlobal();
        verify(auditService).log(eq("SSE_STREAM_GLOBAL_SUBSCRIBE"), eq("global"), eq("all"), anyString(), anyString(), eq(true), anyString());
    }
}
