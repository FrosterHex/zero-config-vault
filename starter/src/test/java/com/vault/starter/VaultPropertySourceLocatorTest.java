package com.vault.starter;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class VaultPropertySourceLocatorTest {

    @Test
    void locate_gracefulFallbackWhenUnreachable_returnsEmptyMapSource() {
        VaultProperties props = new VaultProperties("http://localhost:59999", "payment-service", "test", "dummy-key");
        props.setFailOnError(false);
        props.setTimeout(100);

        VaultPropertySourceLocator locator = new VaultPropertySourceLocator(props);

        MockEnvironment env = new MockEnvironment();
        env.setProperty("vault.uri", "http://localhost:59999");
        env.setProperty("vault.fail-on-error", "false");

        PropertySource<?> source = locator.locate(env);

        assertNotNull(source);
        assertEquals(VaultPropertySourceLocator.PROPERTY_SOURCE_NAME, source.getName());
        assertTrue(source instanceof MapPropertySource);
        MapPropertySource mapSource = (MapPropertySource) source;
        assertTrue(mapSource.getSource().isEmpty(), "Unreachable vault should yield empty source when failOnError=false");
    }

    @Test
    void locate_whenFailOnErrorTrueAndUnreachable_throwsException() {
        VaultProperties props = new VaultProperties("http://localhost:59999", "payment-service", "test", "dummy-key");
        props.setFailOnError(true);
        props.setTimeout(100);

        VaultPropertySourceLocator locator = new VaultPropertySourceLocator(props);

        MockEnvironment env = new MockEnvironment();
        env.setProperty("vault.uri", "http://localhost:59999");
        env.setProperty("vault.fail-on-error", "true");

        assertThrows(IllegalStateException.class, () -> locator.locate(env));
    }
}
