package com.murimrpg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UiSettingsTest {
    @Test
    void loadsHudRefreshSettingsFromYaml() throws Exception {
        Path path = Path.of(getClass().getClassLoader().getResource("ui.yml").toURI());

        UiSettings settings = UiSettings.load(path);

        assertEquals(10, settings.refreshTicks());
        assertEquals(40, settings.transientMessageTicks());
    }
}
