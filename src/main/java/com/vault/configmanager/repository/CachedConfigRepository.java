package com.vault.configmanager.repository;

import com.vault.configmanager.model.CachedConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for CachedConfig operations on the 'config_cache' TTL collection.
 */
@Repository
public interface CachedConfigRepository extends MongoRepository<CachedConfig, String> {
}
