package com.vault.configmanager.service;

import com.vault.configmanager.model.VaultDocument;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which configuration versions may expire. The newest {@code retainCount + 1} versions of an
 * app/environment (the current version plus {@code retainCount} rollback targets) never carry an
 * {@code expiresAt}. Older versions get {@code now + expiryDays} the first time they fall out of that
 * window and keep that date afterwards, so repeated writes never extend it. MongoDB's TTL index
 * (expireAfterSeconds = 0) then deletes each version at its exact date.
 * Only {@code expiresAt} is modified; payloads and hashes are never touched.
 */
@Service
public class VersionRetentionService {

    private final MongoTemplate mongoTemplate;
    private final int retainCount;
    private final int expiryDays;

    public VersionRetentionService(MongoTemplate mongoTemplate,
                                   @Value("${vault.version.retain-count:5}") int retainCount,
                                   @Value("${vault.version.expiry-days:90}") int expiryDays) {
        this.mongoTemplate = mongoTemplate;
        this.retainCount = retainCount;
        this.expiryDays = expiryDays;
    }

    public void applyRetention(String app, String env) {
        Criteria scope = Criteria.where("applicationName").is(app).and("environment").is(env);

        if (expiryDays <= 0) {
            mongoTemplate.updateMulti(new Query(scope), new Update().unset("expiresAt"), VaultDocument.class);
            return;
        }

        // The oldest version still inside the protected window; skip(retainCount) lands on it.
        VaultDocument boundary = mongoTemplate.findOne(
                new Query(scope).with(Sort.by(Sort.Direction.DESC, "version")).skip(retainCount).limit(1),
                VaultDocument.class);
        if (boundary == null) {
            return; // fewer than retainCount + 1 versions: nothing can expire
        }
        int boundaryVersion = boundary.getVersion();

        mongoTemplate.updateMulti(
                new Query(new Criteria().andOperator(scope, Criteria.where("version").gte(boundaryVersion),
                        Criteria.where("expiresAt").ne(null))),
                new Update().unset("expiresAt"), VaultDocument.class);

        mongoTemplate.updateMulti(
                new Query(new Criteria().andOperator(scope, Criteria.where("version").lt(boundaryVersion),
                        Criteria.where("expiresAt").is(null))),
                Update.update("expiresAt", LocalDateTime.now().plusDays(expiryDays)), VaultDocument.class);
    }

    /** Applies the rule to every app/environment pair; returns the number of pairs processed. */
    public int applyRetentionToAll() {
        List<String[]> pairs = new ArrayList<>();
        mongoTemplate.getCollection("configurations").aggregate(List.of(
                new Document("$group", new Document("_id",
                        new Document("a", "$applicationName").append("e", "$environment")))))
                .forEach(d -> {
                    Document id = d.get("_id", Document.class);
                    pairs.add(new String[]{id.getString("a"), id.getString("e")});
                });
        for (String[] pair : pairs) {
            applyRetention(pair[0], pair[1]);
        }
        return pairs.size();
    }
}
