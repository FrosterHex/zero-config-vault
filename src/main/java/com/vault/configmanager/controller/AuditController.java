package com.vault.configmanager.controller;

import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.security.SecretVisibility;
import com.vault.configmanager.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing the append-only audit log, limited to the caller's application scope.
 */
@RestController
@RequestMapping("/api/v1/vault/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Returns the newest audit entries visible to the calling key. Client IPs are truncated
     * unless the key may reveal secrets.
     */
    @GetMapping
    public ResponseEntity<List<AuditLog>> recent(@RequestParam(defaultValue = "50") int limit,
                                                 @RequestParam(required = false) String app,
                                                 @RequestParam(required = false) String env,
                                                 HttpServletRequest request) {
        ApiKey key = (ApiKey) request.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_KEY_ATTR);
        return ResponseEntity.ok(auditService.recent(limit, app, env, key, SecretVisibility.canReveal(request)));
    }
}
