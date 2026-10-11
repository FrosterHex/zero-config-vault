package com.vault.configmanager.security;

/**
 * Abstraction for providing the master encryption key.
 * Allows plugging in different key sources (environment variables, KMS, HSM)
 * without modifying the crypto service.
 */
public interface KeyProvider {

    /**
     * Returns the master encryption key as a string.
     *
     * @return the master key
     */
    String getMasterKey();
}
