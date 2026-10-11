package com.vault.configmanager.dto.request;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for rolling back to a specific version.
 */
public class RollbackRequest {

    @Min(value = 1, message = "version must be >= 1")
    private int version;

    public RollbackRequest() {
    }

    public RollbackRequest(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "RollbackRequest{version=" + version + '}';
    }
}
