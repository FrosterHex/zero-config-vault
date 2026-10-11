package com.vault.configmanager.util;

import com.vault.configmanager.dto.response.DiffResponse;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utility for structural configuration diffing and dot-notation path traversal.
 */
@Component
public class ConfigDiffUtil {

    /**
     * Recursively compares two configuration payloads and produces structural diff keys.
     *
     * @param v1 origin version number
     * @param v2 target version number
     * @param payload1 origin configuration payload
     * @param payload2 target configuration payload
     * @return DiffResponse containing added, removed, modified, and unchanged counts
     */
    public DiffResponse comparePayloads(int v1, int v2, Map<String, Object> payload1, Map<String, Object> payload2) {
        Map<String, Object> flat1 = new LinkedHashMap<>();
        Map<String, Object> flat2 = new LinkedHashMap<>();

        flatten("", payload1 != null ? payload1 : Map.of(), flat1);
        flatten("", payload2 != null ? payload2 : Map.of(), flat2);

        Map<String, Object> added = new LinkedHashMap<>();
        Map<String, Object> removed = new LinkedHashMap<>();
        Map<String, Map<String, Object>> modified = new LinkedHashMap<>();
        int unchangedCount = 0;

        // Check for removed, modified, and unchanged keys
        for (Map.Entry<String, Object> entry : flat1.entrySet()) {
            String key = entry.getKey();
            Object val1 = entry.getValue();

            if (!flat2.containsKey(key)) {
                removed.put(key, val1);
            } else {
                Object val2 = flat2.get(key);
                if (Objects.equals(val1, val2)) {
                    unchangedCount++;
                } else {
                    Map<String, Object> change = new LinkedHashMap<>();
                    change.put("old", val1);
                    change.put("new", val2);
                    modified.put(key, change);
                }
            }
        }

        // Check for added keys
        for (Map.Entry<String, Object> entry : flat2.entrySet()) {
            String key = entry.getKey();
            if (!flat1.containsKey(key)) {
                added.put(key, entry.getValue());
            }
        }

        return new DiffResponse(v1, v2, added, removed, modified, unchangedCount);
    }

    /**
     * Navigates a nested configuration tree using a dot-separated path (e.g., "database.host").
     *
     * @param payload the configuration map
     * @param dotPath dot-separated path string
     * @return Optional containing the extracted value, or empty if the path does not exist
     */
    public Optional<Object> extractByPath(Map<String, Object> payload, String dotPath) {
        if (payload == null || dotPath == null || dotPath.isBlank()) {
            return Optional.empty();
        }

        String[] parts = dotPath.split("\\.");
        Object current = payload;

        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(part)) {
                    return Optional.empty();
                }
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= list.size()) {
                        return Optional.empty();
                    }
                    current = list.get(index);
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        }

        return Optional.ofNullable(current);
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> map, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String currentKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nestedMap) {
                flatten(currentKey, (Map<String, Object>) nestedMap, target);
            } else {
                target.put(currentKey, value);
            }
        }
    }
}
