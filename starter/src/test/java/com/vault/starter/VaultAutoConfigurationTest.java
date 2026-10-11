package com.vault.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class VaultAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VaultAutoConfiguration.class));

    @Test
    void defaultConfiguration_registersVaultClientAndLocator() {
        contextRunner
                .withPropertyValues("vault.uri=http://localhost:8080", "vault.application-name=test-app")
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultClient.class);
                    assertThat(context).hasSingleBean(VaultPropertySourceLocator.class);
                    assertThat(context).doesNotHaveBean(VaultSseRefreshListener.class);
                });
    }

    @Test
    void withStreamEnabled_registersSseRefreshListener() {
        contextRunner
                .withPropertyValues(
                        "vault.uri=http://localhost:8080",
                        "vault.application-name=test-app",
                        "vault.stream-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultClient.class);
                    assertThat(context).hasSingleBean(VaultPropertySourceLocator.class);
                    assertThat(context).hasSingleBean(VaultSseRefreshListener.class);
                });
    }
}
