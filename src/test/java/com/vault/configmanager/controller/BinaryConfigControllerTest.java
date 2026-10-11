package com.vault.configmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.response.BinaryConfigResponse;
import com.vault.configmanager.dto.response.BinaryPayload;
import com.vault.configmanager.exception.GlobalExceptionHandler;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.AuditService;
import com.vault.configmanager.service.BinaryConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BinaryConfigControllerTest {

    private MockMvc mockMvc;
    private BinaryConfigService binaryConfigService;
    private AuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        binaryConfigService = mock(BinaryConfigService.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        BinaryConfigController controller = new BinaryConfigController(binaryConfigService);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("sk-dev-key-001", objectMapper);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(auditService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(filter)
                .build();
    }

    @Test
    void uploadBinary_returns201WithMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.pem", "application/x-pem-file", "pem-cert-content".getBytes()
        );

        BinaryConfigResponse response = new BinaryConfigResponse(
                "id-1", "payment-svc", "prod", "cert.pem", "application/x-pem-file",
                1, "gridfs-id-1", 16, "sha512-hash", Map.of(), LocalDateTime.now()
        );

        when(binaryConfigService.uploadBinaryConfig(eq("payment-svc"), eq("prod"), eq("cert.pem"),
                eq("application/x-pem-file"), any(byte[].class), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/vault/binaries/payment-svc/prod/upload")
                        .file(file)
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.filename").value("cert.pem"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.documentHash").value("sha512-hash"));

        verify(binaryConfigService).uploadBinaryConfig(eq("payment-svc"), eq("prod"), eq("cert.pem"),
                eq("application/x-pem-file"), any(byte[].class), any(), any(), any());
    }

    @Test
    void downloadBinary_returns200WithFileAndHeaders() throws Exception {
        byte[] content = "pem-cert-content".getBytes();
        BinaryPayload payload = new BinaryPayload(
                content, "cert.pem", "application/x-pem-file", 1, "sha512-hash", content.length, Map.of()
        );

        when(binaryConfigService.downloadBinaryConfig(eq("payment-svc"), eq("prod"), eq("cert.pem"), isNull(), any(), any()))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/vault/binaries/payment-svc/prod/cert.pem")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-pem-file"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cert.pem\""))
                .andExpect(header().string("X-Document-Hash", "sha512-hash"))
                .andExpect(header().string("X-Version", "1"))
                .andExpect(content().bytes(content));

        verify(binaryConfigService).downloadBinaryConfig(eq("payment-svc"), eq("prod"), eq("cert.pem"), isNull(), any(), any());
    }

    @Test
    void listVersions_returns200() throws Exception {
        BinaryConfigResponse r1 = new BinaryConfigResponse(
                "id-1", "payment-svc", "prod", "cert.pem", "application/x-pem-file",
                1, "gridfs-1", 16, "hash-1", Map.of(), LocalDateTime.now()
        );

        when(binaryConfigService.listVersions("payment-svc", "prod", "cert.pem"))
                .thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/vault/binaries/payment-svc/prod/cert.pem/versions")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("cert.pem"))
                .andExpect(jsonPath("$[0].version").value(1));

        verify(binaryConfigService).listVersions("payment-svc", "prod", "cert.pem");
    }

    @Test
    void rollbackBinary_returns201() throws Exception {
        BinaryConfigResponse rolledBack = new BinaryConfigResponse(
                "id-2", "payment-svc", "prod", "cert.pem", "application/x-pem-file",
                2, "gridfs-1", 16, "hash-1", Map.of("rollback_from_version", "1"), LocalDateTime.now()
        );

        when(binaryConfigService.rollbackBinaryConfig(eq("payment-svc"), eq("prod"), eq("cert.pem"), eq(1), any(), any()))
                .thenReturn(rolledBack);

        mockMvc.perform(post("/api/v1/vault/binaries/payment-svc/prod/cert.pem/rollback/1")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.metadata.rollback_from_version").value("1"));

        verify(binaryConfigService).rollbackBinaryConfig(eq("payment-svc"), eq("prod"), eq("cert.pem"), eq(1), any(), any());
    }
}
