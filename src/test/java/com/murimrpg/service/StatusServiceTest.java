package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.murimrpg.domain.PluginStatus;
import org.junit.jupiter.api.Test;

class StatusServiceTest {
    @Test
    void reportsConfiguredMetadataAndInitialDataStoreState() {
        StatusService service = new StatusService("MurimRPG", "0.1.0", "26.2.build.112-stable");

        PluginStatus status = service.status();

        assertAll(
                () -> assertEquals("MurimRPG", status.pluginName()),
                () -> assertEquals("0.1.0", status.pluginVersion()),
                () -> assertEquals("26.2.build.112-stable", status.paperApiVersion()),
                () -> assertEquals("not configured", status.dataStoreStatus())
        );
    }

    @Test
    void reportsDataStoreStateTransitionAfterInitialization() {
        StatusService service = new StatusService("MurimRPG", "0.1.0", "26.2.build.112-stable");

        service.setDataStoreStatus("configured (SQLite)");

        assertEquals("configured (SQLite)", service.status().dataStoreStatus());
    }
}
