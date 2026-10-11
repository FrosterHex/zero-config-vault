package com.vault.configmanager.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * KeyProvider implementation that reads the master encryption key from
 * the environment variable VAULT_MASTER_KEY (injected via application.properties).
 *
 * This separation of concerns allows future migration to AWS KMS, HashiCorp Vault,
 * or a hardware security module without modifying any crypto logic.
 */
@Component
public class EnvironmentVariableKeyProvider implements KeyProvider {

    private final String masterKey;

    public EnvironmentVariableKeyProvider(@Value("${vault.master.key:0123456789abcdef0123456789abcdef}") String masterKey) {
        this.masterKey = masterKey;
    }

    @Override
    public String getMasterKey() {
        return masterKey;
    }
}
