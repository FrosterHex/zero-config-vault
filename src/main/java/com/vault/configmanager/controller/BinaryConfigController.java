package com.vault.configmanager.controller;

import com.vault.configmanager.dto.response.BinaryConfigResponse;
import com.vault.configmanager.dto.response.BinaryPayload;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.BinaryConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller exposing REST endpoints for GridFS binary configuration management.
 * Supports secure uploads, SHA-512 integrity verification downloads, version histories, and rollbacks.
 */
@RestController
@RequestMapping("/api/v1/vault/binaries")
public class BinaryConfigController {

    private final BinaryConfigService binaryConfigService;

    public BinaryConfigController(BinaryConfigService binaryConfigService) {
        this.binaryConfigService = binaryConfigService;
    }

    /**
     * Uploads a new binary configuration file (or new revision) via multipart/form-data.
     */
    @PostMapping(value = "/{app}/{env}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BinaryConfigResponse> uploadBinary(
            @PathVariable String app,
            @PathVariable String env,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Map<String, String> metadata,
            HttpServletRequest request) throws IOException {

        String ip = request.getRemoteAddr();
        String maskedKey = (String) request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        // Filter out the 'file' param from metadata map if passed together
        Map<String, String> cleanMeta = new HashMap<>();
        if (metadata != null) {
            cleanMeta.putAll(metadata);
            cleanMeta.remove("file");
        }

        BinaryConfigResponse response = binaryConfigService.uploadBinaryConfig(
                app,
                env,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                cleanMeta,
                ip,
                maskedKey
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Downloads a binary configuration file (latest or specified historical version).
     */
    @GetMapping("/{app}/{env}/{filename}")
    public ResponseEntity<byte[]> downloadBinary(
            @PathVariable String app,
            @PathVariable String env,
            @PathVariable String filename,
            @RequestParam(required = false) Integer version,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        String maskedKey = (String) request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        BinaryPayload payload = binaryConfigService.downloadBinaryConfig(
                app, env, filename, version, ip, maskedKey
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, payload.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.getFilename() + "\"")
                .header("X-Document-Hash", payload.getDocumentHash())
                .header("X-Version", String.valueOf(payload.getVersion()))
                .body(payload.getContent());
    }

    /**
     * Lists version history for a specific binary configuration file.
     */
    @GetMapping("/{app}/{env}/{filename}/versions")
    public ResponseEntity<List<BinaryConfigResponse>> listVersions(
            @PathVariable String app,
            @PathVariable String env,
            @PathVariable String filename) {

        List<BinaryConfigResponse> versions = binaryConfigService.listVersions(app, env, filename);
        return ResponseEntity.ok(versions);
    }

    /**
     * Lists all binary configuration files for an application and environment.
     */
    @GetMapping("/{app}/{env}")
    public ResponseEntity<List<BinaryConfigResponse>> listBinaries(
            @PathVariable String app,
            @PathVariable String env) {

        List<BinaryConfigResponse> list = binaryConfigService.listByAppAndEnv(app, env);
        return ResponseEntity.ok(list);
    }

    /**
     * Rollback endpoint: Re-inserts target revision of binary file under a fresh incremented version.
     */
    @PostMapping("/{app}/{env}/{filename}/rollback/{version}")
    public ResponseEntity<BinaryConfigResponse> rollbackBinary(
            @PathVariable String app,
            @PathVariable String env,
            @PathVariable String filename,
            @PathVariable int version,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        String maskedKey = (String) request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        BinaryConfigResponse response = binaryConfigService.rollbackBinaryConfig(
                app, env, filename, version, ip, maskedKey
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
