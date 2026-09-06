package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClientVisualSettingsTest {
    @Test void loadsVisualPreferencesFromYaml() throws Exception {
        Path directory = Files.createTempDirectory("murimrpg-client-visuals");
        Files.writeString(directory.resolve("client-visuals.yml"), "quality: HIGH\nidle-breath-radians: 0.02\nwalk-arm-sway-radians: 0.03\nsprint-arm-sway-radians: 0.05\nfootstep-interval-ticks: 6\nmax-particles: 40\n");
        ClientVisualSettings settings = ClientVisualSettings.load(directory);
        assertEquals(ClientVisualSettings.Quality.HIGH, settings.quality());
        assertEquals(6, settings.footstepIntervalTicks());
    }
}
