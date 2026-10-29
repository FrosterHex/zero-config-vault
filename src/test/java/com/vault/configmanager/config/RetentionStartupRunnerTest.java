package com.vault.configmanager.config;

import com.mongodb.client.MongoDatabase;
import com.vault.configmanager.service.VersionRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionStartupRunnerTest {

    private VersionRetentionService retention;
    private MongoConfig mongoConfig;
    private MongoDatabase database;
    private RetentionStartupRunner runner;

    @BeforeEach
    void setUp() {
        retention = mock(VersionRetentionService.class);
        mongoConfig = mock(MongoConfig.class);
        database = mock(MongoDatabase.class);
        MongoTemplate template = mock(MongoTemplate.class);
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        when(template.getMongoDatabaseFactory()).thenReturn(factory);
        when(factory.getMongoDatabase()).thenReturn(database);
        runner = new RetentionStartupRunner(retention, mongoConfig, template);
    }

    @Test
    void run_retentionFails_ttlIndexLeftUntouched() {
        when(retention.applyRetentionToAll()).thenThrow(new IllegalStateException("boom"));

        runner.run(null);

        verify(mongoConfig, never()).ensureExpiryTtlIndex(any());
    }

    @Test
    void run_retentionSucceeds_ttlIndexEnsured() {
        when(retention.applyRetentionToAll()).thenReturn(2);

        runner.run(null);

        verify(mongoConfig).ensureExpiryTtlIndex(database);
    }
}
