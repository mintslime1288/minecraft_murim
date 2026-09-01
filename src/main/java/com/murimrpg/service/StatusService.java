package com.murimrpg.service;

import com.murimrpg.domain.PluginStatus;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class StatusService {
    public static final String NOT_CONFIGURED = "not configured";

    private final String pluginName;
    private final String pluginVersion;
    private final String platformVersion;
    private final AtomicReference<String> dataStoreStatus = new AtomicReference<>(NOT_CONFIGURED);

    public StatusService(String pluginName, String pluginVersion, String platformVersion) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.platformVersion = Objects.requireNonNull(platformVersion, "platformVersion");
    }

    public PluginStatus status() {
        return new PluginStatus(pluginName, pluginVersion, platformVersion, dataStoreStatus.get());
    }

    public void setDataStoreStatus(String status) {
        dataStoreStatus.set(Objects.requireNonNull(status, "status"));
    }
}
