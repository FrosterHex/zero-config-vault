package com.vault.configmanager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service for deterministic canonical JSON serialization and SHA-512 integrity hashing.
 *
 * Guarantees cryptographic immutability verification:
 * - Hashes canonical JSON (alphabetically sorted keys) rather than BSON representation
 *   to avoid library-dependent BSON serialization inconsistencies.
 * - Computes standard 128-character lowercase hexadecimal SHA-512 fingerprints.
 */
@Service
public class HashingService {

    private static final String HASH_ALGORITHM = "SHA-512";

    private final ObjectMapper canonicalObjectMapper;

    public HashingService(ObjectMapper objectMapper) {
        // Create an ObjectMapper specifically tuned for deterministic canonical JSON
        this.canonicalObjectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    /**
     * Converts any payload (org.bson.Document, Map, DTO, JsonNode) into a sorted,
     * deterministic canonical JSON string.
     *
     * @param payload the configuration payload
     * @return canonical JSON string with sorted keys
     */
    public String toCanonicalJson(Object payload) {
        if (payload == null) {
            return "{}";
        }

        try {
            // Convert to intermediate tree to ensure all nested object structures normalize
            JsonNode tree = canonicalObjectMapper.valueToTree(payload);
            return canonicalObjectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize payload to canonical JSON", e);
        }
    }

    /**
     * Computes the SHA-512 hash of a canonical JSON string.
     *
     * @param canonicalJson the canonical JSON string
     * @return 128-character lowercase hex digest string
     */
    public String hash(String canonicalJson) {
        if (canonicalJson == null) {
            canonicalJson = "{}";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 algorithm unavailable", e);
        }
    }

    /**
     * Directly serializes a payload to canonical JSON and generates its SHA-512 fingerprint.
     *
     * @param payload the configuration payload
     * @return 128-character lowercase hex digest string
     */
    public String computePayloadHash(Object payload) {
        String canonicalJson = toCanonicalJson(payload);
        return hash(canonicalJson);
    }

    /**
     * Verifies if a given payload matches an expected SHA-512 hash fingerprint.
     *
     * @param payload the configuration payload
     * @param expectedHash the expected SHA-512 hash
     * @return true if calculated hash matches expected hash, false otherwise
     */
    public boolean verifyPayloadHash(Object payload, String expectedHash) {
        if (expectedHash == null) {
            return false;
        }
        String calculatedHash = computePayloadHash(payload);
        return calculatedHash.equalsIgnoreCase(expectedHash.trim());
    }

    /**
     * Computes the SHA-512 hash of a raw binary byte array.
     */
    public String hashBytes(byte[] bytes) {
        if (bytes == null) {
            bytes = new byte[0];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(bytes);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 algorithm unavailable", e);
        }
    }

    /**
     * Verifies if a given binary byte array matches an expected SHA-512 hash fingerprint.
     */
    public boolean verifyBytesHash(byte[] bytes, String expectedHash) {
        if (expectedHash == null) {
            return false;
        }
        String calculatedHash = hashBytes(bytes);
        return calculatedHash.equalsIgnoreCase(expectedHash.trim());
    }

    /**
     * Converts a byte array to lowercase hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
