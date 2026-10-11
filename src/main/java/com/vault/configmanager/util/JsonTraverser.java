package com.vault.configmanager.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.vault.configmanager.dto.EncryptedNodeDTO;
import com.vault.configmanager.service.CryptoService;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

/**
 * Dynamic JSON Traversal Middleware for field-level encryption/decryption.
 *
 * Navigates arbitrary configuration trees without strict POJO mapping:
 * - Scans recursively for any key matching "secure_settings"
 * - In encryption mode: replaces each plaintext child with an EncryptedNodeDTO
 * - In decryption mode: extracts IV and ciphertext, decrypts with AES-256-GCM,
 *   and restores the plaintext child node
 */
@Component
public class JsonTraverser {

    public static final String SECURE_SETTINGS_KEY = "secure_settings";
    private static final String ENCRYPTION_ALGO_KEY = "encryption_algo";
    private static final String IV_KEY = "iv";
    private static final String CIPHERTEXT_KEY = "cipher_text";

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public JsonTraverser(CryptoService cryptoService, ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * Recursively traverses a JsonNode tree, encrypting all values located
     * under any "secure_settings" object node.
     *
     * @param root the incoming configuration tree
     * @return the transformed JsonNode tree with secure_settings encrypted
     */
    public JsonNode encrypt(JsonNode root) {
        if (root == null) {
            return null;
        }

        // Deep copy the node to avoid mutating the caller's reference
        JsonNode copy = root.deepCopy();
        traverseAndEncrypt(copy);
        return copy;
    }

    /**
     * Recursively traverses a JsonNode tree, decrypting all EncryptedNodeDTO
     * objects located under any "secure_settings" node.
     *
     * @param root the stored configuration tree
     * @return the transformed JsonNode tree with secure_settings decrypted
     */
    public JsonNode decrypt(JsonNode root) {
        if (root == null) {
            return null;
        }

        JsonNode copy = root.deepCopy();
        traverseAndDecrypt(copy);
        return copy;
    }

    /**
     * Encrypts secure_settings inside a BSON Document.
     *
     * @param document the BSON Document payload
     * @return new BSON Document with secure_settings encrypted
     */
    public Document encryptDocument(Document document) {
        if (document == null) {
            return new Document();
        }

        JsonNode node = objectMapper.valueToTree(document);
        JsonNode encrypted = encrypt(node);
        try {
            String json = objectMapper.writeValueAsString(encrypted);
            return Document.parse(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize encrypted document", e);
        }
    }

    /**
     * Decrypts secure_settings inside a BSON Document.
     *
     * @param document the BSON Document payload
     * @return new BSON Document with secure_settings decrypted
     */
    public Document decryptDocument(Document document) {
        if (document == null) {
            return new Document();
        }

        JsonNode node = objectMapper.valueToTree(document);
        JsonNode decrypted = decrypt(node);
        try {
            String json = objectMapper.writeValueAsString(decrypted);
            return Document.parse(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize decrypted document", e);
        }
    }

    /**
     * Encrypts secure_settings inside a standard Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> encryptMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(map);
        JsonNode encrypted = encrypt(node);
        return objectMapper.convertValue(encrypted, Map.class);
    }

    /**
     * Decrypts secure_settings inside a standard Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decryptMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(map);
        JsonNode decrypted = decrypt(node);
        return objectMapper.convertValue(decrypted, Map.class);
    }

    // --- Private Traversal Logic ---

    private void traverseAndEncrypt(JsonNode node) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode child = field.getValue();

                if (SECURE_SETTINGS_KEY.equals(fieldName) && child.isObject()) {
                    encryptSecureSettingsNode((ObjectNode) child);
                } else {
                    traverseAndEncrypt(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode element : arrayNode) {
                traverseAndEncrypt(element);
            }
        }
    }

    private void encryptSecureSettingsNode(ObjectNode secureSettingsNode) {
        Iterator<Map.Entry<String, JsonNode>> children = secureSettingsNode.fields();
        // Collect fields to update to avoid ConcurrentModificationException while iterating
        java.util.List<String> fieldNames = new java.util.ArrayList<>();
        while (children.hasNext()) {
            fieldNames.add(children.next().getKey());
        }

        for (String key : fieldNames) {
            JsonNode childValue = secureSettingsNode.get(key);

            // Avoid double encryption if the node is already an EncryptedNodeDTO
            if (isAlreadyEncrypted(childValue)) {
                continue;
            }

            String plainText = extractPlainText(childValue);
            EncryptedNodeDTO encrypted = cryptoService.encrypt(plainText);
            JsonNode encryptedJsonNode = objectMapper.valueToTree(encrypted);
            secureSettingsNode.set(key, encryptedJsonNode);
        }
    }

    private void traverseAndDecrypt(JsonNode node) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode child = field.getValue();

                if (SECURE_SETTINGS_KEY.equals(fieldName) && child.isObject()) {
                    decryptSecureSettingsNode((ObjectNode) child);
                } else {
                    traverseAndDecrypt(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode element : arrayNode) {
                traverseAndDecrypt(element);
            }
        }
    }

    private void decryptSecureSettingsNode(ObjectNode secureSettingsNode) {
        Iterator<Map.Entry<String, JsonNode>> children = secureSettingsNode.fields();
        java.util.List<String> fieldNames = new java.util.ArrayList<>();
        while (children.hasNext()) {
            fieldNames.add(children.next().getKey());
        }

        for (String key : fieldNames) {
            JsonNode childValue = secureSettingsNode.get(key);

            if (isAlreadyEncrypted(childValue)) {
                String iv = childValue.has(IV_KEY) ? childValue.get(IV_KEY).asText() : childValue.get("iv").asText();
                String cipherText = childValue.has(CIPHERTEXT_KEY)
                        ? childValue.get(CIPHERTEXT_KEY).asText()
                        : childValue.get("cipherText").asText();

                String encryptedDek = null;
                if (childValue.has("encrypted_dek") && !childValue.get("encrypted_dek").isNull()) {
                    encryptedDek = childValue.get("encrypted_dek").asText();
                } else if (childValue.has("encryptedDek") && !childValue.get("encryptedDek").isNull()) {
                    encryptedDek = childValue.get("encryptedDek").asText();
                }

                String plainText;
                if (encryptedDek != null && !encryptedDek.isBlank() && !encryptedDek.equals("null")) {
                    plainText = cryptoService.decryptWithEnvelope(iv, cipherText, encryptedDek);
                } else {
                    plainText = cryptoService.decrypt(iv, cipherText);
                }

                // Restore as TextNode
                secureSettingsNode.set(key, new TextNode(plainText));
            }
        }
    }

    private boolean isAlreadyEncrypted(JsonNode node) {
        if (node != null && node.isObject()) {
            boolean hasIv = node.has(IV_KEY) || node.has("iv");
            boolean hasCipher = node.has(CIPHERTEXT_KEY) || node.has("cipherText");
            boolean hasAlgo = node.has(ENCRYPTION_ALGO_KEY) || node.has("encryptionAlgo");
            return hasIv && hasCipher && hasAlgo;
        }
        return false;
    }

    private String extractPlainText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        // If it's a number, boolean, or complex object, serialize to string
        return node.asText().isEmpty() ? node.toString() : node.asText();
    }
}
