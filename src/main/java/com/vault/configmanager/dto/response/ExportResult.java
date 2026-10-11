package com.vault.configmanager.dto.response;

/**
 * Result DTO representing an exported configuration.
 * Encapsulates the formatted content, content type, target filename, and export format.
 */
public class ExportResult {

    private final String content;
    private final String contentType;
    private final String fileName;
    private final String format;

    public ExportResult(String content, String contentType, String fileName, String format) {
        this.content = content;
        this.contentType = contentType;
        this.fileName = fileName;
        this.format = format;
    }

    public String getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFormat() {
        return format;
    }
}
