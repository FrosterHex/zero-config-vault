package com.vault.configmanager.service;

import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.vault.configmanager.model.ApiKey;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditService auditService;
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    void log_successfulAction_savesToRepository() {
        auditService.log("CREATE", "user-service", "prod", "192.168.1.1", "sk-****-001", true, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("CREATE", saved.getAction());
        assertEquals("user-service", saved.getApplicationName());
        assertEquals("prod", saved.getEnvironment());
        assertEquals("192.168.1.1", saved.getRequestingIp());
        assertEquals("sk-****-001", saved.getApiKey());
        assertTrue(saved.isSuccess());
        assertNull(saved.getFailureReason());
        assertNotNull(saved.getTimestamp());
    }

    @Test
    void log_failureAction_savesFailureReason() {
        auditService.log("READ", "auth-service", "staging", "10.0.0.5", "sk-****-002", false, "Integrity check failed");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("READ", saved.getAction());
        assertFalse(saved.isSuccess());
        assertEquals("Integrity check failed", saved.getFailureReason());
    }

    @Test
    void log_whenRepositoryThrowsException_doesNotThrow() {
        doThrow(new RuntimeException("DB offline")).when(auditLogRepository).save(any());

        assertDoesNotThrow(() ->
                auditService.log("EXPORT", "app", "dev", "127.0.0.1", "key", true, null)
        );
    }

    @Test
    void count_returnsRepositoryCount() {
        when(auditLogRepository.count()).thenReturn(42L);
        assertEquals(42L, auditService.count());
    }

    @Test
    void truncateIp_ipv4_replacesLastOctet() {
        assertEquals("203.0.113.x", AuditService.truncateIp("203.0.113.7"));
    }

    @Test
    void truncateIp_ipv6_keepsFirstThreeGroups() {
        assertEquals("2001:db8:85a3:x", AuditService.truncateIp("2001:db8:85a3:0:0:8a2e:370:7334"));
    }

    @Test
    void truncateIp_nullOrBlank_isUnknown() {
        assertEquals("unknown", AuditService.truncateIp(null));
        assertEquals("unknown", AuditService.truncateIp("  "));
    }

    private AuditLog entry(String app, String env) {
        return new AuditLog("READ", app, env, "203.0.113.7", "sk-****-001", true, null, LocalDateTime.now());
    }

    @Test
    void recent_filtersByScopeAndClampsLimit() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AuditService service = new AuditService(auditLogRepository, mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of(
                entry("payment-gateway", "dev"),
                entry("billing-svc", "dev"),
                entry("keys", "some-key"),
                entry("/api/v1/vault/config/x", "all"),
                entry("payment-gateway", "prod")));
        ApiKey scoped = new ApiKey("scoped", "sk-", "hash", List.of("payment-gateway"), List.of("dev"), Set.of("READ"), null);

        List<AuditLog> result = service.recent(500, null, null, scoped, false);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(AuditLog.class));
        assertEquals(200, captor.getValue().getLimit());
        assertTrue(captor.getValue().getSortObject().containsKey("$natural"));
        assertEquals(1, result.size());
        assertEquals("payment-gateway", result.get(0).getApplicationName());
        assertEquals("dev", result.get(0).getEnvironment());
        assertEquals("203.0.113.x", result.get(0).getRequestingIp());
    }

    @Test
    void recent_wildcardKey_seesPseudoAppsAndFullIpWhenRevealed() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AuditService service = new AuditService(auditLogRepository, mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(AuditLog.class)))
                .thenReturn(List.of(entry("keys", "k"), entry("billing-svc", "dev")));
        ApiKey admin = new ApiKey("admin", "sk-", "hash", List.of("*"), List.of("*"), Set.of("ADMIN"), null);

        List<AuditLog> result = service.recent(0, null, null, admin, true);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(AuditLog.class));
        assertEquals(1, captor.getValue().getLimit());
        assertEquals(2, result.size());
        assertEquals("203.0.113.7", result.get(0).getRequestingIp());
    }
}
