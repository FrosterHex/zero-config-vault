package com.vault.configmanager.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when an atomic bulk configuration import fails,
 * triggering an immediate rollback across the MongoDB replica set transaction.
 */
public class BulkTransactionException extends RuntimeException {

    private final List<Map<String, Object>> errors;

    public BulkTransactionException(String message) {
        super(message);
        this.errors = new ArrayList<>();
    }

    public BulkTransactionException(String message, List<Map<String, Object>> errors) {
        super(message);
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}
