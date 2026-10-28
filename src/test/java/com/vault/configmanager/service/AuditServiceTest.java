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
    void truncateIp_compressedIpv6_isExpandedThenTruncated() {
        assertEquals("2001:db8:0:x", AuditService.truncateIp("2001:db8::1"));
        assertEquals("0:0:0:x", AuditService.truncateIp("::1"));
    }

    @Test
    void truncateIp_ipv4MappedIpv6_hidesLastOctet() {
        assertEquals("203.0.113.x", AuditService.truncateIp("::ffff:203.0.113.7"));
    }

    @Test
    void truncateIp_unparseableIpv6_isUnknown() {
        assertEquals("unknown", AuditService.truncateIp("not:an:ip:at:all:zz"));
        assertEquals("unknown", AuditService.truncateIp("host.example.com:80"));
        assertEquals("unknown", AuditService.truncateIp("evil.com:80"));
        assertEquals("unknown", AuditService.truncateIp("cafe:80"));
    }

    @Test
    void truncateIp_ipv6WithZone_ignoresZone() {
        assertEquals("fe80:0:0:x", AuditService.truncateIp("fe80::1%eth0"));
    }

    private Query capturedQuery(MongoTemplate mongoTemplate) {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(AuditLog.class));
        return captor.getValue();
    }

    @Test
    void recent_scopedKey_putsScopeInQueryAndClampsLimit() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AuditService service = new AuditService(auditLogRepository, mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of(entry("payment-gateway", "dev")));
        ApiKey scoped = new ApiKey("scoped", "sk-", "hash", List.of("payment-gateway"), List.of("dev"), Set.of("READ"), null);

        List<AuditLog> result = service.recent(500, null, null, scoped, false);

        Query query = capturedQuery(mongoTemplate);
        assertEquals(200, query.getLimit());
        assertTrue(query.getSortObject().containsKey("$natural"));
        assertEquals(List.of("payment-gateway"), ((org.bson.Document) query.getQueryObject().get("applicationName")).get("$in"));
        assertEquals(List.of("dev"), ((org.bson.Document) query.getQueryObject().get("environment")).get("$in"));
        assertEquals("203.0.113.x", result.get(0).getRequestingIp());
    }

    @Test
    void recent_scopedKey_withRequestFilterInsideScope_usesEquality() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AuditService service = new AuditService(auditLogRepository, mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of());
        ApiKey scoped = new ApiKey("scoped", "sk-", "hash", List.of("a", "b"), List.of("*"), Set.of("READ"), null);

        service.recent(10, "a", "prod", scoped, true);

        Query query = capturedQuery(mongoTemplate);
        assertEquals("a", query.getQueryObject().get("applicationName"));
        assertEquals("prod", query.getQueryObject().get("environment"));
    }

    @Test
    void recent_requestFilterOutsideScope_returnsNothingWithoutQuerying() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AuditService service = new AuditService(auditLogRepository, mongoTemplate);
        ApiKey scoped = new ApiKey("scoped", "sk-", "hash", List.of("a"), List.of("*"), Set.of("READ"), null);

        assertTrue(service.recent(10, "keys", null, scoped, true).isEmpty());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void recent_wildcardKey_hasNoScopeCriteriaAndFullIpWhenRevealed() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AuditService service = new AuditService(auditLogRepository, mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(AuditLog.class)))
                .thenReturn(List.of(entry("keys", "k"), entry("billing-svc", "dev")));
        ApiKey admin = new ApiKey("admin", "sk-", "hash", List.of("*"), List.of("*"), Set.of("ADMIN"), null);

        List<AuditLog> result = service.recent(0, null, null, admin, true);

        Query query = capturedQuery(mongoTemplate);
        assertEquals(1, query.getLimit());
        assertTrue(query.getQueryObject().isEmpty());
        assertEquals(2, result.size());
        assertEquals("203.0.113.7", result.get(0).getRequestingIp());
    }
}
