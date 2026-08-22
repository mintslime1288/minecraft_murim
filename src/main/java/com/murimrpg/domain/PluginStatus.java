package com.murimrpg.domain;

import java.util.Objects;

public record PluginStatus(
        String pluginName,
        String pluginVersion,
        String paperApiVersion,
        String dataStoreStatus
) {
    public PluginStatus {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(paperApiVersion, "paperApiVersion");
        Objects.requireNonNull(dataStoreStatus, "dataStoreStatus");
    }
}
