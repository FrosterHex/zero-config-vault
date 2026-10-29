package com.vault.configmanager.config;

import com.vault.configmanager.service.VersionRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Applies the version retention rule once at startup so data written by earlier builds
 * (which dated every superseded version) is brought in line with the current policy.
 * Idempotent; failures are logged and never block startup.
 */
@Component
public class RetentionStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetentionStartupRunner.class);

    private final VersionRetentionService versionRetentionService;

    public RetentionStartupRunner(VersionRetentionService versionRetentionService) {
        this.versionRetentionService = versionRetentionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int pairs = versionRetentionService.applyRetentionToAll();
            log.info("Version retention applied to {} app/environment pairs", pairs);
        } catch (Exception e) {
            log.warn("Version retention at startup failed: {}", e.getMessage());
        }
    }
}
