package com.vault.configmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MongoDB and Jackson configuration.
 * Sets up MongoTemplate, MongoTransactionManager for multi-document ACID transactions,
 * configures capped collections, and provides pre-configured ObjectMapper beans for JSON and YAML serialization.
 */
@Configuration
public class MongoConfig {

    private final MongoClient mongoClient;

    @Value("${spring.data.mongodb.database:secure_config_vault}")
    private String databaseName;

    @Value("${vault.audit.cap-size-mb:50}")
    private int auditCapSizeMb;

    @Value("${vault.audit.max-documents:10000}")
    private int auditMaxDocuments;

    public MongoConfig(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDbFactory,
                                        MappingMongoConverter converter) {
        return new MongoTemplate(mongoDbFactory, converter);
    }

    /**
     * Configures Spring's MongoTransactionManager to enable multi-document ACID transactions
     * across MongoDB replica sets.
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory mongoDbFactory) {
        return new MongoTransactionManager(mongoDbFactory);
    }

    /**
     * Configures dedicated GridFsTemplate for binary configuration payloads (TLS certs, .jks stores, .pem bundles).
     * Maps to the 'vault_binaries' GridFS bucket.
     */
    @Bean
    public GridFsTemplate gridFsTemplate(MongoDatabaseFactory mongoDbFactory,
                                         MappingMongoConverter converter) {
        return new GridFsTemplate(mongoDbFactory, converter, "vault_binaries");
    }

    /**
     * Initialize the audit_logs capped collection if it does not already exist.
     * Capped collections have a fixed size and auto-evict the oldest documents.
     */
    @PostConstruct
    public void initMongoAdvancedFeatures() {
        try {
            MongoDatabase db = mongoClient.getDatabase(databaseName);
            Set<String> collections = new HashSet<>();
            for (String name : db.listCollectionNames()) {
                collections.add(name);
            }

            // 1. Initialize Capped Collection for audit_logs
            if (!collections.contains("audit_logs")) {
                db.createCollection("audit_logs",
                        new com.mongodb.client.model.CreateCollectionOptions()
                                .capped(true)
                                .sizeInBytes((long) auditCapSizeMb * 1024 * 1024)
                                .maxDocuments(auditMaxDocuments));
            }

            // 2. Initialize Native Time-Series Collection for config_access_telemetry
            if (!collections.contains("config_access_telemetry")) {
                try {
                    com.mongodb.client.model.TimeSeriesOptions tsOptions =
                            new com.mongodb.client.model.TimeSeriesOptions("timestamp")
                                    .metaField("metadata")
                                    .granularity(com.mongodb.client.model.TimeSeriesGranularity.SECONDS);
                    db.createCollection("config_access_telemetry",
                            new com.mongodb.client.model.CreateCollectionOptions().timeSeriesOptions(tsOptions));
                } catch (Exception e) {
                    // Fallback to standard collection if running on standalone or older Mongo versions
                    if (!collections.contains("config_access_telemetry")) {
                        db.createCollection("config_access_telemetry");
                    }
                }
            }

            // 3. Apply Engine-Level $jsonSchema Validation on configurations collection
            try {
                Document schemaValidator = buildConfigurationJsonSchema();
                if (collections.contains("configurations")) {
                    Document collMod = new Document("collMod", "configurations")
                            .append("validator", schemaValidator)
                            .append("validationLevel", "moderate")
                            .append("validationAction", "error");
                    db.runCommand(collMod);
                } else {
                    com.mongodb.client.model.ValidationOptions vOptions =
                            new com.mongodb.client.model.ValidationOptions()
                                    .validator(schemaValidator)
                                    .validationLevel(com.mongodb.client.model.ValidationLevel.MODERATE)
                                    .validationAction(com.mongodb.client.model.ValidationAction.ERROR);
                    db.createCollection("configurations",
                            new com.mongodb.client.model.CreateCollectionOptions().validationOptions(vOptions));
                }
            } catch (Exception e) {
                // Non-blocking schema validation registration
            }

            // 4. Initialize Full-Text Search Index on configurations
            try {
                if (collections.contains("configurations")) {
                    db.getCollection("configurations").createIndex(
                            new Document("applicationName", "text")
                                    .append("searchableText", "text")
                                    .append("environment", "text"),
                            new com.mongodb.client.model.IndexOptions().name("idx_configurations_text_search")
                    );
                }
            } catch (Exception e) {
                // Non-blocking text index creation
            }

        } catch (Exception e) {
            // Allows build/tests and offline startup without blocking
        }
    }

    /**
     * Builds the MongoDB $jsonSchema Document enforcing database-level integrity.
     */
    public static Document buildConfigurationJsonSchema() {
        return new Document("$jsonSchema", new Document()
                .append("bsonType", "object")
                .append("required", List.of("applicationName", "environment", "version", "documentHash", "payload", "createdAt"))
                .append("properties", new Document()
                        .append("applicationName", new Document("bsonType", "string")
                                .append("pattern", "^[a-zA-Z0-9_-]+$")
                                .append("description", "Application name slug"))
                        .append("environment", new Document("enum", List.of("development", "staging", "production", "dr"))
                                .append("description", "Valid environment tier"))
                        .append("version", new Document("bsonType", "int")
                                .append("minimum", 1)
                                .append("description", "Monotonically increasing version integer"))
                        .append("documentHash", new Document("bsonType", "string")
                                .append("minLength", 64)
                                .append("maxLength", 128)
                                .append("description", "Cryptographic SHA-512 canonical hash fingerprint"))
                        .append("payload", new Document("bsonType", "object")
                                .append("description", "Key-value JSON configuration tree"))
                )
        );
    }

    /**
     * Primary ObjectMapper for JSON operations.
     * Configured with ordered map serialization for deterministic canonical output.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }

    /**
     * ObjectMapper configured with YAML factory for YAML export operations.
     */
    @Bean("yamlObjectMapper")
    public ObjectMapper yamlObjectMapper() {
        return new ObjectMapper(new YAMLFactory());
    }
}
