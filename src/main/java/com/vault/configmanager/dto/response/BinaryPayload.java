package com.vault.configmanager.dto.response;

import java.util.HashMap;
import java.util.Map;

/**
 * Carrier DTO containing downloaded raw binary bytes and associated metadata.
 */
public class BinaryPayload {

    private byte[] content;
    private String filename;
    private String contentType;
    private int version;
    private String documentHash;
    private long fileSizeBytes;
    private Map<String, String> metadata = new HashMap<>();

    public BinaryPayload() {
    }

    public BinaryPayload(byte[] content, String filename, String contentType,
                         int version, String documentHash, long fileSizeBytes,
                         Map<String, String> metadata) {
        this.content = content;
        this.filename = filename;
        this.contentType = contentType;
        this.version = version;
        this.documentHash = documentHash;
        this.fileSizeBytes = fileSizeBytes;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
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

    public String getDocumentHash() {
        return documentHash;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
}
