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
    public void initCappedCollections() {
        try {
            MongoDatabase db = mongoClient.getDatabase(databaseName);
            boolean exists = false;
            for (String name : db.listCollectionNames()) {
                if ("audit_logs".equals(name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                db.createCollection("audit_logs",
                        new com.mongodb.client.model.CreateCollectionOptions()
                                .capped(true)
                                .sizeInBytes((long) auditCapSizeMb * 1024 * 1024)
                                .maxDocuments(auditMaxDocuments));
            }
        } catch (Exception e) {
            // Allows build/tests and offline startup without blocking
        }
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
