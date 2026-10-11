package com.vault.configmanager.repository;

import com.vault.configmanager.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AuditLog operations on the 'audit_logs' capped collection.
 * Note: Capped collections do not support delete operations.
 */
@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}
