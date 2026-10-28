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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Service managing append-only auditing into the 'audit_logs' capped collection.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    private static final int MAX_LIMIT = 200;

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
     * Returns the newest audit entries the key is allowed to see, newest first. The key's scope is
     * part of the query, so the limit applies to entries the caller may actually see. Entries whose
     * application is not in the key's list (including pseudo apps such as "keys", "bulk", "global"
     * and request URIs) are only matched by keys with the '*' application.
     *
     * @param limit requested count, clamped to 1..200
     * @param app optional application filter
     * @param env optional environment filter
     * @param scope the authenticated key
     * @param revealIp when false, client IPs are truncated
     */
    public List<AuditLog> recent(int limit, String app, String env, ApiKey scope, boolean revealIp) {
        if (scope == null) {
            return List.of();
        }
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        Query query = new Query();
        if (!addScope(query, "applicationName", app, scope.getAllowedApps())
                || !addScope(query, "environment", env, scope.getAllowedEnvironments())) {
            return List.of();
        }
        query.with(Sort.by(Sort.Direction.DESC, "$natural")).limit(bounded);
        List<AuditLog> entries = mongoTemplate.find(query, AuditLog.class);
        if (!revealIp) {
            entries.forEach(entry -> entry.setRequestingIp(truncateIp(entry.getRequestingIp())));
        }
        return entries;
    }

    /**
     * Adds the criterion for one field, combining the optional request filter with the key's allowed values.
     *
     * @return false when the request filter lies outside the key's scope, so nothing can match
     */
    private static boolean addScope(Query query, String field, String requested, List<String> allowed) {
        boolean wildcard = allowed.contains("*");
        boolean filtered = requested != null && !requested.isBlank();
        if (filtered) {
            if (!wildcard && !allowed.contains(requested)) {
                return false;
            }
            query.addCriteria(Criteria.where(field).is(requested));
        } else if (!wildcard) {
            query.addCriteria(Criteria.where(field).in(allowed));
        }
        return true;
    }

    /**
     * Hides the host part of an address: 203.0.113.7 becomes 203.0.113.x, IPv6 keeps its first 3 groups
     * (compressed forms are expanded first), and IPv4-mapped IPv6 is treated as IPv4.
     */
    public static String truncateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String value = ip.trim();
        if (value.contains(":")) {
            try {
                // A literal containing ':' is parsed locally, never resolved through DNS
                InetAddress address = InetAddress.getByName(value);
                if (address instanceof Inet6Address) {
                    byte[] b = address.getAddress();
                    return hextet(b, 0) + ":" + hextet(b, 2) + ":" + hextet(b, 4) + ":x";
                }
                if (address instanceof Inet4Address) {
                    return truncateIpv4(address.getHostAddress());
                }
            } catch (UnknownHostException e) {
                // fall through
            }
            return "unknown";
        }
        return truncateIpv4(value);
    }

    private static String hextet(byte[] bytes, int offset) {
        return Integer.toHexString(((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff));
    }

    private static String truncateIpv4(String ip) {
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? ip : ip.substring(0, lastDot) + ".x";
    }
}
