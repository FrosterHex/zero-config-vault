package com.vault.configmanager.repository;

import com.vault.configmanager.model.VaultDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for VaultDocument CRUD and query operations on the 'configurations' collection.
 */
@Repository
public interface ConfigurationRepository extends MongoRepository<VaultDocument, String> {

    Optional<VaultDocument> findTopByApplicationNameAndEnvironmentOrderByVersionDesc(
            String applicationName, String environment);

    Optional<VaultDocument> findByApplicationNameAndEnvironmentAndVersion(
            String applicationName, String environment, int version);

    List<VaultDocument> findByApplicationNameAndEnvironmentOrderByVersionDesc(
            String applicationName, String environment);

    boolean existsByApplicationNameAndEnvironmentAndVersion(
            String applicationName, String environment, int version);

    long countByApplicationNameAndEnvironment(
            String applicationName, String environment);
}
