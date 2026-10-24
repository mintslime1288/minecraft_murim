package com.murimrpg.client.render.scabbard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The off switch, the safe defaults, and what a bad identifier does. */
class ScabbardSettingsTest {

    @TempDir Path directory;

    private ScabbardSettings write(String yaml) throws IOException {
        Path file = directory.resolve(ScabbardSettings.FILE_NAME);
        Files.writeString(file, yaml);
        return ScabbardSettings.parse(YamlDocument.load(file));
    }

    @Test
    @DisplayName("the shipped resource default is disabled")
    void shippedDefaultIsOff() throws IOException {
        try (var input = ScabbardSettings.class.getResourceAsStream("/" + ScabbardSettings.FILE_NAME)) {
            assertTrue(input != null, "the shipped YAML must exist");
            Path file = directory.resolve("shipped.yml");
            Files.write(file, input.readAllBytes());
            assertFalse(ScabbardSettings.parse(YamlDocument.load(file)).enabled());
        }
    }

    @Test
    @DisplayName("enabled:false is the whole rollback and no other key overrides it")
    void disabledIsAbsolute() throws IOException {
        var settings = write("""
                enabled: false
                sword-item-model: "murimrpg:something_else"
                render:
                  first-person: true
                attack:
                  right-arm-only: true
                """);
        assertEquals(ScabbardSettings.disabled(), settings);
        assertFalse(settings.enabled());
    }

    @Test
    @DisplayName("a missing or non-boolean enabled key stays off")
    void missingKeyStaysOff() throws IOException {
        assertFalse(write("sword-item-model: \"murimrpg:top_straight_sword\"\n").enabled());
        assertFalse(write("enabled: \"yes\"\n").enabled());
        assertFalse(ScabbardSettings.parse(null).enabled());
    }

    @Test
    @DisplayName("an empty file is absorbed by load() instead of escaping into client start")
    void emptyFileIsSafe() throws IOException {
        Files.writeString(directory.resolve(ScabbardSettings.FILE_NAME), "");
        assertFalse(ScabbardSettings.load(directory).enabled());
    }

    @Test
    @DisplayName("the defaults are the sword and scabbard the user actually saved")
    void defaultsPointAtTheUsersModels() throws IOException {
        var settings = write("enabled: true\n");
        assertTrue(settings.enabled());
        assertEquals("murimrpg:top_straight_sword", settings.swordItemModel());
        assertEquals("murimrpg:extended_black_scabbard", settings.scabbardItemModel());
        assertFalse(settings.matchSwordTagInstead(), "matching the whole sword tag must be opt-in");
        assertTrue(settings.firstPerson());
        assertTrue(settings.thirdPerson());
        assertTrue(settings.rightArmOnlyAttack());
    }

    @Test
    @DisplayName("a malformed identifier falls back instead of reaching the model manager")
    void malformedIdentifierFallsBack() throws IOException {
        var settings = write("""
                enabled: true
                sword-item-model: "Not An Identifier!"
                scabbard-item-model: "murimrpg:a:b"
                """);
        assertEquals("murimrpg:top_straight_sword", settings.swordItemModel());
        assertEquals("murimrpg:extended_black_scabbard", settings.scabbardItemModel());
    }

    @Test
    @DisplayName("identifier validation accepts legal ids and rejects the illegal ones")
    void identifierValidation() {
        assertTrue(ScabbardSettings.isWellFormed("murimrpg:top_straight_sword"));
        assertTrue(ScabbardSettings.isWellFormed("minecraft:item/foo-bar.baz"));
        assertFalse(ScabbardSettings.isWellFormed("no_colon"));
        assertFalse(ScabbardSettings.isWellFormed(":leading"));
        assertFalse(ScabbardSettings.isWellFormed("trailing:"));
        assertFalse(ScabbardSettings.isWellFormed("two:colons:here"));
        assertFalse(ScabbardSettings.isWellFormed("murimrpg:Upper"));
        assertFalse(ScabbardSettings.isWellFormed("murimrpg:with space"));
    }

    @Test
    @DisplayName("each render path can be turned off on its own")
    void eachViewCanBeDisabledSeparately() throws IOException {
        var firstOnly = write("enabled: true\nrender:\n  first-person: true\n  third-person: false\n");
        assertTrue(firstOnly.firstPerson());
        assertFalse(firstOnly.thirdPerson());
        var thirdOnly = write("enabled: true\nrender:\n  first-person: false\n  third-person: true\n");
        assertFalse(thirdOnly.firstPerson());
        assertTrue(thirdOnly.thirdPerson());
    }

    @Test
    @DisplayName("the right-arm-only attack can be turned off without turning the scabbard off")
    void attackClipIsIndependentlySwitchable() throws IOException {
        var settings = write("enabled: true\nattack:\n  right-arm-only: false\n");
        assertTrue(settings.enabled());
        assertFalse(settings.rightArmOnlyAttack());
    }
}
