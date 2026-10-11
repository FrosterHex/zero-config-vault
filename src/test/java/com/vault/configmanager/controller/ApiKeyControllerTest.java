package com.vault.configmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.request.CreateApiKeyRequest;
import com.vault.configmanager.dto.response.ApiKeyResponse;
import com.vault.configmanager.exception.GlobalExceptionHandler;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.ApiKeyService;
import com.vault.configmanager.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiKeyControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ApiKeyService apiKeyService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        apiKeyService = mock(ApiKeyService.class);
        auditService = mock(AuditService.class);

        ApiKeyController controller = new ApiKeyController(apiKeyService, auditService);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("sk-admin-key-001", objectMapper, apiKeyService);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(auditService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(filter)
                .build();
    }

    @Test
    void createKey_returns201WithSecret() throws Exception {
        CreateApiKeyRequest request = new CreateApiKeyRequest(
                "Order Service Key",
                "sk_live_",
                List.of("order-svc"),
                List.of("prod"),
                Set.of("WRITE"),
                30
        );

        ApiKeyResponse response = new ApiKeyResponse(
                "key-1",
                "Order Service Key",
                "sk_live_",
                "sk_live_abcdef123456",
                List.of("order-svc"),
                List.of("prod"),
                Set.of("WRITE"),
                true,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30),
                null
        );

        when(apiKeyService.createApiKey(any(CreateApiKeyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/vault/keys")
                        .header("X-API-Key", "sk-admin-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("key-1"))
                .andExpect(jsonPath("$.name").value("Order Service Key"))
                .andExpect(jsonPath("$.rawKey").value("sk_live_abcdef123456"));

        verify(apiKeyService).createApiKey(any(CreateApiKeyRequest.class));
    }

    @Test
    void listKeys_returns200WithoutRawSecrets() throws Exception {
        ApiKeyResponse response = new ApiKeyResponse(
                "key-1",
                "Order Service Key",
                "sk_live_",
                null, // null secret in listings
                List.of("order-svc"),
                List.of("prod"),
                Set.of("WRITE"),
                true,
                LocalDateTime.now(),
                null,
                null
        );

        when(apiKeyService.listKeys()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/vault/keys")
                        .header("X-API-Key", "sk-admin-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("key-1"))
                .andExpect(jsonPath("$[0].name").value("Order Service Key"))
                .andExpect(jsonPath("$[0].rawKey").doesNotExist());

        verify(apiKeyService).listKeys();
    }

    @Test
    void revokeKey_returns200() throws Exception {
        when(apiKeyService.revokeKey("key-1")).thenReturn(true);

        mockMvc.perform(patch("/api/v1/vault/keys/key-1/revoke")
                        .header("X-API-Key", "sk-admin-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        verify(apiKeyService).revokeKey("key-1");
    }

    @Test
    void deleteKey_returns200() throws Exception {
        when(apiKeyService.deleteKey("key-1")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/vault/keys/key-1")
                        .header("X-API-Key", "sk-admin-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));

        verify(apiKeyService).deleteKey("key-1");
    }
}
