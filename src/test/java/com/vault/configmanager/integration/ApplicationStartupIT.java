package com.vault.configmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

class ApplicationStartupIT extends MongoIntegrationTest {

    @Test
    void contextStarts_againstReplicaSet() {
        Document hello = mongoTemplate.getDb().runCommand(new Document("hello", 1));

        assertThat(hello).containsKey("setName");
        assertThat(mongoTemplate.collectionExists("configurations")).isTrue();
    }

    @Test
    void unknownPath_returns404_andIsNotAuditedAsAnError() {
        String path = "/" + uniqueApp("no-such-page");

        HttpClientErrorException ex = catchThrowableOfType(
                () -> new RestTemplate().getForEntity("http://localhost:" + port + path, String.class),
                HttpClientErrorException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mongoTemplate.count(Query.query(Criteria.where("applicationName").is(path)), "audit_logs")).isZero();
    }
}
