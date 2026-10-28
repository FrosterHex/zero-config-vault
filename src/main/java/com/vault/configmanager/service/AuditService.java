package com.vault.configmanager.service;

import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Service managing append-only auditing into the 'audit_logs' capped collection.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    private static final int MAX_LIMIT = 200;

    // Pseudo application names used by non-config audit entries; only wildcard keys may see them
    private static final Set<String> PSEUDO_APPS = Set.of("keys", "bulk", "global");

    private final MongoTemplate mongoTemplate;

    public AuditService(AuditLogRepository auditLogRepository) {
        this(auditLogRepository, null);
    }

    @Autowired
    public AuditService(AuditLogRepository auditLogRepository, MongoTemplate mongoTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Records an audit log entry.
     *
     * @param action action name (READ, CREATE, ROLLBACK, EXPORT, BULK_IMPORT)
     * @param applicationName application name
     * @param environment environment
     * @param requestingIp client IP address
     * @param apiKey masked API key
     * @param success whether operation succeeded
     * @param failureReason null if success, otherwise error message
     */
    public void log(String action, String applicationName, String environment,
                    String requestingIp, String apiKey, boolean success, String failureReason) {
        String maskedFailureReason = com.vault.configmanager.util.SecretMaskingUtil.maskAuditText(failureReason);
        AuditLog entry = new AuditLog(
                action,
                applicationName,
                environment,
                requestingIp,
                apiKey,
                success,
                maskedFailureReason,
                LocalDateTime.now()
        );
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Capped collection audit logging failure should not break primary operation flow
        }
    }

    public long count() {
        return auditLogRepository.count();
    }

    /**
     * Returns the newest audit entries the key is allowed to see, newest first.
     *
     * @param limit requested count, clamped to 1..200
     * @param app optional application filter
     * @param env optional environment filter
     * @param scope the authenticated key; entries outside its app/environment scope are dropped
     * @param revealIp when false, client IPs are truncated
     */
    public List<AuditLog> recent(int limit, String app, String env, ApiKey scope, boolean revealIp) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "$natural")).limit(bounded);
        if (app != null && !app.isBlank()) {
            query.addCriteria(Criteria.where("applicationName").is(app));
        }
        if (env != null && !env.isBlank()) {
            query.addCriteria(Criteria.where("environment").is(env));
        }
        return mongoTemplate.find(query, AuditLog.class).stream()
                .filter(entry -> isVisible(entry, scope))
                .peek(entry -> {
                    if (!revealIp) {
                        entry.setRequestingIp(truncateIp(entry.getRequestingIp()));
                    }
                })
                .toList();
    }

    private static boolean isVisible(AuditLog entry, ApiKey scope) {
        if (scope == null) {
            return false;
        }
        if (scope.getAllowedApps().contains("*")) {
            return scope.isEnvAllowed(entry.getEnvironment());
        }
        String app = entry.getApplicationName();
        boolean realApp = app != null && !PSEUDO_APPS.contains(app) && !app.startsWith("/");
        return realApp && scope.isAppAllowed(app) && scope.isEnvAllowed(entry.getEnvironment());
    }

    /**
     * Hides the host part of an address: 203.0.113.7 becomes 203.0.113.x, IPv6 keeps its first 3 groups.
     */
    public static String truncateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        if (ip.contains(":")) {
            String[] groups = ip.split(":");
            if (groups.length <= 3) {
                return ip;
            }
            return groups[0] + ":" + groups[1] + ":" + groups[2] + ":x";
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? ip : ip.substring(0, lastDot) + ".x";
    }
}
