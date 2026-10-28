package com.vault.configmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.dto.response.ConfigResponse;
import com.vault.configmanager.dto.response.VersionSummary;
import com.vault.configmanager.exception.ConfigNotFoundException;
import com.vault.configmanager.exception.IntegrityViolationException;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.repository.ConfigurationRepository;
import com.vault.configmanager.security.KeyProvider;
import com.vault.configmanager.util.ConfigDiffUtil;
import com.vault.configmanager.util.JsonTraverser;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigurationServiceTest {

    private ConfigurationService configurationService;
    private ConfigurationRepository configurationRepository;
    private JsonTraverser jsonTraverser;
    private HashingService hashingService;
    private CacheService cacheService;
    private AuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationRepository.class);
        KeyProvider keyProvider = mock(KeyProvider.class);
        when(keyProvider.getMasterKey()).thenReturn("secret-master-key-32-chars-long!");

        CryptoService cryptoService = new CryptoService(keyProvider);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        jsonTraverser = new JsonTraverser(cryptoService, objectMapper);
        hashingService = new HashingService(objectMapper);
        cacheService = mock(CacheService.class);
        auditService = mock(AuditService.class);
        ConfigDiffUtil configDiffUtil = new ConfigDiffUtil();

        configurationService = new ConfigurationService(
                configurationRepository,
                jsonTraverser,
                hashingService,
                cacheService,
                auditService,
                objectMapper,
                configDiffUtil
        );
    }

    @Test
    void createConfiguration_firstRevision_assignsVersion1() {
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("user-service", "prod"))
                .thenReturn(Optional.empty());

        when(configurationRepository.save(any(VaultDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigRequest request = new ConfigRequest(
                "user-service",
                "prod",
                Map.of("db_host", "postgres.prod", "secure_settings", Map.of("password", "root123")),
                Map.of("env_type", "production")
        );

        ConfigResponse response = configurationService.createConfiguration(request, "127.0.0.1", "sk-****-001");

        assertNotNull(response);
        assertEquals(1, response.getVersion());
        assertEquals("user-service", response.getApplicationName());
        assertNotNull(response.getDocumentHash());

        verify(cacheService).evict("user-service", "prod");
        verify(auditService).log(eq("CREATE"), eq("user-service"), eq("prod"), anyString(), anyString(), eq(true), isNull());
    }

    @Test
    void createConfiguration_incrementsVersionSequentially() {
        VaultDocument v1 = new VaultDocument("auth-service", "staging", 1, "hash1",
                new Document("port", 8080), Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("auth-service", "staging"))
                .thenReturn(Optional.of(v1));

        when(configurationRepository.save(any(VaultDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigRequest request = new ConfigRequest(
                "auth-service",
                "staging",
                Map.of("port", 8081),
                Map.of()
        );

        ConfigResponse response = configurationService.createConfiguration(request, "127.0.0.1", "sk-****-001");

        assertEquals(2, response.getVersion());
    }

    @Test
    void getLatestConfiguration_cacheHit_servesFromCache() {
        Document cachedDoc = new Document("cachedKey", "cachedValue");
        when(cacheService.get("order-service", "prod")).thenReturn(Optional.of(cachedDoc));

        VaultDocument latestMeta = new VaultDocument("order-service", "prod", 3, "hash3",
                cachedDoc, Map.of(), LocalDateTime.now(), null);
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("order-service", "prod"))
                .thenReturn(Optional.of(latestMeta));

        ConfigResponse response = configurationService.getLatestConfiguration("order-service", "prod", "127.0.0.1", "sk-key");

        assertNotNull(response);
        assertEquals(3, response.getVersion());
        verify(auditService).log(eq("READ"), eq("order-service"), eq("prod"), anyString(), anyString(), eq(true), isNull());
    }

    @Test
    void getLatestConfiguration_tamperedDocument_throwsIntegrityViolationException() {
        when(cacheService.get("payment-service", "prod")).thenReturn(Optional.empty());

        Document tamperedPayload = new Document("account", "attacker");
        // Store legitimate hash of a different payload
        VaultDocument tamperedDoc = new VaultDocument("payment-service", "prod", 1, "legit-hash-that-wont-match",
                tamperedPayload, Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("payment-service", "prod"))
                .thenReturn(Optional.of(tamperedDoc));

        assertThrows(IntegrityViolationException.class, () ->
                configurationService.getLatestConfiguration("payment-service", "prod", "10.0.0.1", "sk-key")
        );

        verify(auditService).log(eq("READ"), eq("payment-service"), eq("prod"), anyString(), anyString(), eq(false), contains("fingerprint mismatch"));
    }

    @Test
    void rollbackToVersion_createsNewVersionWithTargetPayload() {
        Document v1Payload = jsonTraverser.encryptDocument(new Document("setting", "v1-value"));
        String v1Hash = hashingService.computePayloadHash(v1Payload);

        VaultDocument v1 = new VaultDocument("billing", "prod", 1, v1Hash, v1Payload, Map.of(), LocalDateTime.now(), null);
        VaultDocument v2 = new VaultDocument("billing", "prod", 2, "v2Hash", new Document("setting", "v2-value"), Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findByApplicationNameAndEnvironmentAndVersion("billing", "prod", 1))
                .thenReturn(Optional.of(v1));
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("billing", "prod"))
                .thenReturn(Optional.of(v2));

        when(configurationRepository.save(any(VaultDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigResponse rolledBack = configurationService.rollbackToVersion("billing", "prod", 1, "127.0.0.1", "sk-key");

        assertEquals(3, rolledBack.getVersion(), "Rollback must produce a new version number 3");
        assertEquals("v1-value", rolledBack.getPayload().get("setting"));
        assertEquals("1", rolledBack.getTags().get("rollback_from_version"));
        verify(cacheService).evict("billing", "prod");
        verify(auditService).log(eq("ROLLBACK"), eq("billing"), eq("prod"), anyString(), anyString(), eq(true), isNull());
    }

    @Test
    void listVersions_returnsSortedSummaries() {
        VaultDocument doc1 = new VaultDocument("app", "env", 1, "h1", new Document(), Map.of(), LocalDateTime.now(), null);
        VaultDocument doc2 = new VaultDocument("app", "env", 2, "h2", new Document(), Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findByApplicationNameAndEnvironmentOrderByVersionDesc("app", "env"))
                .thenReturn(List.of(doc2, doc1));

        List<VersionSummary> summaries = configurationService.listVersions("app", "env");

        assertEquals(2, summaries.size());
        assertEquals(2, summaries.get(0).getVersion());
        assertEquals(1, summaries.get(1).getVersion());
    }

    @Test
    void diffVersions_comparesPayloadsAcrossRevisions() {
        Document p1 = new Document("host", "localhost").append("port", 8080);
        Document p2 = new Document("host", "db.internal").append("port", 8080);

        VaultDocument doc1 = new VaultDocument("app", "prod", 1, "h1", p1, Map.of(), LocalDateTime.now(), null);
        VaultDocument doc2 = new VaultDocument("app", "prod", 2, "h2", p2, Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findByApplicationNameAndEnvironmentAndVersion("app", "prod", 1))
                .thenReturn(Optional.of(doc1));
        when(configurationRepository.findByApplicationNameAndEnvironmentAndVersion("app", "prod", 2))
                .thenReturn(Optional.of(doc2));

        com.vault.configmanager.dto.response.DiffResponse diff = configurationService.diffVersions("app", "prod", 1, 2);

        assertNotNull(diff);
        assertEquals(1, diff.getVersionFrom());
        assertEquals(2, diff.getVersionTo());
        assertTrue(diff.getModified().containsKey("host"));
        assertEquals(1, diff.getUnchangedCount());
    }

    @Test
    void searchByPath_extractsNestedProperty() {
        Document payload = new Document("database", new Document("host", "db.cluster.lan"));
        String hash = hashingService.computePayloadHash(payload);
        VaultDocument doc = new VaultDocument("app", "prod", 1, hash, payload, Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("app", "prod"))
                .thenReturn(Optional.of(doc));

        Object result = configurationService.searchByPath("app", "prod", "database.host", "127.0.0.1", "key");
        assertEquals("db.cluster.lan", result);
        verify(auditService).log(eq("SEARCH"), eq("app"), eq("prod"), anyString(), anyString(), eq(true), isNull());
    }

    @Test
    void searchByPath_whenNotFound_throwsConfigNotFoundException() {
        Document payload = new Document("host", "localhost");
        String hash = hashingService.computePayloadHash(payload);
        VaultDocument doc = new VaultDocument("app", "prod", 1, hash, payload, Map.of(), LocalDateTime.now(), null);

        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("app", "prod"))
                .thenReturn(Optional.of(doc));

        assertThrows(ConfigNotFoundException.class, () ->
                configurationService.searchByPath("app", "prod", "database.missing", "127.0.0.1", "key")
        );
    }

    @Test
    void bulkImport_processesBestEffortWithFailuresTracked() {
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(configurationRepository.save(any(VaultDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigRequest req1 = new ConfigRequest("app-1", "prod", Map.of("key", "val1"), Map.of());
        ConfigRequest req2 = new ConfigRequest("app-2", "prod", Map.of("key", "val2"), Map.of());

        com.vault.configmanager.dto.request.BulkConfigRequest bulkRequest =
                new com.vault.configmanager.dto.request.BulkConfigRequest(List.of(req1, req2));

        com.vault.configmanager.dto.response.BulkConfigResponse response =
                configurationService.bulkImport(bulkRequest, "127.0.0.1", "key");

        assertEquals(2, response.getTotalProcessed());
        assertEquals(2, response.getSucceeded());
        assertEquals(0, response.getFailed());
        assertEquals(2, response.getResults().size());
        verify(auditService).log(eq("BULK_IMPORT"), anyString(), anyString(), anyString(), anyString(), eq(true), isNull());
    }

    @Test
    void bulkImport_atomic_whenConfigThrows_throwsBulkTransactionException() {
        org.springframework.data.mongodb.MongoTransactionManager txManager = mock(org.springframework.data.mongodb.MongoTransactionManager.class);
        org.springframework.transaction.TransactionStatus status = mock(org.springframework.transaction.TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(status);

        ConfigurationService serviceWithTx = new ConfigurationService(
                configurationRepository,
                jsonTraverser,
                hashingService,
                cacheService,
                auditService,
                objectMapper,
                new ConfigDiffUtil(),
                txManager
        );

        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("app-fail", "prod"))
                .thenThrow(new RuntimeException("Simulated database failure"));

        ConfigRequest req1 = new ConfigRequest("app-fail", "prod", Map.of("key", "val1"), Map.of());
        com.vault.configmanager.dto.request.BulkConfigRequest bulkRequest =
                new com.vault.configmanager.dto.request.BulkConfigRequest(List.of(req1));

        assertThrows(com.vault.configmanager.exception.BulkTransactionException.class, () ->
                serviceWithTx.bulkImport(bulkRequest, true, "127.0.0.1", "key")
        );
        verify(status).setRollbackOnly();
    }

    @Test
    void createConfiguration_searchableTextNeverContainsSecrets() {
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("user-service", "prod"))
                .thenReturn(Optional.empty());
        when(configurationRepository.save(any(VaultDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        configurationService.createConfiguration(new ConfigRequest(
                "user-service", "prod",
                Map.of("db_host", "postgres.prod",
                        "secure_settings", Map.of("password", "hunter2-plaintext")),
                Map.of()), "127.0.0.1", "sk-****");

        org.mockito.ArgumentCaptor<VaultDocument> captor = org.mockito.ArgumentCaptor.forClass(VaultDocument.class);
        verify(configurationRepository).save(captor.capture());
        String searchable = captor.getValue().getSearchableText();
        assertTrue(searchable.contains("postgres.prod"));
        assertFalse(searchable.contains("hunter2-plaintext"));
        assertFalse(searchable.contains("secure_settings"));
    }

    @Test
    void searchConfigurations_isScopedToKeyAndNeverReturnsSecrets() {
        org.bson.Document payload = jsonTraverser.encryptDocument(new org.bson.Document("region", "payment-eu")
                .append("secure_settings", new org.bson.Document("stripe_key", "payment-secret-plaintext")));
        VaultDocument allowed = new VaultDocument("payment-svc", "prod", 1, "h1", payload, Map.of(), java.time.LocalDateTime.now(), null);
        allowed.setSearchableText(configurationService.buildSearchableContent("payment-svc", "prod", payload, Map.of()));
        VaultDocument foreign = new VaultDocument("payment-ledger", "prod", 1, "h2", payload, Map.of(), java.time.LocalDateTime.now(), null);
        foreign.setSearchableText(configurationService.buildSearchableContent("payment-ledger", "prod", payload, Map.of()));
        when(configurationRepository.findAll()).thenReturn(List.of(allowed, foreign));

        com.vault.configmanager.model.ApiKey scoped = new com.vault.configmanager.model.ApiKey(
                "Reader", "sk-", "hash", List.of("payment-svc"), List.of("prod"), java.util.Set.of("READ"), null);

        List<com.vault.configmanager.dto.response.ConfigSearchResult> results =
                configurationService.searchConfigurations("payment", scoped);

        assertEquals(1, results.size());
        assertEquals("payment-svc", results.get(0).getApplicationName());
        assertFalse(results.get(0).getMatchingSnippets().toString().contains("payment-secret-plaintext"));
        assertTrue(configurationService.searchConfigurations("payment", null).isEmpty());
    }

    private VaultDocument storedV1WithPassword(String password) {
        Document encrypted = jsonTraverser.encryptDocument(new Document("database",
                new Document("host", "old").append("secure_settings", new Document("password", password))));
        VaultDocument v1 = new VaultDocument("app", "prod", 1, hashingService.computePayloadHash(encrypted),
                encrypted, Map.of(), LocalDateTime.now(), null);
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("app", "prod"))
                .thenReturn(Optional.of(v1));
        when(configurationRepository.save(any(VaultDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return v1;
    }

    private Document savedPayloadDecrypted() {
        org.mockito.ArgumentCaptor<VaultDocument> captor = org.mockito.ArgumentCaptor.forClass(VaultDocument.class);
        verify(configurationRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getVersion());
        return jsonTraverser.decryptDocument(captor.getValue().getPayload());
    }

    @Test
    void create_placeholderKeepsStoredSecret() {
        storedV1WithPassword("real");
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("database", Map.of("host", "new", "secure_settings", Map.of("password", "********"))), Map.of());

        configurationService.createConfiguration(request, "127.0.0.1", "k");

        Document db = savedPayloadDecrypted().get("database", Document.class);
        assertEquals("real", db.get("secure_settings", Document.class).getString("password"));
        assertEquals("new", db.getString("host"));
    }

    @Test
    void create_changedSecretIsStored() {
        storedV1WithPassword("real");
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("database", Map.of("host", "new", "secure_settings", Map.of("password", "rotated"))), Map.of());

        configurationService.createConfiguration(request, "127.0.0.1", "k");

        Document db = savedPayloadDecrypted().get("database", Document.class);
        assertEquals("rotated", db.get("secure_settings", Document.class).getString("password"));
    }

    @Test
    void create_placeholderAtUnknownPath_rejected() {
        storedV1WithPassword("real");
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("database", Map.of("secure_settings", Map.of("newKey", "********"))), Map.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> configurationService.createConfiguration(request, "127.0.0.1", "k"));

        assertTrue(ex.getMessage().contains("database.secure_settings.newKey"));
        verify(configurationRepository, never()).save(any(VaultDocument.class));
    }

    @Test
    void create_placeholderOnFirstVersion_rejected() {
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("app", "prod"))
                .thenReturn(Optional.empty());
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("secure_settings", Map.of("password", "********")), Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> configurationService.createConfiguration(request, "127.0.0.1", "k"));
        verify(configurationRepository, never()).save(any(VaultDocument.class));
    }

    @Test
    void patch_placeholderKeepsStoredSecret() {
        storedV1WithPassword("real");

        configurationService.updateConfigurationPartial("app", "prod",
                Map.of("database", Map.of("secure_settings", Map.of("password", "********"))), "127.0.0.1", "k");

        Document db = savedPayloadDecrypted().get("database", Document.class);
        assertEquals("real", db.get("secure_settings", Document.class).getString("password"));
    }

    private void storeV1(Document plain) {
        Document encrypted = jsonTraverser.encryptDocument(plain);
        VaultDocument v1 = new VaultDocument("app", "prod", 1, hashingService.computePayloadHash(encrypted),
                encrypted, Map.of(), LocalDateTime.now(), null);
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("app", "prod"))
                .thenReturn(Optional.of(v1));
        when(configurationRepository.save(any(VaultDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_placeholderOutsideSecureSettings_storedLiterally() {
        storedV1WithPassword("real");
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("database", Map.of("host", "********", "secure_settings", Map.of("password", "********"))), Map.of());

        configurationService.createConfiguration(request, "127.0.0.1", "k");

        Document db = savedPayloadDecrypted().get("database", Document.class);
        assertEquals("********", db.getString("host"));
        assertEquals("real", db.get("secure_settings", Document.class).getString("password"));
    }

    @Test
    void create_placeholdersAtSeveralDepths_resolveToTheirOwnPaths() {
        storeV1(new Document("secure_settings", new Document("token", "t-real"))
                .append("services", new Document("billing", new Document("secure_settings", new Document("key", "k-real")))));
        ConfigRequest request = new ConfigRequest("app", "prod", Map.of(
                "secure_settings", Map.of("token", "********"),
                "services", Map.of("billing", Map.of("secure_settings", Map.of("key", "********")))), Map.of());

        configurationService.createConfiguration(request, "127.0.0.1", "k");

        Document saved = savedPayloadDecrypted();
        assertEquals("t-real", saved.get("secure_settings", Document.class).getString("token"));
        assertEquals("k-real", saved.get("services", Document.class).get("billing", Document.class)
                .get("secure_settings", Document.class).getString("key"));
    }

    // JsonTraverser encrypts each direct child of secure_settings as one opaque value, so a stored list
    // round-trips as its JSON text. The masked response therefore carries the whole value as one placeholder.
    @Test
    void create_placeholderForListSecret_keepsWholeStoredValue() {
        storeV1(new Document("secure_settings", new Document("keys", List.of("old0", "old1"))));
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("secure_settings", Map.of("keys", "********")), Map.of());

        configurationService.createConfiguration(request, "127.0.0.1", "k");

        assertEquals("[\"old0\",\"old1\"]",
                savedPayloadDecrypted().get("secure_settings", Document.class).get("keys"));
    }

    @Test
    void create_placeholderInsideListElement_rejectedBecauseStoredSecretIsOpaque() {
        storeV1(new Document("secure_settings", new Document("keys", List.of("old0", "old1"))));
        ConfigRequest request = new ConfigRequest("app", "prod",
                Map.of("secure_settings", Map.of("keys", List.of("********", "new"))), Map.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> configurationService.createConfiguration(request, "127.0.0.1", "k"));

        assertTrue(ex.getMessage().contains("secure_settings.keys.0"));
        verify(configurationRepository, never()).save(any(VaultDocument.class));
    }
}
