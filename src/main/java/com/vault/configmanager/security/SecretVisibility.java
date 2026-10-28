package com.vault.configmanager.security;

import com.vault.configmanager.model.ApiKey;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides whether the authenticated caller may see secret values in plaintext.
 */
public final class SecretVisibility {

    private SecretVisibility() {
    }

    /**
     * True only when the request was authenticated with a key holding REVEAL_SECRETS (ADMIN implies it).
     */
    public static boolean canReveal(HttpServletRequest request) {
        Object key = request.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_KEY_ATTR);
        return key instanceof ApiKey && ((ApiKey) key).hasPermission("REVEAL_SECRETS");
    }
}
