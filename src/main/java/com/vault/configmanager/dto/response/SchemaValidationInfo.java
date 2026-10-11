package com.vault.configmanager.dto.response;

import java.util.List;
import java.util.Map;

/**
 * DTO providing active MongoDB $jsonSchema engine-level validation details.
 */
public class SchemaValidationInfo {

    private String targetCollection;
    private boolean validationEnabled;
    private String validationLevel;
    private String validationAction;
    private List<String> requiredFields;
    private Map<String, Object> fieldRules;
    private String rawJsonSchema;

    public SchemaValidationInfo() {
    }

    public SchemaValidationInfo(String targetCollection, boolean validationEnabled,
                                String validationLevel, String validationAction,
                                List<String> requiredFields, Map<String, Object> fieldRules,
                                String rawJsonSchema) {
        this.targetCollection = targetCollection;
        this.validationEnabled = validationEnabled;
        this.validationLevel = validationLevel;
        this.validationAction = validationAction;
        this.requiredFields = requiredFields;
        this.fieldRules = fieldRules;
        this.rawJsonSchema = rawJsonSchema;
    }

    public String getTargetCollection() {
        return targetCollection;
    }

    public void setTargetCollection(String targetCollection) {
        this.targetCollection = targetCollection;
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    public String getValidationLevel() {
        return validationLevel;
    }

    public void setValidationLevel(String validationLevel) {
        this.validationLevel = validationLevel;
    }

    public String getValidationAction() {
        return validationAction;
    }

    public void setValidationAction(String validationAction) {
        this.validationAction = validationAction;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public Map<String, Object> getFieldRules() {
        return fieldRules;
    }

    public void setFieldRules(Map<String, Object> fieldRules) {
        this.fieldRules = fieldRules;
    }

    public String getRawJsonSchema() {
        return rawJsonSchema;
    }

    public void setRawJsonSchema(String rawJsonSchema) {
        this.rawJsonSchema = rawJsonSchema;
    }
}
