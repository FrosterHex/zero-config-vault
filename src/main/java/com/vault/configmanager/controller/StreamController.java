package com.vault.configmanager.controller;

import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.AuditService;
import com.vault.configmanager.service.ChangeStreamService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller exposing Server-Sent Events (SSE) endpoints backed by MongoDB Change Streams (collection.watch()).
 * Microservices and dashboard clients receive push notifications on configuration updates with zero polling overhead.
 */
@RestController
@RequestMapping("/api/v1/vault/stream")
public class StreamController {

    private final ChangeStreamService changeStreamService;
    private final AuditService auditService;

    public StreamController(ChangeStreamService changeStreamService, AuditService auditService) {
        this.changeStreamService = changeStreamService;
        this.auditService = auditService;
    }

    /**
     * Scoped SSE stream endpoint for a specific application and environment.
     */
    @GetMapping(value = "/{app}/{env}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamConfigChanges(
            @PathVariable String app,
            @PathVariable String env,
            HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String maskedKey = (String) request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        auditService.log("SSE_STREAM_SUBSCRIBE", app, env, ip, maskedKey, true, "Subscribed to scoped change stream");
        return changeStreamService.subscribe(app, env);
    }

    /**
     * Global SSE stream endpoint for administrative telemetry dashboards.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAllConfigChanges(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String maskedKey = (String) request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR);

        auditService.log("SSE_STREAM_GLOBAL_SUBSCRIBE", "global", "all", ip, maskedKey, true, "Subscribed to global change stream");
        return changeStreamService.subscribeGlobal();
    }
}
