package com.vault.configmanager.service;

import com.vault.configmanager.dto.EncryptedNodeDTO;
import com.vault.configmanager.security.KeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CryptoServiceTest {

    private CryptoService cryptoService;
    private KeyProvider keyProvider;
    private static final String MASTER_KEY = "test-master-secret-key-32-bytes!";

    @BeforeEach
    void setUp() {
        keyProvider = mock(KeyProvider.class);
        when(keyProvider.getMasterKey()).thenReturn(MASTER_KEY);
        cryptoService = new CryptoService(keyProvider);
    }

    @Test
    void encrypt_shouldProduceValidEnvelopeEncryptedNodeDTO() {
        String plainText = "super-secret-password-12345";
        EncryptedNodeDTO dto = cryptoService.encrypt(plainText);

        assertNotNull(dto);
        assertEquals("AES-256-GCM", dto.getEncryptionAlgo());
        assertNotNull(dto.getIv());
        assertNotNull(dto.getCipherText());
        assertNotNull(dto.getEncryptedDek());
        assertTrue(dto.isEnvelopeEncrypted());

        // GCM IV is 12 bytes -> in Base64 it is 16 chars
        byte[] ivBytes = Base64.getDecoder().decode(dto.getIv());
        assertEquals(12, ivBytes.length);

        assertNotEquals(plainText, dto.getCipherText());
    }

    @Test
    void encrypt_shouldProduceDifferentIvAndCiphertextForSamePlaintext() {
        String plainText = "identical-secret-value";
        EncryptedNodeDTO dto1 = cryptoService.encrypt(plainText);
        EncryptedNodeDTO dto2 = cryptoService.encrypt(plainText);

        assertNotEquals(dto1.getIv(), dto2.getIv(), "IV must be uniquely generated for every encryption");
        assertNotEquals(dto1.getCipherText(), dto2.getCipherText(), "Ciphertext must differ due to unique IV");
        assertNotEquals(dto1.getEncryptedDek(), dto2.getEncryptedDek(), "Encrypted DEKs must differ due to unique DEK and IV");
    }

    @Test
    void decrypt_shouldRestoreOriginalPlaintextViaEnvelope() {
        String original = "database://user:pass@localhost:5432/production?ssl=true";
        EncryptedNodeDTO dto = cryptoService.encrypt(original);

        String decrypted = cryptoService.decrypt(dto);
        assertEquals(original, decrypted);
    }

    @Test
    void decrypt_legacyNodeWithoutEnvelope_decryptsDirectly() {
        // Direct AES-256-GCM encryption without DEK envelope (legacy backward compatibility)
        String plainText = "legacy-vault-secret";
        // Encrypt directly by calling decrypt with direct method
        EncryptedNodeDTO envelopeDto = cryptoService.encrypt(plainText);

        // Create legacy DTO without encryptedDek
        EncryptedNodeDTO legacyDto = new EncryptedNodeDTO(
                envelopeDto.getEncryptionAlgo(),
                envelopeDto.getIv(),
                envelopeDto.getCipherText()
        );
        assertFalse(legacyDto.isEnvelopeEncrypted());
        assertNull(legacyDto.getEncryptedDek());
    }

    @Test
    void reEncryptDek_enablesZeroDowntimeMasterKeyRotation() {
        String plainText = "top-secret-api-token";
        EncryptedNodeDTO originalDto = cryptoService.encrypt(plainText);

        String oldMasterKey = MASTER_KEY;
        String newMasterKey = "new-rotated-master-key-32-bytes!!";

        // Re-encrypt DEK with new master key
        String rotatedDek = cryptoService.reEncryptDek(originalDto.getEncryptedDek(), oldMasterKey, newMasterKey);
        assertNotNull(rotatedDek);
        assertNotEquals(originalDto.getEncryptedDek(), rotatedDek);

        // New service instance with new master key
        KeyProvider newKeyProvider = mock(KeyProvider.class);
        when(newKeyProvider.getMasterKey()).thenReturn(newMasterKey);
        CryptoService newCryptoService = new CryptoService(newKeyProvider);

        // Decrypt using new rotated DEK and untouched ciphertext
        EncryptedNodeDTO rotatedDto = new EncryptedNodeDTO(
                originalDto.getEncryptionAlgo(),
                originalDto.getIv(),
                originalDto.getCipherText(),
                rotatedDek
        );

        String decrypted = newCryptoService.decrypt(rotatedDto);
        assertEquals(plainText, decrypted);
    }

    @Test
    void decrypt_withTamperedCiphertext_shouldThrowException() {
        EncryptedNodeDTO dto = cryptoService.encrypt("critical-secret");

        byte[] rawCipherBytes = Base64.getDecoder().decode(dto.getCipherText());
        rawCipherBytes[0] ^= (byte) 0xFF; // Flip bits in ciphertext
        String tamperedCipher = Base64.getEncoder().encodeToString(rawCipherBytes);

        EncryptedNodeDTO tamperedDto = new EncryptedNodeDTO(
                dto.getEncryptionAlgo(),
                dto.getIv(),
                tamperedCipher,
                dto.getEncryptedDek()
        );

        assertThrows(IllegalStateException.class, () ->
                cryptoService.decrypt(tamperedDto)
        );
    }

    @Test
    void decrypt_withTamperedIv_shouldThrowException() {
        EncryptedNodeDTO dto = cryptoService.encrypt("critical-secret");

        byte[] rawIvBytes = Base64.getDecoder().decode(dto.getIv());
        rawIvBytes[0] ^= (byte) 0xFF; // Flip bits in IV
        String tamperedIv = Base64.getEncoder().encodeToString(rawIvBytes);

        EncryptedNodeDTO tamperedDto = new EncryptedNodeDTO(
                dto.getEncryptionAlgo(),
                tamperedIv,
                dto.getCipherText(),
                dto.getEncryptedDek()
        );

        assertThrows(IllegalStateException.class, () ->
                cryptoService.decrypt(tamperedDto)
        );
    }

    @Test
    void encryptAndDecrypt_nullHandling() {
        assertNull(cryptoService.encrypt(null));
        assertNull(cryptoService.decrypt(null, null));
        assertNull(cryptoService.decrypt((EncryptedNodeDTO) null));
        assertNull(cryptoService.decryptWithEnvelope(null, null, null));
    }
}
