package com.vault.configmanager.exception;

public class DuplicateVersionException extends RuntimeException {
    public DuplicateVersionException(String message) {
        super(message);
    }

    public DuplicateVersionException(String app, String env, int version) {
        super("A version conflict occurred for app '" + app + "', env '" + env + "', version " + version + ". Please retry.");
    }
}
