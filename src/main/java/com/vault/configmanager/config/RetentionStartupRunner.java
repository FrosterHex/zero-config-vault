package com.vault.configmanager.config;

import com.vault.configmanager.service.VersionRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies the version retention rule once at startup so data written by earlier builds
 * (which dated every superseded version) is brought in line with the current policy.
 * The exact-date TTL index is switched on only after that pass succeeds (a failed pass leaves the old index for the next boot), so a version that is still protected
 * but carries an old, already-past expiresAt is cleared before MongoDB can delete it.
 * Idempotent; failures are logged and never block startup.
 */
@Component
public class RetentionStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetentionStartupRunner.class);

    private final VersionRetentionService versionRetentionService;

    private final MongoConfig mongoConfig;
    private final MongoTemplate mongoTemplate;

    public RetentionStartupRunner(VersionRetentionService versionRetentionService,
                                  MongoConfig mongoConfig, MongoTemplate mongoTemplate) {
        this.versionRetentionService = versionRetentionService;
        this.mongoConfig = mongoConfig;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int pairs = versionRetentionService.applyRetentionToAll();
            log.info("Version retention applied to {} app/environment pairs", pairs);
        } catch (Exception e) {
            log.warn("Version retention at startup failed, leaving the existing expiresAt index unchanged: {}",
                    e.getMessage());
            return;
        }
        mongoConfig.ensureExpiryTtlIndex(mongoTemplate.getMongoDatabaseFactory().getMongoDatabase());
    }
}
