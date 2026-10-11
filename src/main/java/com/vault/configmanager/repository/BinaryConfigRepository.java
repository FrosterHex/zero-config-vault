package com.vault.configmanager.repository;

import com.vault.configmanager.model.BinaryConfigDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for BinaryConfigDocument in 'binary_configurations'.
 */
@Repository
public interface BinaryConfigRepository extends MongoRepository<BinaryConfigDocument, String> {

    Optional<BinaryConfigDocument> findTopByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc(
            String applicationName, String environment, String filename);

    Optional<BinaryConfigDocument> findByApplicationNameAndEnvironmentAndFilenameAndVersion(
            String applicationName, String environment, String filename, int version);

    List<BinaryConfigDocument> findByApplicationNameAndEnvironmentAndFilenameOrderByVersionDesc(
            String applicationName, String environment, String filename);

    List<BinaryConfigDocument> findByApplicationNameAndEnvironmentOrderByVersionDesc(
            String applicationName, String environment);
}
