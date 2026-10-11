package com.vault.configmanager.repository;

import com.vault.configmanager.model.AccessTelemetry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Repository for MongoDB Time-Series collection 'config_access_telemetry'.
 */
@Repository
public interface TelemetryRepository extends MongoRepository<AccessTelemetry, String> {

    List<AccessTelemetry> findByTimestampAfterOrderByTimestampAsc(Date since);

    long countByTimestampAfter(Date since);
}
