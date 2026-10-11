package com.vault.configmanager.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for bulk importing multiple configurations at once.
 */
public class BulkConfigRequest {

    @NotEmpty(message = "configs list must not be empty")
    @Valid
    private List<ConfigRequest> configs;

    public BulkConfigRequest() {
    }

    public BulkConfigRequest(List<ConfigRequest> configs) {
        this.configs = configs;
    }

    public List<ConfigRequest> getConfigs() {
        return configs;
    }

    public void setConfigs(List<ConfigRequest> configs) {
        this.configs = configs;
    }

    @Override
    public String toString() {
        return "BulkConfigRequest{" +
                "configCount=" + (configs != null ? configs.size() : 0) +
                '}';
    }
}
