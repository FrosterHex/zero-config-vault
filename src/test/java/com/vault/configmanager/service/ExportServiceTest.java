package com.vault.configmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.dto.response.ExportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportServiceTest {

    private ExportService exportService;
    private ConfigurationService configurationService;
    private AuditService auditService;
    private ObjectMapper objectMapper;
    private ObjectMapper yamlObjectMapper;

    @BeforeEach
    void setUp() {
        configurationService = mock(ConfigurationService.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        yamlObjectMapper = new ObjectMapper(new YAMLFactory());

        exportService = new ExportService(
                configurationService,
                auditService,
                objectMapper,
                yamlObjectMapper
        );
    }

    @Test
    void testExportToJson() {
        Map<String, Object> payload = Map.of("key", "value", "count", 42);
        String json = exportService.exportToJson(payload);

        assertNotNull(json);
        assertTrue(json.contains("\"key\" : \"value\""));
        assertTrue(json.contains("\"count\" : 42"));
    }

    @Test
    void testExportToYaml() {
        Map<String, Object> payload = Map.of("server", Map.of("port", 8080));
        String yaml = exportService.exportToYaml(payload);

        assertNotNull(yaml);
        assertTrue(yaml.contains("server:"));
        assertTrue(yaml.contains("port: 8080"));
    }

    @Test
    void testExportToEnvFlatFormat() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("host", "localhost");
        nested.put("port", 5432);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("database", nested);
        payload.put("app-name", "Zero Config Vault");
        payload.put("tags", List.of("prod", "primary"));

        String env = exportService.exportToEnv(payload);

        assertNotNull(env);
        assertTrue(env.contains("DATABASE_HOST=localhost"));
        assertTrue(env.contains("DATABASE_PORT=5432"));
        assertTrue(env.contains("APP_NAME=\"Zero Config Vault\""));
        assertTrue(env.contains("TAGS=prod,primary"));
    }

    @Test
    void testExportConfigLatestJson() {
        Map<String, Object> payload = Map.of("timeout", 100);
        ConfigResponse configResponse = new ConfigResponse(
                "id1", "app1", "prod", 3, "hash123", payload, Map.of(), LocalDateTime.now()
        );
        when(configurationService.getLatestConfiguration(eq("app1"), eq("prod"), anyString(), anyString()))
                .thenReturn(configResponse);

        ExportResult result = exportService.exportConfig("app1", "prod", null, "json", "127.0.0.1", "sk-test");

        assertEquals("application/json", result.getContentType());
        assertEquals("app1-prod-v3.json", result.getFileName());
        assertEquals("json", result.getFormat());
        assertTrue(result.getContent().contains("\"timeout\" : 100"));

        verify(auditService).log(eq("EXPORT"), eq("app1"), eq("prod"), eq("127.0.0.1"), eq("sk-test"), eq(true), isNull());
    }

    @Test
    void testExportConfigSpecificVersionYaml() {
        Map<String, Object> payload = Map.of("env", "staging");
        ConfigResponse configResponse = new ConfigResponse(
                "id2", "app1", "staging", 2, "hash456", payload, Map.of(), LocalDateTime.now()
        );
        when(configurationService.getConfigurationByVersion(eq("app1"), eq("staging"), eq(2), anyString(), anyString()))
                .thenReturn(configResponse);

        ExportResult result = exportService.exportConfig("app1", "staging", 2, "yaml", "127.0.0.1", "sk-test");

        assertEquals("application/x-yaml", result.getContentType());
        assertEquals("app1-staging-v2.yaml", result.getFileName());
        assertEquals("yaml", result.getFormat());
        assertTrue(result.getContent().contains("env: \"staging\"") || result.getContent().contains("env: staging"));

        verify(auditService).log(eq("EXPORT"), eq("app1"), eq("staging"), eq("127.0.0.1"), eq("sk-test"), eq(true), isNull());
    }

    @Test
    void testExportConfigEnvFormat() {
        Map<String, Object> payload = Map.of("port", 9000);
        ConfigResponse configResponse = new ConfigResponse(
                "id3", "app1", "dev", 1, "hash789", payload, Map.of(), LocalDateTime.now()
        );
        when(configurationService.getLatestConfiguration(eq("app1"), eq("dev"), anyString(), anyString()))
                .thenReturn(configResponse);

        ExportResult result = exportService.exportConfig("app1", "dev", null, "env", "127.0.0.1", "sk-test");

        assertEquals("text/plain", result.getContentType());
        assertEquals("app1-dev-v1.env", result.getFileName());
        assertEquals("env", result.getFormat());
        assertTrue(result.getContent().contains("PORT=9000"));
    }

    @Test
    void testExportUnsupportedFormatThrowsException() {
        ConfigResponse configResponse = new ConfigResponse(
                "id4", "app1", "prod", 1, "hash123", Map.of(), Map.of(), LocalDateTime.now()
        );
        when(configurationService.getLatestConfiguration(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(configResponse);

        assertThrows(IllegalArgumentException.class, () ->
                exportService.exportConfig("app1", "prod", null, "xml", "127.0.0.1", "sk-test")
        );
    }
}
