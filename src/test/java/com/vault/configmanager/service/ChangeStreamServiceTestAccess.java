package com.vault.configmanager.service;

/**
 * Lets tests outside the service package reach the package-private test hooks of {@link ChangeStreamService}.
 */
public final class ChangeStreamServiceTestAccess {

    private ChangeStreamServiceTestAccess() {
    }

    public static void closeCurrentCursor(ChangeStreamService service) {
        service.closeCurrentCursorForTest();
    }

    public static int reconnectCount(ChangeStreamService service) {
        return service.reconnectCountForTest();
    }
}
