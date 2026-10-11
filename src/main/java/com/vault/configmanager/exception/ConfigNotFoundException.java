package com.vault.configmanager.exception;

public class ConfigNotFoundException extends RuntimeException {
    public ConfigNotFoundException(String message) {
        super(message);
    }

    public ConfigNotFoundException(String app, String env) {
        super("Configuration not found for application '" + app + "' and environment '" + env + "'.");
    }

    public ConfigNotFoundException(String app, String env, int version) {
        super("Configuration not found for application '" + app + "', environment '" + env + "', version " + version + ".");
    }
}
