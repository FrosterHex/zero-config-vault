package com.vault.configmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HashingServiceTest {

    private HashingService hashingService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        hashingService = new HashingService(objectMapper);
    }

    @Test
    void hash_shouldProduce128CharHexDigest() {
        String hash = hashingService.hash("{\"key\":\"value\"}");
        assertNotNull(hash);
        assertEquals(128, hash.length(), "SHA-512 hex string must be exactly 128 characters");
        assertTrue(hash.matches("^[0-9a-f]{128}$"), "Hash must be lowercase hexadecimal");
    }

    @Test
    void toCanonicalJson_shouldOrderKeysAlphabetically() {
        Map<String, Object> unordered = new LinkedHashMap<>();
        unordered.put("zebra", 1);
        unordered.put("apple", 2);
        unordered.put("mango", 3);

        String canonical = hashingService.toCanonicalJson(unordered);
        assertEquals("{\"apple\":2,\"mango\":3,\"zebra\":1}", canonical);
    }

    @Test
    void computePayloadHash_differentKeyOrder_producesIdenticalHash() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("b", "beta");
        map1.put("a", "alpha");
        map1.put("c", "gamma");

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("c", "gamma");
        map2.put("b", "beta");
        map2.put("a", "alpha");

        String hash1 = hashingService.computePayloadHash(map1);
        String hash2 = hashingService.computePayloadHash(map2);

        assertEquals(hash1, hash2, "Key order must not affect canonical hash");
    }

    @Test
    void computePayloadHash_withBsonDocument_worksConsistently() {
        Document doc = new Document("database", "mongodb")
                .append("port", 27017)
                .append("host", "localhost");

        String hash = hashingService.computePayloadHash(doc);
        assertNotNull(hash);
        assertEquals(128, hash.length());

        assertTrue(hashingService.verifyPayloadHash(doc, hash));

        // Tamper with document
        doc.put("port", 27018);
        assertFalse(hashingService.verifyPayloadHash(doc, hash), "Tampered document must fail verification");
    }

    @Test
    void verifyPayloadHash_returnsFalseForMismatchedHash() {
        Map<String, Object> map = Map.of("key", "value");
        assertFalse(hashingService.verifyPayloadHash(map, "wrong-hash"));
        assertFalse(hashingService.verifyPayloadHash(map, null));
    }
}
