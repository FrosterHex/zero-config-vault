package com.vault.configmanager.model;

/**
 * Granular permissions and roles supported by Scoped API keys.
 */
public enum ApiKeyRole {
    READ,
    WRITE,
    ROLLBACK,
    EXPORT,
    REVEAL_SECRETS,
    ADMIN
}
