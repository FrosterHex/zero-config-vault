package com.vault.configmanager.controller;

import com.vault.configmanager.dto.response.ExportResult;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing configuration export capabilities in JSON, YAML, and flat .env formats.
 */
@RestController
@RequestMapping("/api/v1/vault/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Exports configuration payloads in JSON, YAML, or flat .env file format.
     *
     * @param app            application name
     * @param env            environment
     * @param format         export format: "json", "yaml", or "env" (default: "json")
     * @param version        optional version number (defaults to latest active revision)
     * @param servletRequest HTTP servlet request
     * @return file content formatted with matching Content-Type and Content-Disposition headers
     */
    @GetMapping("/{app}/{env}")
    public ResponseEntity<String> exportConfig(
            @PathVariable String app,
            @PathVariable String env,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) Integer version,
            HttpServletRequest servletRequest) {

        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ExportResult result = exportService.exportConfig(app, env, version, format, ip, maskedKey);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, result.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.getFileName() + "\"")
                .body(result.getContent());
    }
}
