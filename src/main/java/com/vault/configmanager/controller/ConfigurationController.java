package com.vault.configmanager.controller;

import com.vault.configmanager.dto.request.BulkConfigRequest;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.dto.response.BulkConfigResponse;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.dto.response.DiffResponse;
import com.vault.configmanager.dto.response.VersionSummary;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.dto.response.ExportResult;
import com.vault.configmanager.service.ConfigurationService;
import com.vault.configmanager.service.ExportService;
import com.vault.configmanager.util.SecretMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller managing configuration ingestion, retrieval, partial patching,
 * version history, diffing, dot-notation search, bulk import, rollbacks, and export.
 */
@RestController
@RequestMapping("/api/v1/vault/config")
public class ConfigurationController {

    private final ConfigurationService configurationService;
    private final ExportService exportService;

    @Autowired
    public ConfigurationController(ConfigurationService configurationService,
                                   @Autowired(required = false) ExportService exportService) {
        this.configurationService = configurationService;
        this.exportService = exportService;
    }

    public ConfigurationController(ConfigurationService configurationService) {
        this(configurationService, null);
    }

    /**
     * Ingestion endpoint: Creates a new configuration or new sequential revision.
     */
    @PostMapping
    public ResponseEntity<ConfigResponse> createConfig(
            @Valid @RequestBody ConfigRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ConfigResponse response = configurationService.createConfiguration(request, ip, maskedKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Bulk import endpoint: Processes multiple configuration entries.
     * When atomic=true, uses MongoTransactionManager to ensure an ACID multi-document transaction.
     * When atomic=false, executes best-effort batch ingestion.
     */
    @PostMapping("/bulk")
    public ResponseEntity<BulkConfigResponse> bulkImport(
            @Valid @RequestBody BulkConfigRequest bulkRequest,
            @RequestParam(defaultValue = "false") boolean atomic,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        BulkConfigResponse response = configurationService.bulkImport(bulkRequest, atomic, ip, maskedKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieval endpoint: Gets the latest decrypted configuration.
     * Optional ?masked=true parameter masks sensitive secrets with "********".
     */
    @GetMapping("/{app}/{env}")
    public ResponseEntity<ConfigResponse> getLatestConfig(
            @PathVariable String app,
            @PathVariable String env,
            @RequestParam(defaultValue = "false") boolean masked,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ConfigResponse response = configurationService.getLatestConfiguration(app, env, ip, maskedKey);
        if (masked && response != null && response.getPayload() != null) {
            response.setPayload(SecretMaskingUtil.maskMap(response.getPayload()));
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Diffs two configuration revisions key-by-key.
     */
    @GetMapping("/{app}/{env}/diff")
    public ResponseEntity<DiffResponse> diffConfigs(
            @PathVariable String app,
            @PathVariable String env,
            @RequestParam int v1,
            @RequestParam int v2) {
        DiffResponse diff = configurationService.diffVersions(app, env, v1, v2);
        return ResponseEntity.ok(diff);
    }

    /**
     * Dot-notation path query endpoint (e.g. ?path=database.host).
     */
    @GetMapping("/{app}/{env}/search")
    public ResponseEntity<Map<String, Object>> searchByPath(
            @PathVariable String app,
            @PathVariable String env,
            @RequestParam String path,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        Object result = configurationService.searchByPath(app, env, path, ip, maskedKey);
        return ResponseEntity.ok(Map.of("path", path, "value", result));
    }

    /**
     * Retrieves a specific historical revision by version number.
     * Optional ?masked=true parameter masks sensitive secrets with "********".
     */
    @GetMapping("/{app}/{env}/version/{version}")
    public ResponseEntity<ConfigResponse> getConfigByVersion(
            @PathVariable String app,
            @PathVariable String env,
            @PathVariable int version,
            @RequestParam(defaultValue = "false") boolean masked,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ConfigResponse response = configurationService.getConfigurationByVersion(app, env, version, ip, maskedKey);
        if (masked && response != null && response.getPayload() != null) {
            response.setPayload(SecretMaskingUtil.maskMap(response.getPayload()));
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all versions for the given app and environment.
     */
    @GetMapping("/{app}/{env}/versions")
    public ResponseEntity<List<VersionSummary>> listVersions(
            @PathVariable String app,
            @PathVariable String env) {
        List<VersionSummary> versions = configurationService.listVersions(app, env);
        return ResponseEntity.ok(versions);
    }

    /**
     * Partial update: Deep merges changes into current config and persists as a new revision.
     */
    @PatchMapping("/{app}/{env}")
    public ResponseEntity<ConfigResponse> patchConfig(
            @PathVariable String app,
            @PathVariable String env,
            @RequestBody Map<String, Object> partialPayload,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ConfigResponse response = configurationService.updateConfigurationPartial(app, env, partialPayload, ip, maskedKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Rollback endpoint: Re-inserts specified target revision as a brand-new sequential version.
     */
    @PostMapping("/{app}/{env}/rollback/{version}")
    public ResponseEntity<ConfigResponse> rollbackConfig(
            @PathVariable String app,
            @PathVariable String env,
            @PathVariable int version,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ConfigResponse response = configurationService.rollbackToVersion(app, env, version, ip, maskedKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Export endpoint: Transforms configuration payload into JSON, YAML, or flat .env format.
     */
    @GetMapping("/{app}/{env}/export")
    public ResponseEntity<String> exportConfig(
            @PathVariable String app,
            @PathVariable String env,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) Integer version,
            HttpServletRequest servletRequest) {
        if (exportService == null) {
            throw new IllegalStateException("ExportService is not available");
        }
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ExportResult result = exportService.exportConfig(app, env, version, format, ip, maskedKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, result.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.getFileName() + "\"")
                .body(result.getContent());
    }

    /**
     * MongoDB Full-Text and Regex search across configurations.
     */
    @GetMapping("/search")
    public ResponseEntity<List<com.vault.configmanager.dto.response.ConfigSearchResult>> searchConfigs(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "q", required = false) String q) {
        String searchTerm = (query != null && !query.isBlank()) ? query : q;
        List<com.vault.configmanager.dto.response.ConfigSearchResult> results =
                configurationService.searchConfigurations(searchTerm);
        return ResponseEntity.ok(results);
    }
}
