package com.vault.configmanager.util;

import com.vault.configmanager.dto.response.DiffResponse;
import org.bson.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for dynamically masking sensitive credentials and encrypted configurations.
 * Replaces sensitive keys (passwords, tokens, secure_settings, private keys) with "********".
 */
public class SecretMaskingUtil {

    public static final String MASKED_PLACEHOLDER = "********";

    private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
            "password",
            "secret",
            "apikey",
            "api_key",
            "token",
            "access_token",
            "auth_token",
            "privatekey",
            "private_key",
            "credentials",
            "secret_key",
            "client_secret"
    );

    private static final Pattern AUDIT_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(\"?\\b(?:password|secret|apikey|api_key|token|credentials)\\b\"?\\s*[:=]\\s*\"?)([^\"\\s,;]+)(\"?)");

    /**
     * Recursively masks sensitive fields inside a MongoDB Document.
     */
    public static Document maskDocument(Document document) {
        if (document == null) {
            return null;
        }

        Document copy = new Document();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSecureSettingsKey(key)) {
                if (value instanceof Document) {
                    copy.put(key, maskAllValues((Document) value));
                } else if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapVal = (Map<String, Object>) value;
                    copy.put(key, maskAllMapValues(mapVal));
                } else {
                    copy.put(key, MASKED_PLACEHOLDER);
                }
            } else if (isSensitiveKey(key)) {
                if (value instanceof Document) {
                    copy.put(key, maskDocument((Document) value));
                } else if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapVal = (Map<String, Object>) value;
                    copy.put(key, maskMap(mapVal));
                } else {
                    copy.put(key, MASKED_PLACEHOLDER);
                }
            } else if (value instanceof Document) {
                copy.put(key, maskDocument((Document) value));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapVal = (Map<String, Object>) value;
                copy.put(key, maskMap(mapVal));
            } else if (value instanceof List) {
                copy.put(key, maskList((List<?>) value));
            } else {
                copy.put(key, value);
            }
        }
        return copy;
    }

    /**
     * Recursively masks sensitive fields inside a Map.
     */
    public static Map<String, Object> maskMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSecureSettingsKey(key)) {
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapVal = (Map<String, Object>) value;
                    copy.put(key, maskAllMapValues(mapVal));
                } else {
                    copy.put(key, MASKED_PLACEHOLDER);
                }
            } else if (isSensitiveKey(key)) {
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapVal = (Map<String, Object>) value;
                    copy.put(key, maskMap(mapVal));
                } else {
                    copy.put(key, MASKED_PLACEHOLDER);
                }
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapVal = (Map<String, Object>) value;
                copy.put(key, maskMap(mapVal));
            } else if (value instanceof List) {
                copy.put(key, maskList((List<?>) value));
            } else {
                copy.put(key, value);
            }
        }
        return copy;
    }

    private static List<Object> maskList(List<?> list) {
        List<Object> copy = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Document) {
                copy.add(maskDocument((Document) item));
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapItem = (Map<String, Object>) item;
                copy.add(maskMap(mapItem));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }

    private static Document maskAllValues(Document doc) {
        Document masked = new Document();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            if (entry.getValue() instanceof Document) {
                masked.put(entry.getKey(), maskAllValues((Document) entry.getValue()));
            } else {
                masked.put(entry.getKey(), MASKED_PLACEHOLDER);
            }
        }
        return masked;
    }

    private static Map<String, Object> maskAllMapValues(Map<String, Object> map) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) entry.getValue();
                masked.put(entry.getKey(), maskAllMapValues(childMap));
            } else {
                masked.put(entry.getKey(), MASKED_PLACEHOLDER);
            }
        }
        return masked;
    }

    public static boolean isSecureSettingsKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase().replace("-", "").replace("_", "");
        return "securesettings".equals(normalized);
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase().replace("-", "").replace("_", "");
        if ("securesettings".equals(normalized)) {
            return true;
        }
        for (String sensitive : SENSITIVE_KEY_NAMES) {
            if (normalized.contains(sensitive.replace("_", ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when any dot-separated segment of the path is a secure_settings or sensitive key name.
     */
    public static boolean isSensitivePath(String dotPath) {
        if (dotPath == null) {
            return false;
        }
        for (String segment : dotPath.split("\\.")) {
            if (isSecureSettingsKey(segment) || isSensitiveKey(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a copy of the diff where every value under a sensitive path is replaced by the placeholder.
     * The changed paths themselves stay visible.
     */
    public static DiffResponse maskDiff(DiffResponse diff) {
        if (diff == null) {
            return null;
        }
        Map<String, Object> added = maskPathValues(diff.getAdded());
        Map<String, Object> removed = maskPathValues(diff.getRemoved());
        Map<String, Map<String, Object>> modified = null;
        if (diff.getModified() != null) {
            modified = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : diff.getModified().entrySet()) {
                modified.put(entry.getKey(), isSensitivePath(entry.getKey())
                        ? maskAllMapValues(entry.getValue())
                        : maskEachValue(entry.getValue()));
            }
        }
        return new DiffResponse(diff.getVersionFrom(), diff.getVersionTo(), added, removed, modified,
                diff.getUnchangedCount());
    }

    /**
     * Structurally masks an arbitrary value: Maps via maskMap, Lists element by element, others unchanged.
     */
    @SuppressWarnings("unchecked")
    public static Object maskValue(Object value) {
        if (value instanceof Map) {
            return maskMap((Map<String, Object>) value);
        }
        if (value instanceof List) {
            return maskList((List<?>) value);
        }
        return value;
    }

    private static Map<String, Object> maskEachValue(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            copy.put(entry.getKey(), maskValue(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, Object> maskPathValues(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            copy.put(entry.getKey(), isSensitivePath(entry.getKey()) ? MASKED_PLACEHOLDER : maskValue(entry.getValue()));
        }
        return copy;
    }

    /**
     * Dynamically masks sensitive credentials found in raw audit text messages or exception traces.
     */
    public static String maskAuditText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = AUDIT_KEY_VALUE_PATTERN.matcher(text);
        return matcher.replaceAll("$1" + MASKED_PLACEHOLDER + "$3");
    }
}
