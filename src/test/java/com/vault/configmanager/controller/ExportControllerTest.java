package com.vault.configmanager.controller;

import com.vault.configmanager.dto.response.ExportResult;
import com.vault.configmanager.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExportControllerTest {

    private MockMvc mockMvc;
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = mock(ExportService.class);
        ExportController controller = new ExportController(exportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testExportJson() throws Exception {
        ExportResult result = new ExportResult("{\n  \"port\" : 8080\n}", "application/json", "demo-prod-v1.json", "json");
        when(exportService.exportConfig(eq("demo"), eq("prod"), isNull(), eq("json"), any(), any()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/vault/export/demo/prod")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-prod-v1.json\""))
                .andExpect(content().string("{\n  \"port\" : 8080\n}"));

        verify(exportService).exportConfig(eq("demo"), eq("prod"), isNull(), eq("json"), any(), any());
    }

    @Test
    void testExportYamlWithVersion() throws Exception {
        ExportResult result = new ExportResult("port: 8080\n", "application/x-yaml", "demo-prod-v2.yaml", "yaml");
        when(exportService.exportConfig(eq("demo"), eq("prod"), eq(2), eq("yaml"), any(), any()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/vault/export/demo/prod")
                        .param("format", "yaml")
                        .param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-yaml"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-prod-v2.yaml\""))
                .andExpect(content().string("port: 8080\n"));

        verify(exportService).exportConfig(eq("demo"), eq("prod"), eq(2), eq("yaml"), any(), any());
    }

    @Test
    void testExportEnv() throws Exception {
        ExportResult result = new ExportResult("PORT=8080\n", "text/plain", "demo-dev-v1.env", "env");
        when(exportService.exportConfig(eq("demo"), eq("dev"), isNull(), eq("env"), any(), any()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/vault/export/demo/dev")
                        .param("format", "env"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-dev-v1.env\""))
                .andExpect(content().string("PORT=8080\n"));
    }
}
