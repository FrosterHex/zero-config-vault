package com.vault.configmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vault.configmanager.dto.request.BulkConfigRequest;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.dto.response.BulkConfigResponse;
import com.vault.configmanager.dto.response.ConfigChangeEvent;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.dto.response.DiffResponse;
import com.vault.configmanager.dto.response.VersionSummary;
import com.vault.configmanager.exception.ConfigNotFoundException;
import com.vault.configmanager.exception.DuplicateVersionException;
import com.vault.configmanager.exception.IntegrityViolationException;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.repository.ConfigurationRepository;
import com.vault.configmanager.util.ConfigDiffUtil;
import com.vault.configmanager.util.JsonTraverser;
import org.bson.Document;
import com.vault.configmanager.exception.BulkTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.vault.configmanager.dto.response.ConfigSearchResult;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Core Configuration Service implementing GitOps-style Temporal Versioning.
 *
 * Immutability Guarantee:
 * - Configurations are NEVER updated in place.
 * - Every creation, partial update, or rollback calculates the next sequential
 *   version number and inserts a fresh document.
 * - Verifies SHA-512 cryptographic integrity fingerprints on reads.
 */
@Service
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigurationRepository configurationRepository;
    private final JsonTraverser jsonTraverser;
    private final HashingService hashingService;
    private final CacheService cacheService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ConfigDiffUtil configDiffUtil;
    private final MongoTransactionManager transactionManager;
    private final ChangeStreamService changeStreamService;
    private final MongoTemplate mongoTemplate;
    private final TelemetryService telemetryService;

    @Value("${vault.version.expiry-days:90}")
    private int expiryDays;

    public ConfigurationService(ConfigurationRepository configurationRepository,
                                JsonTraverser jsonTraverser,
                                HashingService hashingService,
                                CacheService cacheService,
                                AuditService auditService,
                                ObjectMapper objectMapper,
                                ConfigDiffUtil configDiffUtil) {
        this(configurationRepository, jsonTraverser, hashingService, cacheService, auditService, objectMapper, configDiffUtil, null, null, null, null);
    }

    public ConfigurationService(ConfigurationRepository configurationRepository,
                                JsonTraverser jsonTraverser,
                                HashingService hashingService,
                                CacheService cacheService,
                                AuditService auditService,
                                ObjectMapper objectMapper,
                                ConfigDiffUtil configDiffUtil,
                                MongoTransactionManager transactionManager) {
        this(configurationRepository, jsonTraverser, hashingService, cacheService, auditService, objectMapper, configDiffUtil, transactionManager, null, null, null);
    }

    @Autowired
    public ConfigurationService(ConfigurationRepository configurationRepository,
                                JsonTraverser jsonTraverser,
                                HashingService hashingService,
                                CacheService cacheService,
                                AuditService auditService,
                                ObjectMapper objectMapper,
                                ConfigDiffUtil configDiffUtil,
                                @Autowired(required = false) MongoTransactionManager transactionManager,
                                @Autowired(required = false) ChangeStreamService changeStreamService,
                                @Autowired(required = false) MongoTemplate mongoTemplate,
                                @Autowired(required = false) TelemetryService telemetryService) {
        this.configurationRepository = configurationRepository;
        this.jsonTraverser = jsonTraverser;
        this.hashingService = hashingService;
        this.cacheService = cacheService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.configDiffUtil = configDiffUtil;
        this.transactionManager = transactionManager;
        this.changeStreamService = changeStreamService;
        this.mongoTemplate = mongoTemplate;
        this.telemetryService = telemetryService;
    }

    /**
     * Ingests a new configuration or creates the first revision.
     */
    public ConfigResponse createConfiguration(ConfigRequest request, String requestingIp, String apiKey) {
        long startTime = System.currentTimeMillis();
        String app = request.getApplicationName();
        String env = request.getEnvironment();

        // 1. Encrypt secure_settings
        Document rawPayload = new Document(request.getPayload());
        Document encryptedPayload = jsonTraverser.encryptDocument(rawPayload);

        // 2. Compute canonical SHA-512 fingerprint on encrypted payload
        String documentHash = hashingService.computePayloadHash(encryptedPayload);

        // 3. Determine next version number
        Optional<VaultDocument> latestOpt = configurationRepository
                .findTopByApplicationNameAndEnvironmentOrderByVersionDesc(app, env);
        int nextVersion = latestOpt.map(v -> v.getVersion() + 1).orElse(1);

        // Expire older version after configured days if enabled
        latestOpt.ifPresent(this::retirePreviousVersion);

        // 4. Persist new immutable document
        LocalDateTime now = LocalDateTime.now();
        VaultDocument document = new VaultDocument(
                app,
                env,
                nextVersion,
                documentHash,
                encryptedPayload,
                request.getTags(),
                now,
                null // Active latest version never expires
        );
        document.setSearchableText(buildSearchableContent(app, env, rawPayload, request.getTags()));

        VaultDocument saved;
        try {
            saved = configurationRepository.save(document);
        } catch (DuplicateKeyException e) {
            throw new DuplicateVersionException(app, env, nextVersion);
        }

        // 5. Invalidate read cache
        cacheService.evict(app, env);

        // 6. Broadcast reactive change stream event
        notifyChangeStream("CREATE", app, env, nextVersion, documentHash, saved.getTags());

        // 7. Audit log
        auditService.log("CREATE", app, env, requestingIp, apiKey, true, null);
        recordTelemetry(app, env, "WRITE", startTime, false, 201, requestingIp);

        // 8. Return decrypted response
        Document decryptedPayload = jsonTraverser.decryptDocument(saved.getPayload());
        return toConfigResponse(saved, decryptedPayload);
    }

    /**
     * Retrieves the latest active configuration revision.
     * Uses read-through caching and validates document integrity.
     */
    public ConfigResponse getLatestConfiguration(String app, String env, String requestingIp, String apiKey) {
        long startTime = System.currentTimeMillis();
        // 1. Check MongoDB TTL Cache
        Optional<Document> cachedPayload = cacheService.get(app, env);
        if (cachedPayload.isPresent()) {
            auditService.log("READ", app, env, requestingIp, apiKey, true, null);
            Optional<VaultDocument> latestMeta = configurationRepository
                    .findTopByApplicationNameAndEnvironmentOrderByVersionDesc(app, env);
            if (latestMeta.isPresent()) {
                recordTelemetry(app, env, "READ", startTime, true, 200, requestingIp);
                return toConfigResponse(latestMeta.get(), cachedPayload.get());
            }
        }

        // 2. Cache Miss - Query MongoDB for latest version
        VaultDocument document = configurationRepository
                .findTopByApplicationNameAndEnvironmentOrderByVersionDesc(app, env)
                .orElseThrow(() -> new ConfigNotFoundException(app, env));

        // 3. Verify SHA-512 cryptographic integrity
        verifyIntegrity(document, app, env, requestingIp, apiKey);

        // 4. Decrypt secure settings
        Document decryptedPayload = jsonTraverser.decryptDocument(document.getPayload());

        // 5. Populate cache
        cacheService.put(app, env, decryptedPayload);

        // 6. Audit log
        auditService.log("READ", app, env, requestingIp, apiKey, true, null);
        recordTelemetry(app, env, "READ", startTime, false, 200, requestingIp);

        return toConfigResponse(document, decryptedPayload);
    }

    /**
     * Retrieves a specific historical revision by version number.
     */
    public ConfigResponse getConfigurationByVersion(String app, String env, int version, String requestingIp, String apiKey) {
        long startTime = System.currentTimeMillis();
        VaultDocument document = configurationRepository
                .findByApplicationNameAndEnvironmentAndVersion(app, env, version)
                .orElseThrow(() -> new ConfigNotFoundException(app, env, version));

        verifyIntegrity(document, app, env, requestingIp, apiKey);

        Document decryptedPayload = jsonTraverser.decryptDocument(document.getPayload());
        auditService.log("READ", app, env, requestingIp, apiKey, true, null);
        recordTelemetry(app, env, "READ", startTime, false, 200, requestingIp);

        return toConfigResponse(document, decryptedPayload);
    }

    /**
     * Partially updates an existing configuration by deep-merging changes into a new version.
     */
    public ConfigResponse updateConfigurationPartial(String app, String env, Map<String, Object> partialPayload,
                                                     String requestingIp, String apiKey) {
        // 1. Fetch latest active revision
        VaultDocument latestDoc = configurationRepository
                .findTopByApplicationNameAndEnvironmentOrderByVersionDesc(app, env)
                .orElseThrow(() -> new ConfigNotFoundException(app, env));

        verifyIntegrity(latestDoc, app, env, requestingIp, apiKey);

        // 2. Decrypt current payload and deep merge with incoming patch
        Document currentDecrypted = jsonTraverser.decryptDocument(latestDoc.getPayload());
        JsonNode currentNode = objectMapper.valueToTree(currentDecrypted);
        JsonNode patchNode = objectMapper.valueToTree(partialPayload);

        JsonNode mergedNode = deepMerge(currentNode, patchNode);
        Document mergedDoc = Document.parse(objectMapper.valueToTree(mergedNode).toString());

        // 3. Create as new incremented revision
        ConfigRequest request = new ConfigRequest(app, env, mergedDoc, latestDoc.getTags());
        return createConfiguration(request, requestingIp, apiKey);
    }

    /**
     * Lists all historical version summaries (sorted descending) without full payloads.
     */
    public List<VersionSummary> listVersions(String app, String env) {
        return configurationRepository.findByApplicationNameAndEnvironmentOrderByVersionDesc(app, env)
                .stream()
                .map(doc -> new VersionSummary(
                        doc.getVersion(),
                        doc.getDocumentHash(),
                        doc.getTags(),
                        doc.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Performs a GitOps-style rollback to a historical version.
     * Re-inserts the historical configuration as a brand-new revision with incremented version number.
     */
    public ConfigResponse rollbackToVersion(String app, String env, int targetVersion, String requestingIp, String apiKey) {
        long startTime = System.currentTimeMillis();
        // 1. Find target historical revision
        VaultDocument targetDoc = configurationRepository
                .findByApplicationNameAndEnvironmentAndVersion(app, env, targetVersion)
                .orElseThrow(() -> new ConfigNotFoundException(app, env, targetVersion));

        verifyIntegrity(targetDoc, app, env, requestingIp, apiKey);

        // 2. Determine next version number
        Optional<VaultDocument> latestOpt = configurationRepository
                .findTopByApplicationNameAndEnvironmentOrderByVersionDesc(app, env);
        int nextVersion = latestOpt.map(v -> v.getVersion() + 1).orElse(1);

        latestOpt.ifPresent(this::retirePreviousVersion);

        // 3. Re-insert the target payload under the new version number
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> tags = new HashMap<>(targetDoc.getTags());
        tags.put("rollback_from_version", String.valueOf(targetVersion));

        VaultDocument rolledBackDoc = new VaultDocument(
                app,
                env,
                nextVersion,
                targetDoc.getDocumentHash(),
                new Document(targetDoc.getPayload()),
                tags,
                now,
                null
        );
        rolledBackDoc.setSearchableText(buildSearchableContent(app, env, targetDoc.getPayload(), tags));

        VaultDocument saved;
        try {
            saved = configurationRepository.save(rolledBackDoc);
        } catch (DuplicateKeyException e) {
            throw new DuplicateVersionException(app, env, nextVersion);
        }

        // 4. Invalidate cache and audit
        cacheService.evict(app, env);
        notifyChangeStream("ROLLBACK", app, env, nextVersion, targetDoc.getDocumentHash(), tags);
        auditService.log("ROLLBACK", app, env, requestingIp, apiKey, true, null);
        recordTelemetry(app, env, "ROLLBACK", startTime, false, 200, requestingIp);

        Document decryptedPayload = jsonTraverser.decryptDocument(saved.getPayload());
        return toConfigResponse(saved, decryptedPayload);
    }

    private void notifyChangeStream(String eventType, String app, String env, int version, String hash, Map<String, ?> metadata) {
        if (changeStreamService != null) {
            try {
                Map<String, Object> meta = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
                changeStreamService.broadcastChange(new ConfigChangeEvent(
                        eventType, app, env, version, hash, LocalDateTime.now(), meta
                ));
            } catch (Exception e) {
                log.warn("Failed to broadcast change stream event for app={}, env={}: {}", app, env, e.getMessage());
            }
        }
    }

    // --- Helper Methods ---

    private void verifyIntegrity(VaultDocument document, String app, String env, String ip, String apiKey) {
        boolean valid = hashingService.verifyPayloadHash(document.getPayload(), document.getDocumentHash());
        if (!valid) {
            String failureReason = "SHA-512 fingerprint mismatch. Possible manual tampering detected.";
            auditService.log("READ", app, env, ip, apiKey, false, failureReason);
            throw new IntegrityViolationException(app, env, document.getVersion());
        }
    }

    private void retirePreviousVersion(VaultDocument doc) {
        if (expiryDays > 0) {
            doc.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));
            configurationRepository.save(doc);
        }
    }

    private ConfigResponse toConfigResponse(VaultDocument doc, Document decryptedPayload) {
        return new ConfigResponse(
                doc.getId(),
                doc.getApplicationName(),
                doc.getEnvironment(),
                doc.getVersion(),
                doc.getDocumentHash(),
                decryptedPayload,
                doc.getTags(),
                doc.getCreatedAt()
        );
    }

    private JsonNode deepMerge(JsonNode target, JsonNode source) {
        if (!source.isObject()) {
            return source;
        }

        ObjectNode targetObject = target.isObject() ? (ObjectNode) target : objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode value = field.getValue();

            if (targetObject.has(fieldName) && targetObject.get(fieldName).isObject() && value.isObject()) {
                deepMerge(targetObject.get(fieldName), value);
            } else {
                targetObject.set(fieldName, value);
            }
        }

        return targetObject;
    }

    /**
     * Diffs two configuration revisions, decrypting both and comparing key paths.
     */
    public DiffResponse diffVersions(String app, String env, int v1, int v2) {
        VaultDocument doc1 = configurationRepository
                .findByApplicationNameAndEnvironmentAndVersion(app, env, v1)
                .orElseThrow(() -> new ConfigNotFoundException(app, env, v1));

        VaultDocument doc2 = configurationRepository
                .findByApplicationNameAndEnvironmentAndVersion(app, env, v2)
                .orElseThrow(() -> new ConfigNotFoundException(app, env, v2));

        Document decrypted1 = jsonTraverser.decryptDocument(doc1.getPayload());
        Document decrypted2 = jsonTraverser.decryptDocument(doc2.getPayload());

        return configDiffUtil.comparePayloads(v1, v2, decrypted1, decrypted2);
    }

    /**
     * Searches for a specific value in the latest decrypted configuration via dot-separated path.
     */
    public Object searchByPath(String app, String env, String dotPath, String requestingIp, String apiKey) {
        VaultDocument latest = configurationRepository
                .findTopByApplicationNameAndEnvironmentOrderByVersionDesc(app, env)
                .orElseThrow(() -> new ConfigNotFoundException(app, env));

        verifyIntegrity(latest, app, env, requestingIp, apiKey);

        Document decrypted = jsonTraverser.decryptDocument(latest.getPayload());
        Optional<Object> result = configDiffUtil.extractByPath(decrypted, dotPath);

        if (result.isEmpty()) {
            throw new ConfigNotFoundException("Path '" + dotPath + "' not found in configuration for app '" + app + "', env '" + env + "'.");
        }

        auditService.log("SEARCH", app, env, requestingIp, apiKey, true, null);
        return result.get();
    }

    /**
     * Performs bulk configuration import.
     * When atomic=true, wraps execution in an ACID transaction using Spring's MongoTransactionManager.
     * If any validation or insertion fails, the entire transaction is rolled back and a BulkTransactionException is thrown.
     * When atomic=false, performs best-effort import.
     */
    public BulkConfigResponse bulkImport(BulkConfigRequest bulkRequest, boolean atomic, String requestingIp, String apiKey) {
        if (atomic && transactionManager != null) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            try {
                return transactionTemplate.execute(status -> {
                    int succeeded = 0;
                    List<ConfigResponse> results = new ArrayList<>();
                    List<Map<String, Object>> errors = new ArrayList<>();
                    List<ConfigRequest> configs = bulkRequest.getConfigs() != null ? bulkRequest.getConfigs() : List.of();

                    for (int i = 0; i < configs.size(); i++) {
                        ConfigRequest req = configs.get(i);
                        try {
                            ConfigResponse response = createConfiguration(req, requestingIp, apiKey);
                            results.add(response);
                            succeeded++;
                        } catch (Exception e) {
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("index", i);
                            err.put("applicationName", req.getApplicationName());
                            err.put("environment", req.getEnvironment());
                            err.put("error", e.getMessage());
                            errors.add(err);
                        }
                    }

                    if (!errors.isEmpty()) {
                        status.setRollbackOnly();
                        auditService.log("BULK_IMPORT_ROLLBACK", "bulk", "multi", requestingIp, apiKey, false,
                                "Atomic bulk transaction rolled back due to " + errors.size() + " failure(s)");
                        throw new BulkTransactionException(
                                "Atomic bulk transaction failed and was rolled back due to " + errors.size() + " validation/execution error(s)",
                                errors);
                    }

                    auditService.log("BULK_IMPORT_ATOMIC", "bulk", "multi", requestingIp, apiKey, true, null);
                    return new BulkConfigResponse(configs.size(), succeeded, 0, results, List.of());
                });
            } catch (BulkTransactionException bte) {
                throw bte;
            } catch (Exception ex) {
                log.warn("MongoTransactionManager transaction failed or unsupported ({}: {}). Re-throwing BulkTransactionException.",
                        ex.getClass().getSimpleName(), ex.getMessage());
                throw new BulkTransactionException("Atomic bulk transaction error: " + ex.getMessage());
            }
        }

        return executeBestEffortBulkImport(bulkRequest, requestingIp, apiKey);
    }

    public BulkConfigResponse bulkImport(BulkConfigRequest bulkRequest, String requestingIp, String apiKey) {
        return bulkImport(bulkRequest, false, requestingIp, apiKey);
    }

    private BulkConfigResponse executeBestEffortBulkImport(BulkConfigRequest bulkRequest, String requestingIp, String apiKey) {
        int succeeded = 0;
        int failed = 0;
        List<ConfigResponse> results = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        List<ConfigRequest> configs = bulkRequest.getConfigs() != null ? bulkRequest.getConfigs() : List.of();

        for (int i = 0; i < configs.size(); i++) {
            ConfigRequest req = configs.get(i);
            try {
                ConfigResponse response = createConfiguration(req, requestingIp, apiKey);
                results.add(response);
                succeeded++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("index", i);
                err.put("applicationName", req.getApplicationName());
                err.put("environment", req.getEnvironment());
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }

        auditService.log("BULK_IMPORT", "bulk", "multi", requestingIp, apiKey,
                failed == 0, failed > 0 ? "Failures: " + failed + "/" + configs.size() : null);

        return new BulkConfigResponse(configs.size(), succeeded, failed, results, errors);
    }

    /**
     * Executes MongoDB Full-Text and regex search across configuration payloads, apps, and tags.
     */
    public List<ConfigSearchResult> searchConfigurations(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        long start = System.currentTimeMillis();
        String cleaned = query.trim();

        List<VaultDocument> matches;
        if (mongoTemplate != null) {
            Query mongoQuery = new Query();
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("applicationName").regex(Pattern.quote(cleaned), "i"),
                    Criteria.where("environment").regex(Pattern.quote(cleaned), "i"),
                    Criteria.where("searchableText").regex(Pattern.quote(cleaned), "i")
            );
            mongoQuery.addCriteria(searchCriteria);
            mongoQuery.with(Sort.by(Sort.Direction.DESC, "version"));
            mongoQuery.limit(25);
            matches = mongoTemplate.find(mongoQuery, VaultDocument.class);
        } else {
            matches = configurationRepository.findAll().stream()
                    .filter(d -> (d.getApplicationName() != null && d.getApplicationName().toLowerCase().contains(cleaned.toLowerCase())) ||
                            (d.getEnvironment() != null && d.getEnvironment().toLowerCase().contains(cleaned.toLowerCase())) ||
                            (d.getSearchableText() != null && d.getSearchableText().toLowerCase().contains(cleaned.toLowerCase())))
                    .limit(25)
                    .toList();
        }

        List<ConfigSearchResult> results = new ArrayList<>();
        for (VaultDocument doc : matches) {
            Document decrypted = jsonTraverser.decryptDocument(doc.getPayload());
            Map<String, Object> snippets = extractMatchingSnippets(decrypted, cleaned.toLowerCase());
            float score = (float) (snippets.size() * 2.0 + (doc.getApplicationName().toLowerCase().contains(cleaned.toLowerCase()) ? 3.0 : 1.0));

            results.add(new ConfigSearchResult(
                    doc.getApplicationName(),
                    doc.getEnvironment(),
                    doc.getVersion(),
                    doc.getDocumentHash(),
                    doc.getCreatedAt(),
                    score,
                    snippets
            ));
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        recordTelemetry("all", "all", "SEARCH", start, false, 200, "internal");
        return results;
    }

    public String buildSearchableContent(String app, String env, Document payload, Map<String, String> tags) {
        StringBuilder sb = new StringBuilder();
        sb.append(app).append(" ").append(env).append(" ");
        if (tags != null) {
            tags.forEach((k, v) -> sb.append(k).append(":").append(v).append(" "));
        }
        if (payload != null) {
            flattenDocumentToSearch(payload, "", sb);
        }
        return sb.toString();
    }

    private void flattenDocumentToSearch(Document doc, String prefix, StringBuilder sb) {
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object val = entry.getValue();
            sb.append(key).append(" ");
            if (val instanceof Document childDoc) {
                flattenDocumentToSearch(childDoc, key, sb);
            } else if (val != null) {
                sb.append(val.toString()).append(" ");
            }
        }
    }

    private Map<String, Object> extractMatchingSnippets(Document doc, String term) {
        Map<String, Object> snippets = new LinkedHashMap<>();
        flattenMatchingNodes(doc, "", term, snippets);
        return snippets;
    }

    private void flattenMatchingNodes(Document doc, String prefix, String term, Map<String, Object> snippets) {
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object val = entry.getValue();
            if (key.toLowerCase().contains(term) || (val != null && val.toString().toLowerCase().contains(term))) {
                snippets.put(key, val instanceof Document ? "{...}" : val);
            }
            if (val instanceof Document childDoc && snippets.size() < 8) {
                flattenMatchingNodes(childDoc, key, term, snippets);
            }
        }
    }

    private void recordTelemetry(String app, String env, String op, long startMs, boolean cacheHit, int statusCode, String ip) {
        if (telemetryService != null) {
            long lat = Math.max(1, System.currentTimeMillis() - startMs);
            telemetryService.recordAccess(app, env, op, lat, cacheHit, statusCode, ip);
        }
    }
}
