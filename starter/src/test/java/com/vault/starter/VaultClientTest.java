package com.vault.starter;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaultClientTest {

    @Test
    void flattenMap_flattensNestedObjectsAndArrays() {
        VaultClient client = new VaultClient("http://localhost:8080");

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("host", "db.internal");
        nested.put("port", 5432);

        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("max-size", 20);
        pool.put("min-idle", 5);
        nested.put("pool", pool);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("database", nested);
        root.put("tags", List.of("prod", "us-east"));
        root.put("enabled", true);

        Map<String, Object> flattened = client.flattenMap("", root);

        assertNotNull(flattened);
        assertEquals("db.internal", flattened.get("database.host"));
        assertEquals(5432, flattened.get("database.port"));
        assertEquals(20, flattened.get("database.pool.max-size"));
        assertEquals(5, flattened.get("database.pool.min-idle"));
        assertEquals("prod", flattened.get("tags[0]"));
        assertEquals("us-east", flattened.get("tags[1]"));
        assertEquals(true, flattened.get("enabled"));
    }

    @Test
    void baseUriNormalization_trimsTrailingSlash() {
        VaultClient client1 = new VaultClient("http://localhost:8080/");
        assertEquals("http://localhost:8080", client1.getBaseUri());

        VaultClient client2 = new VaultClient("http://localhost:8080");
        assertEquals("http://localhost:8080", client2.getBaseUri());
    }
}
