package com.vault.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot AutoConfiguration for the Zero-Config Vault client starter.
 * Automatically activates when the starter is placed on the classpath of a client service.
 */
@AutoConfiguration
@EnableConfigurationProperties(VaultProperties.class)
public class VaultAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultClient vaultClient(VaultProperties vaultProperties) {
        return new VaultClient(vaultProperties.getUri(), vaultProperties.getTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultPropertySourceLocator vaultPropertySourceLocator(VaultProperties vaultProperties) {
        return new VaultPropertySourceLocator(vaultProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vault", name = "stream-enabled", havingValue = "true")
    public VaultSseRefreshListener vaultSseRefreshListener(VaultProperties vaultProperties,
                                                           ApplicationEventPublisher eventPublisher) {
        return new VaultSseRefreshListener(vaultProperties, eventPublisher);
    }
}
