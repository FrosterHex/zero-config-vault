package com.vault.configmanager.repository;

import com.vault.configmanager.model.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for ApiKey entities in the 'api_keys' collection.
 */
@Repository
public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {

    Optional<ApiKey> findByHashedSecret(String hashedSecret);

    List<ApiKey> findByActiveTrue();
}
