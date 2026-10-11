package com.vault.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Collections;
import java.util.Map;

/**
 * Spring Cloud PropertySourceLocator implementation.
 * Integrates directly into Spring's Bootstrap Context during pre-initialization,
 * binding dynamic properties from Zero-Config Vault before any standard Spring beans are initialized.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VaultPropertySourceLocator implements PropertySourceLocator {

    private static final Logger log = LoggerFactory.getLogger(VaultPropertySourceLocator.class);
    public static final String PROPERTY_SOURCE_NAME = "zero-config-vault";

    private final VaultProperties vaultProperties;

    public VaultPropertySourceLocator() {
        this.vaultProperties = new VaultProperties();
    }

    public VaultPropertySourceLocator(VaultProperties vaultProperties) {
        this.vaultProperties = vaultProperties;
    }

    @Override
    public PropertySource<?> locate(Environment environment) {
        String uri = environment.getProperty("vault.uri", vaultProperties.getUri());
        String app = environment.getProperty("vault.application-name",
                environment.getProperty("spring.application.name", "application"));
        String env = environment.getProperty("vault.environment",
                environment.getActiveProfiles().length > 0 ? environment.getActiveProfiles()[0] : "default");
        String apiKey = environment.getProperty("vault.api-key", vaultProperties.getApiKey());
        boolean failOnError = Boolean.parseBoolean(environment.getProperty("vault.fail-on-error", "false"));

        log.info("VaultPropertySourceLocator locating properties for app='{}', env='{}' from '{}'", app, env, uri);

        try {
            VaultClient client = new VaultClient(uri, vaultProperties.getTimeout());
            Map<String, Object> properties = client.fetchFlattenedProperties(app, env, apiKey);

            if (properties.isEmpty()) {
                log.info("Zero-Config Vault returned no configuration properties for app='{}', env='{}'", app, env);
                return new MapPropertySource(PROPERTY_SOURCE_NAME, Collections.emptyMap());
            }

            log.info("Successfully bound {} dynamic properties from Zero-Config Vault for app='{}', env='{}'",
                    properties.size(), app, env);
            return new MapPropertySource(PROPERTY_SOURCE_NAME, properties);

        } catch (Exception e) {
            if (failOnError) {
                log.error("Failed to locate dynamic properties from Zero-Config Vault: {}", e.getMessage());
                throw new IllegalStateException("Bootstrap failed: unable to connect to Zero-Config Vault", e);
            }
            log.warn("Unable to load dynamic configuration from Vault: {}. Proceeding with local configuration defaults.", e.getMessage());
            return new MapPropertySource(PROPERTY_SOURCE_NAME, Collections.emptyMap());
        }
    }
}
