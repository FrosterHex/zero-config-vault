package com.vault.configmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.security.TestApiKeys;
import com.vault.configmanager.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerTest {

    private MockMvc mockMvc;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        // The service is stubbed: the controller only decides whether IPs are revealed
        auditService = mock(AuditService.class);
        TestApiKeys keys = TestApiKeys.withAdmin("sk-admin-001")
                .register("sk-guest-read", List.of("*"), List.of("*"), Set.of("READ"));
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(new ObjectMapper(), keys.service(), mock(AuditService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(auditService)).addFilters(filter).build();
    }

    private AuditLog entry(String ip) {
        return new AuditLog("READ", "app", "dev", ip, "sk-****-001", true, null, LocalDateTime.now());
    }

    @Test
    void guestKey_getsTruncatedIps() throws Exception {
        when(auditService.recent(anyInt(), any(), any(), any(), eq(false))).thenReturn(List.of(entry("203.0.113.x")));

        mockMvc.perform(get("/api/v1/vault/audit").header("X-API-Key", "sk-guest-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestingIp").value("203.0.113.x"));

        verify(auditService).recent(eq(50), isNull(), isNull(), any(), eq(false));
    }

    @Test
    void adminKey_getsFullIps() throws Exception {
        when(auditService.recent(anyInt(), any(), any(), any(), eq(true))).thenReturn(List.of(entry("203.0.113.7")));

        mockMvc.perform(get("/api/v1/vault/audit").param("limit", "10").param("app", "app")
                        .header("X-API-Key", "sk-admin-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestingIp").value("203.0.113.7"));

        verify(auditService).recent(eq(10), eq("app"), isNull(), any(), eq(true));
    }
}
