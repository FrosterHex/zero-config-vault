package com.vault.configmanager.config;

import com.vault.configmanager.model.CachedConfig;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Startup remediation for data written by earlier builds that leaked plaintext secrets:
 * - 'searchableText' on configurations used to include decrypted "secure_settings" values.
 *   It is recomputed from the stored (encrypted) payload, which skips secure_settings.
 * - 'config_cache' entries used to carry a plaintext copy of the payload. Cache entries are
 *   unreadable after a restart anyway (the cache key lives only in memory), so all are dropped.
 *
 * Only the derived 'searchableText' field is touched; payloads, hashes and versions are not.
 * Idempotent: documents whose searchableText is already clean are skipped.
 */
@Component
public class PlaintextRemediationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaintextRemediationRunner.class);

    private final MongoTemplate mongoTemplate;
    private final ConfigurationService configurationService;

    public PlaintextRemediationRunner(MongoTemplate mongoTemplate, ConfigurationService configurationService) {
        this.mongoTemplate = mongoTemplate;
        this.configurationService = configurationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            long purged = mongoTemplate.remove(new Query(), CachedConfig.class).getDeletedCount();

            int rewritten = 0;
            for (VaultDocument doc : mongoTemplate.findAll(VaultDocument.class)) {
                String clean = configurationService.buildSearchableContent(
                        doc.getApplicationName(), doc.getEnvironment(), doc.getPayload(), doc.getTags());
                if (!Objects.equals(clean, doc.getSearchableText())) {
                    mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(doc.getId())),
                            Update.update("searchableText", clean),
                            VaultDocument.class);
                    rewritten++;
                }
            }

            if (purged > 0 || rewritten > 0) {
                log.info("Plaintext remediation: purged {} cache entries, rewrote searchableText on {} configurations.",
                        purged, rewritten);
            }
        } catch (Exception e) {
            log.warn("Plaintext remediation could not complete: {}", e.getMessage());
        }
    }
}
