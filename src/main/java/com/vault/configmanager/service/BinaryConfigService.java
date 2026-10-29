package com.vault.configmanager.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.vault.configmanager.dto.response.BinaryConfigResponse;
import com.vault.configmanager.dto.response.BinaryPayload;
import com.vault.configmanager.dto.response.ConfigChangeEvent;
import com.vault.configmanager.exception.ConfigNotFoundException;
import com.vault.configmanager.exception.IntegrityViolationException;
import com.vault.configmanager.model.BinaryConfigDocument;
import com.vault.configmanager.repository.BinaryConfigRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing MongoDB GridFS binary configuration storage ('vault_binaries' bucket).
 * Supports enterprise binary blobs exceeding 16MB (TLS certs, .jks stores, .pem bundles, license keys)
 * with strict GitOps-style temporal versioning and SHA-512 byte integrity verification.
 */
@Service
public class BinaryConfigService {

    private static final Logger log = LoggerFactory.getLogger(BinaryConfigService.class);

    private final GridFsTemplate gridFsTemplate;
    private final BinaryConfigRepository binaryConfigRepository;
    private final HashingService hashingService;
    private final AuditService auditService;
    private final ChangeStreamService changeStreamService;

    public BinaryConfigService(GridFsTemplate gridFsTemplate,
                               BinaryConfigRepository binaryConfigRepository,
                               HashingService hashingService,
                               AuditService auditService) {
        this(gridFsTemplate, binaryConfigRepository, hashingService, auditService, null);
    }

    @Autowired
    public BinaryConfigService(GridFsTemplate gridFsTemplate,
                               BinaryConfigRepository binaryConfigRepository,
                               HashingService hashingService,
                               AuditService auditService,
                               @Autowired(required = false) ChangeStreamService changeStreamService) {
        this.gridFsTemplate = gridFsTemplate;
        this.binaryConfigRepository = binaryConfigRepository;
        this.hashingService = hashingService;
        this.auditService = auditService;
        this.changeStreamService = changeStreamService;
    }

    /**
     * Uploads and persists a binary configuration file into MongoDB GridFS.
     */
    public BinaryConfigResponse uploadBinaryConfig(String app, String env, String filename,
                                                   String contentType, byte[] content,
                                                   Map<String, String> metadata,
                                                   String requestingIp, String apiKey) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Binary file payload cannot be empty.");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be blank.");
        }

        // 1. Calculate SHA-512 integrity hash
        String documentHash = hashingService.hashBytes(content);

        // 2. Calculate next sequential version
        int nextVersion = binaryConfigRepository
                .findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc(app, env, filename)
                .map(d -> d.getVersion() + 1)
                .orElse(1);

        // 3. Store binary chunks in GridFS ('vault_binaries' bucket)
        Document gridFsMeta = new Document();
        gridFsMeta.put("applicationName", app);
        gridFsMeta.put("environment", env);
        gridFsMeta.put("filename", filename);
        gridFsMeta.put("version", nextVersion);
        gridFsMeta.put("documentHash", documentHash);
        if (metadata != null) {
            gridFsMeta.putAll(metadata);
        }

        ObjectId gridFsFileId = gridFsTemplate.store(
                new ByteArrayInputStream(content),
                filename,
                contentType != null ? contentType : "application/octet-stream",
                gridFsMeta
        );

        // 4. Persist metadata document in 'binary_configurations'
        BinaryConfigDocument document = new BinaryConfigDocument(
                app,
                env,
                filename,
                contentType,
                nextVersion,
                gridFsFileId.toHexString(),
                content.length,
                documentHash,
                metadata,
                LocalDateTime.now()
        );

        BinaryConfigDocument saved = binaryConfigRepository.save(document);

        // 5. Audit log & Change stream broadcast
        auditService.log("BINARY_UPLOAD", app, env, requestingIp, apiKey, true,
                filename + " v" + nextVersion + " (" + content.length + " bytes)");

        notifyChangeStream("BINARY_CREATE", app, env, nextVersion, documentHash, metadata);

        return toResponse(saved);
    }

    /**
     * Downloads a binary configuration file from GridFS and verifies SHA-512 integrity.
     */
    public BinaryPayload downloadBinaryConfig(String app, String env, String filename,
                                              Integer version, String requestingIp, String apiKey) {
        BinaryConfigDocument doc;
        if (version == null) {
            doc = binaryConfigRepository
                    .findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc(app, env, filename)
                    .orElseThrow(() -> new ConfigNotFoundException("Binary configuration '" + filename + "' not found for app=" + app + ", env=" + env));
        } else {
            doc = binaryConfigRepository
                    .findByApplicationNameAndEnvironmentAndFilenameAndVersion(app, env, filename, version)
                    .orElseThrow(() -> new ConfigNotFoundException("Binary configuration '" + filename + "' v" + version + " not found for app=" + app + ", env=" + env));
        }

        // Retrieve raw GridFS chunks
        ObjectId fileObjectId;
        try {
            fileObjectId = new ObjectId(doc.getGridFsFileId());
        } catch (IllegalArgumentException e) {
            throw new ConfigNotFoundException("Invalid GridFS ObjectId: " + doc.getGridFsFileId());
        }

        GridFSFile gridFsFile = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(fileObjectId)));
        if (gridFsFile == null) {
            throw new ConfigNotFoundException("Underlying GridFS file payload missing for " + filename + " (ID: " + doc.getGridFsFileId() + ")");
        }

        GridFsResource resource = gridFsTemplate.getResource(gridFsFile);
        byte[] content;
        try {
            content = resource.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read binary stream from GridFS for " + filename, e);
        }

        // Byte-level cryptographic integrity verification
        boolean valid = hashingService.verifyBytesHash(content, doc.getDocumentHash());
        if (!valid) {
            String failureReason = "SHA-512 fingerprint mismatch for binary file '" + filename + "'. Tampering detected.";
            auditService.log("BINARY_DOWNLOAD", app, env, requestingIp, apiKey, false, failureReason);
            throw new IntegrityViolationException(app, env, doc.getVersion());
        }

        auditService.log("BINARY_DOWNLOAD", app, env, requestingIp, apiKey, true,
                filename + " v" + doc.getVersion());

        return new BinaryPayload(
                content,
                doc.getFilename(),
                doc.getContentType(),
                doc.getVersion(),
                doc.getDocumentHash(),
                doc.getFileSizeBytes(),
                doc.getMetadata()
        );
    }

    /**
     * Lists all binary configurations for an application and environment.
     */
    public List<BinaryConfigResponse> listByAppAndEnv(String app, String env) {
        return binaryConfigRepository.findByApplicationNameAndEnvironmentOrderByVersionDesc(app, env)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lists version history for a specific binary configuration file.
     */
    public List<BinaryConfigResponse> listVersions(String app, String env, String filename) {
        return binaryConfigRepository.findByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc(app, env, filename)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * GitOps-style rollback for binary configurations. Re-inserts target revision under a new incremented version.
     */
    public BinaryConfigResponse rollbackBinaryConfig(String app, String env, String filename,
                                                     int targetVersion, String requestingIp, String apiKey) {
        BinaryConfigDocument targetDoc = binaryConfigRepository
                .findByApplicationNameAndEnvironmentAndFilenameAndVersion(app, env, filename, targetVersion)
                .orElseThrow(() -> new ConfigNotFoundException("Target revision v" + targetVersion + " of binary '" + filename + "' not found."));

        int nextVersion = binaryConfigRepository
                .findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc(app, env, filename)
                .map(d -> d.getVersion() + 1)
                .orElse(1);

        Map<String, String> tags = new HashMap<>(targetDoc.getMetadata());
        tags.put("rollback_from_version", String.valueOf(targetVersion));

        BinaryConfigDocument rolledBackDoc = new BinaryConfigDocument(
                app,
                env,
                filename,
                targetDoc.getContentType(),
                nextVersion,
                targetDoc.getGridFsFileId(),
                targetDoc.getFileSizeBytes(),
                targetDoc.getDocumentHash(),
                tags,
                LocalDateTime.now()
        );

        BinaryConfigDocument saved = binaryConfigRepository.save(rolledBackDoc);

        auditService.log("BINARY_ROLLBACK", app, env, requestingIp, apiKey, true,
                "Rolled back " + filename + " to v" + targetVersion + " as new v" + nextVersion);

        notifyChangeStream("BINARY_ROLLBACK", app, env, nextVersion, targetDoc.getDocumentHash(), tags);

        return toResponse(saved);
    }

    private void notifyChangeStream(String eventType, String app, String env, int version, String hash, Map<String, String> metadata) {
        if (changeStreamService != null && changeStreamService.servicesShouldBroadcast()) {
            try {
                Map<String, Object> meta = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
                meta.put("isBinary", true);
                changeStreamService.broadcastChange(new ConfigChangeEvent(
                        eventType, app, env, version, hash, LocalDateTime.now(), meta
                ));
            } catch (Exception e) {
                log.warn("Failed to broadcast change stream event for binary config app={}, env={}: {}", app, env, e.getMessage());
            }
        }
    }

    private BinaryConfigResponse toResponse(BinaryConfigDocument doc) {
        return new BinaryConfigResponse(
                doc.getId(),
                doc.getApplicationName(),
                doc.getEnvironment(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getVersion(),
                doc.getGridFsFileId(),
                doc.getFileSizeBytes(),
                doc.getDocumentHash(),
                doc.getMetadata(),
                doc.getCreatedAt()
        );
    }
}
