package com.vault.configmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing an encrypted node inside the configuration payload.
 * Stored in place of plaintext values under 'secure_settings' keys.
 * Supports cryptographic Envelope Encryption with unique Data Encryption Keys (DEKs)
 * and legacy direct AES-256-GCM.
 */
public class EncryptedNodeDTO {

    @JsonProperty("encryption_algo")
    private String encryptionAlgo;

    @JsonProperty("iv")
    private String iv;

    @JsonProperty("cipher_text")
    private String cipherText;

    @JsonProperty("encrypted_dek")
    private String encryptedDek;

    public EncryptedNodeDTO() {}

    public EncryptedNodeDTO(@JsonProperty("encryption_algo") String encryptionAlgo,
                            @JsonProperty("iv") String iv,
                            @JsonProperty("cipher_text") String cipherText) {
        this(encryptionAlgo, iv, cipherText, null);
    }

    public EncryptedNodeDTO(@JsonProperty("encryption_algo") String encryptionAlgo,
                            @JsonProperty("iv") String iv,
                            @JsonProperty("cipher_text") String cipherText,
                            @JsonProperty("encrypted_dek") String encryptedDek) {
        this.encryptionAlgo = encryptionAlgo;
        this.iv = iv;
        this.cipherText = cipherText;
        this.encryptedDek = encryptedDek;
    }

    @JsonProperty("encryption_algo")
    public String getEncryptionAlgo() { return encryptionAlgo; }

    @JsonProperty("encryption_algo")
    public void setEncryptionAlgo(String encryptionAlgo) { this.encryptionAlgo = encryptionAlgo; }

    @JsonProperty("iv")
    public String getIv() { return iv; }

    @JsonProperty("iv")
    public void setIv(String iv) { this.iv = iv; }

    @JsonProperty("cipher_text")
    public String getCipherText() { return cipherText; }

    @JsonProperty("cipher_text")
    public void setCipherText(String cipherText) { this.cipherText = cipherText; }

    @JsonProperty("encrypted_dek")
    public String getEncryptedDek() { return encryptedDek; }

    @JsonProperty("encrypted_dek")
    public void setEncryptedDek(String encryptedDek) { this.encryptedDek = encryptedDek; }

    public boolean isEnvelopeEncrypted() {
        return encryptedDek != null && !encryptedDek.isBlank();
    }

    @Override
    public String toString() {
        return "EncryptedNodeDTO{algo='" + encryptionAlgo + "', envelope=" + isEnvelopeEncrypted() + "}";
    }
}
