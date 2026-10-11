package com.vault.configmanager.service;

import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service managing append-only auditing into the 'audit_logs' capped collection.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
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
}
