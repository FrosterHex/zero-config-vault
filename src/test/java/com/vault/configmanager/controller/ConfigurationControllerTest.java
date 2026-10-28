package com.vault.configmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.dto.request.BulkConfigRequest;
import com.vault.configmanager.dto.response.BulkConfigResponse;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.dto.response.DiffResponse;
import com.vault.configmanager.dto.response.VersionSummary;
import com.vault.configmanager.exception.ConfigNotFoundException;
import com.vault.configmanager.exception.GlobalExceptionHandler;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.security.TestApiKeys;
import com.vault.configmanager.service.AuditService;
import com.vault.configmanager.service.ConfigurationService;
import com.vault.configmanager.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfigurationControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ConfigurationService configurationService;
    private ExportService exportService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        configurationService = mock(ConfigurationService.class);
        exportService = mock(ExportService.class);
        auditService = mock(AuditService.class);

        TestApiKeys keys = TestApiKeys.withAdmin("sk-dev-key-001")
                .register("sk-scoped-writer", List.of("user-svc"), List.of("prod"), Set.of("READ", "WRITE"))
                .register("sk-guest-read", List.of("*"), List.of("*"), Set.of("READ", "EXPORT"))
                .register("sk-guest-write", List.of("*"), List.of("*"), Set.of("READ", "WRITE"))
                .register("sk-guest-sandbox", List.of("playground"), List.of("*"), Set.of("READ", "WRITE", "ROLLBACK"));
        ConfigurationController controller = new ConfigurationController(configurationService, exportService, keys.service());
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(objectMapper, keys.service());
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(auditService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(filter)
                .build();
    }

    @Test
    void createConfig_withValidApiKey_returns201() throws Exception {
        ConfigRequest request = new ConfigRequest(
                "user-svc",
                "prod",
                Map.of("host", "localhost"),
                Map.of("env", "prod")
        );

        ConfigResponse response = new ConfigResponse(
                "doc-1",
                "user-svc",
                "prod",
                1,
                "hash123",
                Map.of("host", "localhost"),
                Map.of("env", "prod"),
                LocalDateTime.now()
        );

        when(configurationService.createConfiguration(any(ConfigRequest.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/vault/config")
                        .header("X-API-Key", "sk-dev-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.applicationName").value("user-svc"));
    }

    @Test
    void getLatestConfig_whenFound_returns200() throws Exception {
        ConfigResponse response = new ConfigResponse(
                "doc-1",
                "user-svc",
                "prod",
                1,
                "hash123",
                Map.of("host", "localhost"),
                Map.of(),
                LocalDateTime.now()
        );

        when(configurationService.getLatestConfiguration(eq("user-svc"), eq("prod"), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void getLatestConfig_whenNotFound_returns404() throws Exception {
        when(configurationService.getLatestConfiguration(eq("unknown"), eq("dev"), any(), any()))
                .thenThrow(new ConfigNotFoundException("unknown", "dev"));

        mockMvc.perform(get("/api/v1/vault/config/unknown/dev")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listVersions_returns200() throws Exception {
        List<VersionSummary> list = List.of(
                new VersionSummary(2, "hash2", Map.of(), LocalDateTime.now()),
                new VersionSummary(1, "hash1", Map.of(), LocalDateTime.now())
        );

        when(configurationService.listVersions("app", "prod")).thenReturn(list);

        mockMvc.perform(get("/api/v1/vault/config/app/prod/versions")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2));
    }

    @Test
    void rollback_returns201WithNewVersion() throws Exception {
        ConfigResponse response = new ConfigResponse(
                "doc-3",
                "app",
                "prod",
                3,
                "hash1",
                Map.of("key", "val"),
                Map.of("rollback_from_version", "1"),
                LocalDateTime.now()
        );

        when(configurationService.rollbackToVersion(eq("app"), eq("prod"), eq(1), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/vault/config/app/prod/rollback/1")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void diffConfigs_returns200() throws Exception {
        com.vault.configmanager.dto.response.DiffResponse diff =
                new com.vault.configmanager.dto.response.DiffResponse(1, 2, Map.of("addedKey", "val"), Map.of(), Map.of(), 5);

        when(configurationService.diffVersions("app", "prod", 1, 2)).thenReturn(diff);

        mockMvc.perform(get("/api/v1/vault/config/app/prod/diff?v1=1&v2=2")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionFrom").value(1))
                .andExpect(jsonPath("$.versionTo").value(2))
                .andExpect(jsonPath("$.unchangedCount").value(5));
    }

    @Test
    void searchByPath_returns200() throws Exception {
        when(configurationService.searchByPath(eq("app"), eq("prod"), eq("database.host"), any(), any()))
                .thenReturn("db.internal");

        mockMvc.perform(get("/api/v1/vault/config/app/prod/search?path=database.host")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("database.host"))
                .andExpect(jsonPath("$.value").value("db.internal"));
    }

    @Test
    void bulkImport_returns200() throws Exception {
        com.vault.configmanager.dto.request.BulkConfigRequest bulkReq =
                new com.vault.configmanager.dto.request.BulkConfigRequest(List.of(
                        new ConfigRequest("app-1", "prod", Map.of("k", "v"), Map.of())
                ));

        com.vault.configmanager.dto.response.BulkConfigResponse bulkResp =
                new com.vault.configmanager.dto.response.BulkConfigResponse(1, 1, 0, List.of(), List.of());

        when(configurationService.bulkImport(any(), anyBoolean(), any(), any())).thenReturn(bulkResp);

        mockMvc.perform(post("/api/v1/vault/config/bulk")
                        .header("X-API-Key", "sk-dev-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(1))
                .andExpect(jsonPath("$.succeeded").value(1));

        mockMvc.perform(post("/api/v1/vault/config/bulk?atomic=true")
                        .header("X-API-Key", "sk-dev-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(1))
                .andExpect(jsonPath("$.succeeded").value(1));
    }

    @Test
    void exportConfig_viaConfigController_returns200() throws Exception {
        com.vault.configmanager.dto.response.ExportResult exportResult =
                new com.vault.configmanager.dto.response.ExportResult("{\"key\":\"value\"}", "application/json", "app-prod-v1.json", "json");
        when(exportService.exportConfig(eq("app"), eq("prod"), isNull(), eq("json"), any(), any()))
                .thenReturn(exportResult);

        mockMvc.perform(get("/api/v1/vault/config/app/prod/export?format=json")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"app-prod-v1.json\""))
                .andExpect(content().string("{\"key\":\"value\"}"));
    }

    @Test
    void createConfig_outsideKeyScope_returns403WithoutWriting() throws Exception {
        ConfigRequest request = new ConfigRequest("billing-svc", "prod", Map.of("host", "localhost"), Map.of());

        mockMvc.perform(post("/api/v1/vault/config")
                        .header("X-API-Key", "sk-scoped-writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(configurationService, never()).createConfiguration(any(), any(), any());
    }

    @Test
    void createConfig_insideKeyScope_returns201() throws Exception {
        ConfigRequest request = new ConfigRequest("user-svc", "prod", Map.of("host", "localhost"), Map.of());
        when(configurationService.createConfiguration(any(ConfigRequest.class), any(), any()))
                .thenReturn(new ConfigResponse("doc-1", "user-svc", "prod", 1, "hash", Map.of(), Map.of(), LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/vault/config")
                        .header("X-API-Key", "sk-scoped-writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void bulkImport_withAnyEntryOutsideKeyScope_rejectsWholeBatch() throws Exception {
        com.vault.configmanager.dto.request.BulkConfigRequest bulkReq =
                new com.vault.configmanager.dto.request.BulkConfigRequest(List.of(
                        new ConfigRequest("user-svc", "prod", Map.of("k", "v"), Map.of()),
                        new ConfigRequest("billing-svc", "prod", Map.of("k", "v"), Map.of())
                ));

        mockMvc.perform(post("/api/v1/vault/config/bulk")
                        .header("X-API-Key", "sk-scoped-writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isForbidden());

        verify(configurationService, never()).bulkImport(any(), anyBoolean(), any(), any());
    }

    @Test
    void globalSearch_passesAuthenticatedKeyScopeToService() throws Exception {
        when(configurationService.searchConfigurations(eq("payment"), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vault/config/search?q=payment")
                        .header("X-API-Key", "sk-scoped-writer"))
                .andExpect(status().isOk());

        verify(configurationService).searchConfigurations(eq("payment"),
                argThat(key -> key != null && key.getAllowedApps().equals(List.of("user-svc"))));
    }

    @Test
    void getLatestConfig_withMaskedTrue_masksSensitivePayload() throws Exception {
        ConfigResponse response = new ConfigResponse(
                "doc-1",
                "user-svc",
                "prod",
                1,
                "hash123",
                new java.util.HashMap<>(Map.of("host", "localhost", "password", "my-secret-pw")),
                Map.of(),
                LocalDateTime.now()
        );

        when(configurationService.getLatestConfiguration(eq("user-svc"), eq("prod"), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod?masked=true")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.host").value("localhost"))
                .andExpect(jsonPath("$.payload.password").value("********"));
    }

    private static ConfigResponse secretResponse(String app, String env) {
        return new ConfigResponse("doc-1", app, env, 1, "hash123",
                Map.of("host", "h", "secure_settings", Map.of("pw", "s")), Map.of(), LocalDateTime.now());
    }

    @Test
    void getLatest_withoutReveal_masksEvenWhenMaskedFalseRequested() throws Exception {
        when(configurationService.getLatestConfiguration(eq("user-svc"), eq("prod"), any(), any()))
                .thenReturn(secretResponse("user-svc", "prod"));

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod?masked=false")
                        .header("X-API-Key", "sk-guest-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.secure_settings.pw").value("********"))
                .andExpect(jsonPath("$.payload.host").value("h"));
    }

    @Test
    void getLatest_admin_revealsPlaintext() throws Exception {
        when(configurationService.getLatestConfiguration(eq("user-svc"), eq("prod"), any(), any()))
                .thenReturn(secretResponse("user-svc", "prod"));

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod?masked=false")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.secure_settings.pw").value("s"));
    }

    @Test
    void getByVersion_withoutReveal_masks() throws Exception {
        when(configurationService.getConfigurationByVersion(eq("user-svc"), eq("prod"), eq(1), any(), any()))
                .thenReturn(secretResponse("user-svc", "prod"));

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod/version/1")
                        .header("X-API-Key", "sk-guest-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.secure_settings.pw").value("********"));
    }

    @Test
    void rollback_withoutReveal_masksResponse() throws Exception {
        when(configurationService.rollbackToVersion(eq("playground"), eq("dev"), eq(1), any(), any()))
                .thenReturn(secretResponse("playground", "dev"));

        mockMvc.perform(post("/api/v1/vault/config/playground/dev/rollback/1")
                        .header("X-API-Key", "sk-guest-sandbox"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.secure_settings.pw").value("********"));
    }

    @Test
    void create_withoutReveal_masksResponse() throws Exception {
        when(configurationService.createConfiguration(any(ConfigRequest.class), any(), any()))
                .thenReturn(secretResponse("user-svc", "prod"));
        ConfigRequest request = new ConfigRequest("user-svc", "prod",
                Map.of("secure_settings", Map.of("pw", "s")), Map.of());

        mockMvc.perform(post("/api/v1/vault/config")
                        .header("X-API-Key", "sk-guest-write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.secure_settings.pw").value("********"));
    }

    @Test
    void bulk_withoutReveal_masksEachResult() throws Exception {
        BulkConfigResponse bulk = new BulkConfigResponse(1, 1, 0,
                List.of(secretResponse("user-svc", "prod")), List.of());
        when(configurationService.bulkImport(any(BulkConfigRequest.class), anyBoolean(), any(), any()))
                .thenReturn(bulk);
        BulkConfigRequest request = new BulkConfigRequest(List.of(new ConfigRequest("user-svc", "prod",
                Map.of("secure_settings", Map.of("pw", "s")), Map.of())));

        mockMvc.perform(post("/api/v1/vault/config/bulk")
                        .header("X-API-Key", "sk-guest-write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].payload.secure_settings.pw").value("********"));
    }

    @Test
    void diff_withoutReveal_masksSecretValues() throws Exception {
        Map<String, Object> added = new java.util.LinkedHashMap<>();
        added.put("payment.secure_settings.apiKey", "sk_x");
        added.put("payment.currency", "USD");
        DiffResponse diff = new DiffResponse(1, 2, added, Map.of(),
                Map.of("database.secure_settings.password", Map.of("oldValue", "a", "newValue", "b")), 0);
        when(configurationService.diffVersions("user-svc", "prod", 1, 2)).thenReturn(diff);

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod/diff?v1=1&v2=2")
                        .header("X-API-Key", "sk-guest-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added['payment.secure_settings.apiKey']").value("********"))
                .andExpect(jsonPath("$.added['payment.currency']").value("USD"))
                .andExpect(jsonPath("$.modified['database.secure_settings.password'].oldValue").value("********"))
                .andExpect(jsonPath("$.modified['database.secure_settings.password'].newValue").value("********"));
    }

    @Test
    void searchByPath_withoutReveal_masksSecretPath() throws Exception {
        when(configurationService.searchByPath(eq("user-svc"), eq("prod"), eq("database.secure_settings.password"), any(), any()))
                .thenReturn("hunter2");
        when(configurationService.searchByPath(eq("user-svc"), eq("prod"), eq("database.host"), any(), any()))
                .thenReturn("db1");

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod/search?path=database.secure_settings.password")
                        .header("X-API-Key", "sk-guest-read"))
                .andExpect(jsonPath("$.value").value("********"));
        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod/search?path=database.host")
                        .header("X-API-Key", "sk-guest-read"))
                .andExpect(jsonPath("$.value").value("db1"));
    }

    @Test
    void searchByParentPath_masksNestedSecretsForGuestOnly() throws Exception {
        when(configurationService.searchByPath(eq("user-svc"), eq("prod"), eq("database"), any(), any()))
                .thenReturn(Map.of("host", "db1", "secure_settings", Map.of("password", "hunter2")));

        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod/search?path=database")
                        .header("X-API-Key", "sk-guest-read"))
                .andExpect(jsonPath("$.value.host").value("db1"))
                .andExpect(jsonPath("$.value.secure_settings.password").value("********"));
        mockMvc.perform(get("/api/v1/vault/config/user-svc/prod/search?path=database")
                        .header("X-API-Key", "sk-dev-key-001"))
                .andExpect(jsonPath("$.value.secure_settings.password").value("hunter2"));
    }
}
