package com.vault.configmanager.exception;

public class IntegrityViolationException extends RuntimeException {
    public IntegrityViolationException(String message) {
        super(message);
    }

    public IntegrityViolationException(String app, String env, int version) {
        super("Document integrity check failed for app '" + app + "', env '" + env +
                "', version " + version + ". Calculated SHA-512 fingerprint does not match stored hash. Possible tampering detected.");
    }
}
