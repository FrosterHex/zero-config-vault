package com.vault.configmanager.exception;

/**
 * Thrown when an authenticated API key lacks the role or app/environment scope
 * required for an operation whose target is only known after the request body is parsed.
 */
public class ScopeViolationException extends RuntimeException {
    public ScopeViolationException(String message) {
        super(message);
    }

    public ScopeViolationException(String requiredRole, String app, String env) {
        super("API key unauthorized for role '" + requiredRole + "' on app='" + app + "', env='" + env + "'");
    }
}
