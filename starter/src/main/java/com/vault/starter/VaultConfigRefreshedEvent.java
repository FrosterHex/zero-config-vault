package com.vault.starter;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when dynamic configuration updates are received via MongoDB Change Stream SSE channel.
 */
public class VaultConfigRefreshedEvent extends ApplicationEvent {

    private final String applicationName;
    private final String environment;
    private final int version;
    private final String action;

    public VaultConfigRefreshedEvent(Object source, String applicationName, String environment, int version, String action) {
        super(source);
        this.applicationName = applicationName;
        this.environment = environment;
        this.version = version;
        this.action = action;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public int getVersion() {
        return version;
    }

    public String getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "VaultConfigRefreshedEvent{" +
                "app='" + applicationName + '\'' +
                ", env='" + environment + '\'' +
                ", version=" + version +
                ", action='" + action + '\'' +
                '}';
    }
}
