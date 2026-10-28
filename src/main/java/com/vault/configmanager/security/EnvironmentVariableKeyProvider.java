package com.vault.configmanager.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * KeyProvider implementation that reads the master encryption key from
 * the environment variable VAULT_MASTER_KEY (injected via application.yml).
 *
 * Outside dev mode the application refuses to start with a missing, short, or
 * publicly known master key.
 *
 * This separation of concerns allows future migration to AWS KMS, HashiCorp Vault,
 * or a hardware security module without modifying any crypto logic.
 */
@Component
public class EnvironmentVariableKeyProvider implements KeyProvider {

    /** The development key committed to this repository; never acceptable outside dev mode. */
    static final String PUBLISHED_DEV_KEY = "0123456789abcdef0123456789abcdef";
    static final int MIN_KEY_LENGTH = 32;

    private final String masterKey;

    public EnvironmentVariableKeyProvider(@Value("${vault.master.key:}") String masterKey,
                                          @Value("${vault.dev-mode:false}") boolean devMode) {
        if (!devMode) {
            if (masterKey == null || masterKey.length() < MIN_KEY_LENGTH) {
                throw new IllegalStateException("VAULT_MASTER_KEY must be set to at least " + MIN_KEY_LENGTH
                        + " characters (or enable the 'dev' profile for local development).");
            }
            if (PUBLISHED_DEV_KEY.equals(masterKey)) {
                throw new IllegalStateException("VAULT_MASTER_KEY is the published development key. "
                        + "Generate a new key (e.g. 'openssl rand -base64 48') or enable the 'dev' profile.");
            }
        }
        this.masterKey = masterKey;
    }

    @Override
    public String getMasterKey() {
        return masterKey;
    }
}
