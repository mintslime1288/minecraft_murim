package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CharacterSettingsTest {
    @Test
    void loadsLiteralDecimalTalentKeysFromYaml() throws Exception {
        Path path = Path.of(getClass().getClassLoader().getResource("character.yml").toURI());
        CharacterSettings settings = CharacterSettings.load(path);
        assertEquals(10_000, settings.talentWeights().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(10_000, settings.constitutionWeights().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(5, settings.maxRerolls());
    }
}
