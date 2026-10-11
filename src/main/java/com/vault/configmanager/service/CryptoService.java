package com.vault.configmanager.service;

import com.vault.configmanager.dto.EncryptedNodeDTO;
import com.vault.configmanager.security.KeyProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic engine service implementing AES-256-GCM Envelope Encryption
 * adhering strictly to NIST SP 800-38D guidelines:
 *
 * Envelope Encryption Architecture:
 * - Each configuration secret is encrypted with a unique ephemeral 256-bit Data Encryption Key (DEK).
 * - The DEK is encrypted using the Master Key Encryption Key (KEK) provided by KeyProvider.
 * - Fluid Key Rotation: Rotating the Master KEK only requires re-encrypting the 32-byte DEKs,
 *   never touching or re-encrypting the underlying data payloads.
 * - 12-byte (96-bit) cryptographically random IV per encryption operation.
 * - 128-bit authentication tag length.
 */
@Service
public class CryptoService {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENCRYPTION_ALGO_NAME = "AES-256-GCM";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int DEK_LENGTH_BYTES = 32; // 256 bits

    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom;

    public CryptoService(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext string using Envelope Encryption.
     * Generates an ephemeral 256-bit DEK, encrypts the payload with the DEK,
     * and encrypts the DEK with the Master KEK.
     */
    public EncryptedNodeDTO encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            // 1. Generate an ephemeral 256-bit Data Encryption Key (DEK)
            byte[] dekBytes = new byte[DEK_LENGTH_BYTES];
            secureRandom.nextBytes(dekBytes);
            SecretKey dek = new SecretKeySpec(dekBytes, ALGORITHM);

            // 2. Encrypt plaintext with ephemeral DEK
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);

            byte[] cipherTextBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherTextBytes);

            // 3. Encrypt the DEK with Master Key Encryption Key (KEK)
            String encryptedDekBase64 = encryptDekWithKek(dekBytes, getSecretKey());

            return new EncryptedNodeDTO(ENCRYPTION_ALGO_NAME, ivBase64, cipherTextBase64, encryptedDekBase64);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt data using AES-256-GCM envelope encryption", e);
        }
    }

    /**
     * Decrypts an EncryptedNodeDTO.
     * If the node is Envelope Encrypted, unwraps the ephemeral DEK via the Master KEK first.
     * Otherwise, falls back to direct Master Key decryption for backward compatibility.
     */
    public String decrypt(EncryptedNodeDTO node) {
        if (node == null) {
            return null;
        }

        if (node.isEnvelopeEncrypted()) {
            return decryptWithEnvelope(node.getIv(), node.getCipherText(), node.getEncryptedDek());
        }

        return decrypt(node.getIv(), node.getCipherText());
    }

    /**
     * Decrypts payload using Envelope Encryption by unwrapping the DEK first.
     */
    public String decryptWithEnvelope(String ivBase64, String cipherTextBase64, String encryptedDekBase64) {
        if (ivBase64 == null || cipherTextBase64 == null || encryptedDekBase64 == null) {
            return null;
        }

        try {
            // 1. Decrypt DEK using Master KEK
            byte[] dekBytes = decryptDekWithKek(encryptedDekBase64, getSecretKey());
            SecretKey dek = new SecretKeySpec(dekBytes, ALGORITHM);

            // 2. Decrypt payload using decrypted DEK
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] cipherTextBytes = Base64.getDecoder().decode(cipherTextBase64);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, dek, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherTextBytes);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt envelope ciphertext. Possible tampering or incorrect key.", e);
        }
    }

    /**
     * Direct AES-256-GCM decryption using the Master Key.
     * Maintained for direct key usage and backward compatibility.
     */
    public String decrypt(String ivBase64, String cipherTextBase64) {
        if (ivBase64 == null || cipherTextBase64 == null) {
            return null;
        }

        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] cipherTextBytes = Base64.getDecoder().decode(cipherTextBase64);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherTextBytes);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt AES-256-GCM ciphertext. Possible tampering or incorrect key.", e);
        }
    }

    /**
     * Zero-Downtime Key Rotation:
     * Unwraps the DEK using the old Master KEK and re-wraps it with the new Master KEK.
     * The underlying ciphertext payload remains completely untouched.
     */
    public String reEncryptDek(String encryptedDekBase64, String oldMasterKey, String newMasterKey) {
        if (encryptedDekBase64 == null || oldMasterKey == null || newMasterKey == null) {
            throw new IllegalArgumentException("Parameters cannot be null for key rotation");
        }

        try {
            SecretKey oldKek = deriveSecretKey(oldMasterKey);
            SecretKey newKek = deriveSecretKey(newMasterKey);

            byte[] rawDek = decryptDekWithKek(encryptedDekBase64, oldKek);
            return encryptDekWithKek(rawDek, newKek);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to re-encrypt DEK during key rotation", e);
        }
    }

    private String encryptDekWithKek(byte[] dekBytes, SecretKey kek) throws Exception {
        byte[] dekIv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(dekIv);

        Cipher kekCipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec kekParamSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, dekIv);
        kekCipher.init(Cipher.ENCRYPT_MODE, kek, kekParamSpec);

        byte[] encryptedDek = kekCipher.doFinal(dekBytes);

        // Prepend IV to encrypted DEK: [12-byte IV] + [encrypted DEK]
        byte[] combined = new byte[GCM_IV_LENGTH_BYTES + encryptedDek.length];
        System.arraycopy(dekIv, 0, combined, 0, GCM_IV_LENGTH_BYTES);
        System.arraycopy(encryptedDek, 0, combined, GCM_IV_LENGTH_BYTES, encryptedDek.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    private byte[] decryptDekWithKek(String encryptedDekBase64, SecretKey kek) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedDekBase64);
        if (combined.length <= GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Encrypted DEK payload too short");
        }

        byte[] dekIv = new byte[GCM_IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, dekIv, 0, GCM_IV_LENGTH_BYTES);

        int cipherLen = combined.length - GCM_IV_LENGTH_BYTES;
        byte[] encryptedDekBytes = new byte[cipherLen];
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, encryptedDekBytes, 0, cipherLen);

        Cipher kekCipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec kekParamSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, dekIv);
        kekCipher.init(Cipher.DECRYPT_MODE, kek, kekParamSpec);

        return kekCipher.doFinal(encryptedDekBytes);
    }

    /**
     * Derives a consistent 256-bit AES SecretKey from the Master Key provided by KeyProvider.
     */
    private SecretKey getSecretKey() {
        String masterKey = keyProvider.getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("Master encryption key is not configured in KeyProvider");
        }
        return deriveSecretKey(masterKey);
    }

    private SecretKey deriveSecretKey(String keyString) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(keyString.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }
}
