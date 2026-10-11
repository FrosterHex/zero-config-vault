package com.vault.configmanager.dto.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response DTO summarizing bulk configuration ingestion results.
 */
public class BulkConfigResponse {

    private int totalProcessed;
    private int succeeded;
    private int failed;
    private List<ConfigResponse> results;
    private List<Map<String, Object>> errors;

    public BulkConfigResponse() {
        this.results = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public BulkConfigResponse(int totalProcessed, int succeeded, int failed,
                              List<ConfigResponse> results, List<Map<String, Object>> errors) {
        this.totalProcessed = totalProcessed;
        this.succeeded = succeeded;
        this.failed = failed;
        this.results = results != null ? results : new ArrayList<>();
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }
    public int getSucceeded() { return succeeded; }
    public void setSucceeded(int succeeded) { this.succeeded = succeeded; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public List<ConfigResponse> getResults() { return results; }
    public void setResults(List<ConfigResponse> results) { this.results = results; }
    public List<Map<String, Object>> getErrors() { return errors; }
    public void setErrors(List<Map<String, Object>> errors) { this.errors = errors; }

    @Override
    public String toString() {
        return "BulkConfigResponse{" +
                "total=" + totalProcessed +
                ", succeeded=" + succeeded +
                ", failed=" + failed +
                '}';
    }
}
