package com.vault.configmanager.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.vault.configmanager.dto.response.BinaryConfigResponse;
import com.vault.configmanager.dto.response.BinaryPayload;
import com.vault.configmanager.exception.IntegrityViolationException;
import com.vault.configmanager.model.BinaryConfigDocument;
import com.vault.configmanager.repository.BinaryConfigRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BinaryConfigServiceTest {

    private GridFsTemplate gridFsTemplate;
    private BinaryConfigRepository binaryConfigRepository;
    private HashingService hashingService;
    private AuditService auditService;
    private ChangeStreamService changeStreamService;
    private BinaryConfigService binaryConfigService;

    @BeforeEach
    void setUp() {
        gridFsTemplate = mock(GridFsTemplate.class);
        binaryConfigRepository = mock(BinaryConfigRepository.class);
        hashingService = mock(HashingService.class);
        auditService = mock(AuditService.class);
        changeStreamService = mock(ChangeStreamService.class);

        binaryConfigService = new BinaryConfigService(
                gridFsTemplate,
                binaryConfigRepository,
                hashingService,
                auditService,
                changeStreamService
        );
    }

    @Test
    void uploadBinaryConfig_calculatesHashStoresGridFsAndPersistsDocument() {
        byte[] content = "test-tls-cert-content".getBytes();
        String expectedHash = "sha512-test-binary-hash";
        ObjectId fileId = new ObjectId();

        when(hashingService.hashBytes(content)).thenReturn(expectedHash);
        when(binaryConfigRepository.findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc("payment-svc", "prod", "cert.pem"))
                .thenReturn(Optional.empty());
        when(gridFsTemplate.store(any(InputStream.class), eq("cert.pem"), eq("application/x-pem-file"), any(Document.class)))
                .thenReturn(fileId);
        when(binaryConfigRepository.save(any(BinaryConfigDocument.class)))
                .thenAnswer(inv -> {
                    BinaryConfigDocument d = inv.getArgument(0);
                    d.setId("binary-doc-1");
                    return d;
                });

        BinaryConfigResponse response = binaryConfigService.uploadBinaryConfig(
                "payment-svc", "prod", "cert.pem", "application/x-pem-file", content,
                Map.of("purpose", "mTLS"), "127.0.0.1", "key"
        );

        assertNotNull(response);
        assertEquals("binary-doc-1", response.getId());
        assertEquals("payment-svc", response.getApplicationName());
        assertEquals(1, response.getVersion());
        assertEquals(fileId.toHexString(), response.getGridFsFileId());
        assertEquals(expectedHash, response.getDocumentHash());

        verify(gridFsTemplate).store(any(InputStream.class), eq("cert.pem"), eq("application/x-pem-file"), any(Document.class));
        verify(binaryConfigRepository).save(any(BinaryConfigDocument.class));
        verify(auditService).log(eq("BINARY_UPLOAD"), eq("payment-svc"), eq("prod"), anyString(), anyString(), eq(true), anyString());
    }

    @Test
    void downloadBinaryConfig_verifiesHashAndReturnsPayload() throws Exception {
        byte[] content = "valid-binary-bytes".getBytes();
        String hash = "valid-sha512-hash";
        ObjectId fileId = new ObjectId();

        BinaryConfigDocument doc = new BinaryConfigDocument(
                "auth-svc", "prod", "keystore.jks", "application/octet-stream",
                2, fileId.toHexString(), content.length, hash, Map.of(), LocalDateTime.now()
        );

        GridFSFile gridFsFile = mock(GridFSFile.class);
        GridFsResource resource = mock(GridFsResource.class);

        when(binaryConfigRepository.findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc("auth-svc", "prod", "keystore.jks"))
                .thenReturn(Optional.of(doc));
        when(gridFsTemplate.findOne(any())).thenReturn(gridFsFile);
        when(gridFsTemplate.getResource(gridFsFile)).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(hashingService.verifyBytesHash(content, hash)).thenReturn(true);

        BinaryPayload payload = binaryConfigService.downloadBinaryConfig(
                "auth-svc", "prod", "keystore.jks", null, "127.0.0.1", "key"
        );

        assertNotNull(payload);
        assertArrayEquals(content, payload.getContent());
        assertEquals(2, payload.getVersion());
        assertEquals(hash, payload.getDocumentHash());
        verify(auditService).log(eq("BINARY_DOWNLOAD"), eq("auth-svc"), eq("prod"), anyString(), anyString(), eq(true), anyString());
    }

    @Test
    void downloadBinaryConfig_hashMismatch_throwsIntegrityViolationException() throws Exception {
        byte[] content = "tampered-binary-bytes".getBytes();
        String hash = "original-sha512-hash";
        ObjectId fileId = new ObjectId();

        BinaryConfigDocument doc = new BinaryConfigDocument(
                "auth-svc", "prod", "keystore.jks", "application/octet-stream",
                1, fileId.toHexString(), content.length, hash, Map.of(), LocalDateTime.now()
        );

        GridFSFile gridFsFile = mock(GridFSFile.class);
        GridFsResource resource = mock(GridFsResource.class);

        when(binaryConfigRepository.findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc("auth-svc", "prod", "keystore.jks"))
                .thenReturn(Optional.of(doc));
        when(gridFsTemplate.findOne(any())).thenReturn(gridFsFile);
        when(gridFsTemplate.getResource(gridFsFile)).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(hashingService.verifyBytesHash(content, hash)).thenReturn(false);

        assertThrows(IntegrityViolationException.class, () ->
                binaryConfigService.downloadBinaryConfig("auth-svc", "prod", "keystore.jks", null, "127.0.0.1", "key")
        );
        verify(auditService).log(eq("BINARY_DOWNLOAD"), eq("auth-svc"), eq("prod"), anyString(), anyString(), eq(false), anyString());
    }

    @Test
    void rollbackBinaryConfig_createsNewVersionLinkedToTargetRevision() {
        BinaryConfigDocument v1 = new BinaryConfigDocument(
                "app", "prod", "tls.crt", "application/x-x509-ca-cert",
                1, "fileId-1", 1024, "hash-1", Map.of(), LocalDateTime.now()
        );
        BinaryConfigDocument v2 = new BinaryConfigDocument(
                "app", "prod", "tls.crt", "application/x-x509-ca-cert",
                2, "fileId-2", 2048, "hash-2", Map.of(), LocalDateTime.now()
        );

        when(binaryConfigRepository.findByApplicationNameAndEnvironmentAndFilenameAndVersion("app", "prod", "tls.crt", 1))
                .thenReturn(Optional.of(v1));
        when(binaryConfigRepository.findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc("app", "prod", "tls.crt"))
                .thenReturn(Optional.of(v2));
        when(binaryConfigRepository.save(any(BinaryConfigDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BinaryConfigResponse rolledBack = binaryConfigService.rollbackBinaryConfig(
                "app", "prod", "tls.crt", 1, "127.0.0.1", "key"
        );

        assertNotNull(rolledBack);
        assertEquals(3, rolledBack.getVersion());
        assertEquals("fileId-1", rolledBack.getGridFsFileId());
        assertEquals("hash-1", rolledBack.getDocumentHash());
        assertEquals("1", rolledBack.getMetadata().get("rollback_from_version"));
    }

    @Test
    void listByAppAndEnv_returnsMappedList() {
        BinaryConfigDocument doc = new BinaryConfigDocument(
                "app", "prod", "tls.crt", "application/x-x509-ca-cert",
                1, "fileId-1", 1024, "hash-1", Map.of(), LocalDateTime.now()
        );
        when(binaryConfigRepository.findByApplicationNameAndEnvironmentOrderByVersionDesc("app", "prod"))
                .thenReturn(List.of(doc));

        List<BinaryConfigResponse> list = binaryConfigService.listByAppAndEnv("app", "prod");
        assertEquals(1, list.size());
        assertEquals("tls.crt", list.get(0).getFilename());
    }
}
