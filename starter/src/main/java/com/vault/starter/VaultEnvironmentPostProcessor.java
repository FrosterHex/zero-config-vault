package com.vault.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Spring Boot EnvironmentPostProcessor hook.
 * Enables zero-config property binding in native Spring Boot applications
 * without requiring the Spring Cloud bootstrap context.
 */
public class VaultEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(VaultEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // If properties have already been loaded by PropertySourceLocator, avoid duplicating
        if (environment.getPropertySources().contains(VaultPropertySourceLocator.PROPERTY_SOURCE_NAME)) {
            return;
        }

        boolean enabled = Boolean.parseBoolean(environment.getProperty("vault.enabled", "true"));
        if (!enabled) {
            log.debug("Zero-Config Vault integration explicitly disabled via vault.enabled=false");
            return;
        }

        VaultPropertySourceLocator locator = new VaultPropertySourceLocator();
        PropertySource<?> propertySource = locator.locate(environment);

        if (propertySource != null && propertySource instanceof MapPropertySource) {
            MapPropertySource mapSource = (MapPropertySource) propertySource;
            if (!mapSource.getSource().isEmpty()) {
                // Add to property sources with high precedence (after command-line args and system properties)
                environment.getPropertySources().addLast(mapSource);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
