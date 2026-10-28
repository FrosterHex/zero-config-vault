package com.vault.configmanager.controller;

import com.vault.configmanager.dto.request.CreateApiKeyRequest;
import com.vault.configmanager.dto.response.ApiKeyResponse;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.ApiKeyService;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing Scoped API Keys.
 * All key management endpoints require the ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/vault/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuditService auditService;

    public ApiKeyController(ApiKeyService apiKeyService, AuditService auditService) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
    }

    /**
     * Creates a new scoped API key with granular roles and allowed scopes.
     */
    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        ApiKey creator = (ApiKey) servletRequest.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_KEY_ATTR);
        ApiKeyResponse response = apiKeyService.createApiKey(request, creator);
        auditService.log("API_KEY_CREATED", "keys", request.getName(), ip, maskedKey, true,
                "Created key prefix: " + response.getKeyPrefix());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all registered API keys (secrets are masked).
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        List<ApiKeyResponse> keys = apiKeyService.listKeys();
        auditService.log("API_KEY_LIST", "keys", "all", ip, maskedKey, true, null);

        return ResponseEntity.ok(keys);
    }

    /**
     * Revokes an existing API key.
     */
    @PatchMapping("/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeKey(
            @PathVariable String id,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        boolean revoked = apiKeyService.revokeKey(id);
        auditService.log("API_KEY_REVOKED", "keys", id, ip, maskedKey, revoked,
                revoked ? "Key revoked" : "Key not found");

        if (revoked) {
            return ResponseEntity.ok(Map.of("id", id, "status", "REVOKED"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("id", id, "error", "API key not found"));
        }
    }

    /**
     * Deletes an existing API key document.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteKey(
            @PathVariable String id,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String maskedKey = (String) servletRequest.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        boolean deleted = apiKeyService.deleteKey(id);
        auditService.log("API_KEY_DELETED", "keys", id, ip, maskedKey, deleted,
                deleted ? "Key deleted" : "Key not found");

        if (deleted) {
            return ResponseEntity.ok(Map.of("id", id, "status", "DELETED"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("id", id, "error", "API key not found"));
        }
    }
}
