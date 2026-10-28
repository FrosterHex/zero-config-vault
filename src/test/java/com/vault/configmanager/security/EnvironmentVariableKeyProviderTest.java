package com.vault.configmanager.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentVariableKeyProviderTest {

    @Test
    void outsideDevMode_rejectsMissingShortOrPublishedKeys() {
        assertThrows(IllegalStateException.class, () -> new EnvironmentVariableKeyProvider("", false));
        assertThrows(IllegalStateException.class, () -> new EnvironmentVariableKeyProvider("too-short", false));
        assertThrows(IllegalStateException.class,
                () -> new EnvironmentVariableKeyProvider(EnvironmentVariableKeyProvider.PUBLISHED_DEV_KEY, false));
    }

    @Test
    void outsideDevMode_acceptsStrongKey() {
        String key = "q8Zr2LwT5mXv9KpB3nYc6HsJ1dFg4AeU7oRi0WtV";
        assertEquals(key, new EnvironmentVariableKeyProvider(key, false).getMasterKey());
    }

    @Test
    void devMode_acceptsPublishedDevKey() {
        assertEquals(EnvironmentVariableKeyProvider.PUBLISHED_DEV_KEY,
                new EnvironmentVariableKeyProvider(EnvironmentVariableKeyProvider.PUBLISHED_DEV_KEY, true).getMasterKey());
    }
}
