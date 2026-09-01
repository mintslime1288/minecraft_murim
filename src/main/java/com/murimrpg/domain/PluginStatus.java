package com.murimrpg.domain;

import java.util.Objects;

public record PluginStatus(
        String pluginName,
        String pluginVersion,
        String platformVersion,
        String dataStoreStatus
) {
    public PluginStatus {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(platformVersion, "platformVersion");
        Objects.requireNonNull(dataStoreStatus, "dataStoreStatus");
    }
}
