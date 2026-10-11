package com.vault.configmanager.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB document entity representing binary configuration metadata in 'binary_configurations'.
 * The underlying binary byte chunks are stored in the MongoDB GridFS bucket 'vault_binaries' (fs.chunks / fs.files).
 * Enforces strict GitOps-style temporal versioning and SHA-512 byte-level integrity verification.
 */
@Document(collection = "binary_configurations")
@CompoundIndex(name = "binary_app_env_file_ver_idx",
        def = "{'applicationName': 1, 'environment': 1, 'filename': 1, 'version': 1}",
        unique = true)
public class BinaryConfigDocument {

    @Id
    private String id;

    private String applicationName;
    private String environment;
    private String filename;
    private String contentType;
    private int version;
    private String gridFsFileId;
    private long fileSizeBytes;
    private String documentHash;
    private Map<String, String> metadata = new HashMap<>();
    private LocalDateTime createdAt;

    public BinaryConfigDocument() {
        this.createdAt = LocalDateTime.now();
    }

    public BinaryConfigDocument(String applicationName, String environment, String filename,
                                String contentType, int version, String gridFsFileId,
                                long fileSizeBytes, String documentHash,
                                Map<String, String> metadata, LocalDateTime createdAt) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.filename = filename;
        this.contentType = contentType != null ? contentType : "application/octet-stream";
        this.version = version;
        this.gridFsFileId = gridFsFileId;
        this.fileSizeBytes = fileSizeBytes;
        this.documentHash = documentHash;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // Explicit Getters and Setters (STRICT NO LOMBOK POLICY)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getGridFsFileId() {
        return gridFsFileId;
    }

    public void setGridFsFileId(String gridFsFileId) {
        this.gridFsFileId = gridFsFileId;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "BinaryConfigDocument{" +
                "id='" + id + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", environment='" + environment + '\'' +
                ", filename='" + filename + '\'' +
                ", contentType='" + contentType + '\'' +
                ", version=" + version +
                ", gridFsFileId='" + gridFsFileId + '\'' +
                ", fileSizeBytes=" + fileSizeBytes +
                ", documentHash='" + documentHash + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
