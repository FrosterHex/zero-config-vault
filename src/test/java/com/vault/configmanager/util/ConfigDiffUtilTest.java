package com.vault.configmanager.util;

import com.vault.configmanager.dto.response.DiffResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigDiffUtilTest {

    private ConfigDiffUtil diffUtil;

    @BeforeEach
    void setUp() {
        diffUtil = new ConfigDiffUtil();
    }

    @Test
    void comparePayloads_detectsAddedRemovedModifiedAndUnchanged() {
        Map<String, Object> v1 = Map.of(
                "database", Map.of("host", "localhost", "port", 5432),
                "debug", true,
                "timeout", 30
        );

        Map<String, Object> v2 = Map.of(
                "database", Map.of("host", "db.prod.internal", "port", 5432),
                "timeout", 30,
                "poolSize", 20
        );

        DiffResponse diff = diffUtil.comparePayloads(1, 2, v1, v2);

        assertEquals(1, diff.getVersionFrom());
        assertEquals(2, diff.getVersionTo());

        // added: poolSize
        assertTrue(diff.getAdded().containsKey("poolSize"));
        assertEquals(20, diff.getAdded().get("poolSize"));

        // removed: debug
        assertTrue(diff.getRemoved().containsKey("debug"));
        assertEquals(true, diff.getRemoved().get("debug"));

        // modified: database.host (changed from localhost to db.prod.internal)
        assertTrue(diff.getModified().containsKey("database.host"));
        assertEquals("localhost", diff.getModified().get("database.host").get("old"));
        assertEquals("db.prod.internal", diff.getModified().get("database.host").get("new"));

        // unchanged: database.port, timeout (count: 2)
        assertEquals(2, diff.getUnchangedCount());
    }

    @Test
    void extractByPath_nestedMapNavigation() {
        Map<String, Object> payload = Map.of(
                "database", Map.of("host", "db-primary", "credentials", Map.of("user", "admin"))
        );

        Optional<Object> host = diffUtil.extractByPath(payload, "database.host");
        assertTrue(host.isPresent());
        assertEquals("db-primary", host.get());

        Optional<Object> user = diffUtil.extractByPath(payload, "database.credentials.user");
        assertTrue(user.isPresent());
        assertEquals("admin", user.get());
    }

    @Test
    void extractByPath_arrayIndexNavigation() {
        Map<String, Object> payload = Map.of(
                "servers", List.of("10.0.0.1", "10.0.0.2")
        );

        Optional<Object> first = diffUtil.extractByPath(payload, "servers.0");
        assertTrue(first.isPresent());
        assertEquals("10.0.0.1", first.get());

        Optional<Object> outOfBounds = diffUtil.extractByPath(payload, "servers.5");
        assertTrue(outOfBounds.isEmpty());
    }

    @Test
    void extractByPath_nonExistentPath_returnsEmpty() {
        Map<String, Object> payload = Map.of("key", "val");
        assertTrue(diffUtil.extractByPath(payload, "non.existent.path").isEmpty());
        assertTrue(diffUtil.extractByPath(null, "key").isEmpty());
        assertTrue(diffUtil.extractByPath(payload, "").isEmpty());
    }
}
