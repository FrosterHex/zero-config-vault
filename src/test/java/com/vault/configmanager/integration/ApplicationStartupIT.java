package com.vault.configmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class ApplicationStartupIT extends MongoIntegrationTest {

    @Test
    void contextStarts_againstReplicaSet() {
        Document hello = mongoTemplate.getDb().runCommand(new Document("hello", 1));

        assertThat(hello).containsKey("setName");
        assertThat(mongoTemplate.collectionExists("configurations")).isTrue();
    }
}
