package com.vault.configmanager.controller;

import com.vault.configmanager.dto.response.StatsResponse;
import com.vault.configmanager.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing system telemetry and aggregation pipeline dashboard statistics.
 */
@RestController
@RequestMapping("/api/v1/vault")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Retrieves system-wide telemetry collected via MongoDB Aggregation Pipelines.
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        StatsResponse stats = statsService.getSystemStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Dashboard alias endpoint for telemetry and storage distributions.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<StatsResponse> getDashboard() {
        StatsResponse stats = statsService.getSystemStats();
        return ResponseEntity.ok(stats);
    }
}
