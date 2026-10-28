package com.vault.configmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.dto.response.ExportResult;
import com.vault.configmanager.util.SecretMaskingUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for transforming configuration payloads into
 * JSON, YAML, and flat .env file formats.
 */
@Service
public class ExportService {

    private final ConfigurationService configurationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlObjectMapper;

    public ExportService(
            ConfigurationService configurationService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper) {
        this.configurationService = configurationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.yamlObjectMapper = yamlObjectMapper;
    }

    /**
     * Exports a configuration to the requested format (JSON, YAML, or ENV).
     *
     * @param app          the application name
     * @param env          the environment
     * @param version      optional specific revision (if null, latest revision is used)
     * @param format       target format: "json", "yaml" / "yml", or "env" / "dotenv"
     * @param reveal       when false, secret values are replaced with the masked placeholder
     * @param requestingIp client IP address for audit logging
     * @param apiKey       masked API key for audit logging
     * @return ExportResult containing content, content-type, filename, and format
     */
    public ExportResult exportConfig(String app, String env, Integer version, String format,
                                     boolean reveal, String requestingIp, String apiKey) {
        ConfigResponse config;
        if (version != null) {
            config = configurationService.getConfigurationByVersion(app, env, version, requestingIp, apiKey);
        } else {
            config = configurationService.getLatestConfiguration(app, env, requestingIp, apiKey);
        }

        Map<String, Object> payload = config.getPayload() != null ? config.getPayload() : Collections.emptyMap();
        if (!reveal) {
            payload = SecretMaskingUtil.maskMap(payload);
        }
        String normalizedFormat = format != null ? format.trim().toLowerCase(Locale.ROOT) : "json";

        String content;
        String contentType;
        String fileExtension;

        switch (normalizedFormat) {
            case "json":
                content = exportToJson(payload);
                contentType = "application/json";
                fileExtension = "json";
                break;
            case "yaml":
            case "yml":
                content = exportToYaml(payload);
                contentType = "application/x-yaml";
                fileExtension = "yaml";
                break;
            case "env":
            case "dotenv":
                content = exportToEnv(payload);
                contentType = "text/plain";
                fileExtension = "env";
                break;
            default:
                throw new IllegalArgumentException("Unsupported export format: '" + format +
                        "'. Supported formats are: json, yaml, env.");
        }

        // Record audit trail for export action
        auditService.log("EXPORT", app, env, requestingIp, apiKey, true, null);

        int actualVersion = config.getVersion();
        String fileName = String.format("%s-%s-v%d.%s", app, env, actualVersion, fileExtension);

        return new ExportResult(content, contentType, fileName, normalizedFormat);
    }

    /**
     * Formats the configuration payload into pretty-printed JSON.
     */
    public String exportToJson(Map<String, Object> payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export payload to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Formats the configuration payload into YAML.
     */
    public String exportToYaml(Map<String, Object> payload) {
        try {
            return yamlObjectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export payload to YAML: " + e.getMessage(), e);
        }
    }

    /**
     * Flattens nested maps and structures into key=value lines suitable for .env files.
     * Nested paths are converted to uppercase snake_case (e.g. database.host -> DATABASE_HOST).
     */
    public String exportToEnv(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }

        Map<String, String> flattened = new TreeMap<>();
        flattenMap("", payload, flattened);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : flattened.entrySet()) {
            sb.append(entry.getKey())
              .append("=")
              .append(formatEnvValue(entry.getValue()))
              .append("\n");
        }
        return sb.toString();
    }

    private void flattenMap(String prefix, Map<String, Object> map, Map<String, String> flattened) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String normalizedKey = key.replace('.', '_')
                                      .replace('-', '_')
                                      .toUpperCase(Locale.ROOT);
            String fullKey = prefix.isEmpty() ? normalizedKey : prefix + "_" + normalizedKey;

            Object val = entry.getValue();
            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) val;
                flattenMap(fullKey, subMap, flattened);
            } else if (val instanceof List) {
                List<?> list = (List<?>) val;
                String listStr = list.stream().map(String::valueOf).collect(Collectors.joining(","));
                flattened.put(fullKey, listStr);
            } else if (val != null) {
                flattened.put(fullKey, String.valueOf(val));
            } else {
                flattened.put(fullKey, "");
            }
        }
    }

    private String formatEnvValue(String value) {
        if (value == null) {
            return "";
        }
        // Wrap in double quotes if value contains spaces, quotes, newlines, or equals signs
        if (value.contains(" ") || value.contains("\n") || value.contains("\t") ||
            value.contains("\"") || value.contains("=")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
