package com.vault.configmanager.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogTest {

    @Test
    void modelAnnotations_shouldBeConfigured() throws NoSuchFieldException {
        org.springframework.data.mongodb.core.mapping.Document docAnnotation =
                AuditLog.class.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);
        assertNotNull(docAnnotation);
        assertEquals("audit_logs", docAnnotation.collection());

        Field idField = AuditLog.class.getDeclaredField("id");
        assertNotNull(idField.getAnnotation(Id.class), "@Id must be on id field");
    }

    @Test
    void gettersSettersAndConstructor() {
        LocalDateTime now = LocalDateTime.now();
        AuditLog log = new AuditLog("CREATE", "test-app", "prod", "127.0.0.1", "sk-****-001", true, null, now);

        assertEquals("CREATE", log.getAction());
        assertEquals("test-app", log.getApplicationName());
        assertEquals("prod", log.getEnvironment());
        assertEquals("127.0.0.1", log.getRequestingIp());
        assertEquals("sk-****-001", log.getApiKey());
        assertTrue(log.isSuccess());
        assertNull(log.getFailureReason());
        assertEquals(now, log.getTimestamp());

        log.setId("audit-123");
        assertEquals("audit-123", log.getId());
        assertTrue(log.toString().contains("audit-123"));
    }
}
