package com.vault.configmanager.service;

import com.vault.configmanager.model.AuditLog;
import com.vault.configmanager.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
}
