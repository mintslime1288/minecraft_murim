package com.murimrpg.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EffectConfigLoaderTest {
    @Test void loadsVisualOnlyEffectDefinitions() throws Exception {
        Path file = Files.createTempFile("effects", ".yml");
        Files.writeString(file, "effects:\n  SPARK:\n    display-name: \"불꽃\"\n    duration-ticks: 5\n    scale: 1.5\n    max-distance: 32\n    particle-count: 8\n");
        var catalog = new EffectConfigLoader().load(file);
        assertEquals(8, catalog.require("SPARK").particleCount());
        assertEquals(1.5f, catalog.require("SPARK").scale());
    }

    @Test void rejectsMissingParticleCount() throws Exception {
        Path file = Files.createTempFile("effects", ".yml");
        Files.writeString(file, "effects:\n  SPARK:\n    display-name: a\n    duration-ticks: 5\n    scale: 1\n    max-distance: 32\n");
        assertThrows(IllegalArgumentException.class, () -> new EffectConfigLoader().load(file));
    }
}
