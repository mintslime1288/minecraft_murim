package com.murimrpg.client.render.wuxia;

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

/** The one-line rollback, the safe defaults, and what a malformed file does. */
class WuxiaPlayerSettingsTest {

    @TempDir Path directory;

    private WuxiaPlayerSettings write(String yaml) throws IOException {
        Path file = directory.resolve(WuxiaPlayerSettings.FILE_NAME);
        Files.writeString(file, yaml);
        return WuxiaPlayerSettings.parse(YamlDocument.load(file));
    }

    @Test
    @DisplayName("the shipped resource default is disabled")
    void shippedDefaultIsOff() throws IOException {
        try (var input = WuxiaPlayerSettings.class.getResourceAsStream("/" + WuxiaPlayerSettings.FILE_NAME)) {
            assertTrue(input != null, "the shipped YAML must exist");
            Path file = directory.resolve("shipped.yml");
            Files.write(file, input.readAllBytes());
            assertFalse(WuxiaPlayerSettings.parse(YamlDocument.load(file)).enabled(),
                    "the shipped default must never draw the replacement body");
        }
    }

    @Test
    @DisplayName("enabled:false is the whole rollback; no other key can switch it back on")
    void disabledIsAnAbsoluteOff() throws IOException {
        var settings = write("""
                enabled: false
                scale:
                  target-height-blocks: 2.0
                compatibility:
                  allow-held-items: true
                  allow-armour: true
                """);
        assertFalse(settings.enabled());
        assertFalse(settings.allowHeldItems());
        assertFalse(settings.allowArmour());
        assertEquals(WuxiaPlayerSettings.disabled(), settings);
    }

    @Test
    @DisplayName("a missing enabled key stays off rather than defaulting on")
    void missingKeyStaysOff() throws IOException {
        assertFalse(write("scale:\n  target-height-blocks: 1.8\n").enabled());
    }

    @Test
    @DisplayName("an empty or malformed file yields the disabled settings, never an exception")
    void malformedInputIsSafe() throws IOException {
        assertFalse(WuxiaPlayerSettings.parse(null).enabled());
        assertFalse(write("enabled: \"yes please\"\n").enabled(),
                "a non-boolean enabled value must not count as true");
        // An empty document makes YamlDocument.load throw. load() must absorb that and leave the
        // existing player body alone rather than letting the exception escape into client start.
        Files.writeString(directory.resolve(WuxiaPlayerSettings.FILE_NAME), "");
        assertFalse(WuxiaPlayerSettings.load(directory).enabled());
    }

    @Test
    @DisplayName("out-of-range numbers clamp instead of throwing, so one typo cannot kill a frame")
    void numbersClampRatherThanThrow() throws IOException {
        var settings = write("""
                enabled: true
                scale:
                  target-height-blocks: 900.0
                animation:
                  maximum-playback-rate: -5.0
                  transition-seconds: 0.0
                """);
        assertTrue(settings.enabled());
        assertEquals(3f, settings.targetHeightBlocks(), 1.0e-6);
        assertEquals(.25f, settings.maximumPlaybackRate(), 1.0e-6);
        assertEquals(.02f, settings.transitionSeconds(), 1.0e-6);
    }

    @Test
    @DisplayName("render scale maps the measured bind height onto the configured target")
    void renderScaleUsesTheMeasuredSourceHeight() throws IOException {
        var settings = write("enabled: true\nscale:\n  target-height-blocks: 1.8\n  source-height-metres: 1.7\n");
        assertEquals(1.8f / 1.7f, settings.renderScale(), 1.0e-6);
        assertEquals(1.0588235f, settings.renderScale(), 1.0e-6);
        var tall = write("enabled: true\nscale:\n  target-height-blocks: 2.0\n  source-height-metres: 1.7\n");
        assertEquals(20f / 17f, tall.renderScale(), 1.0e-6);
    }

    @Test
    @DisplayName("the measured humanoid-stack floor correction survives config parsing")
    void acceptsMeasuredGroundOffset() throws IOException {
        var settings = write("enabled: true\nplacement:\n  ground-offset-blocks: 1.501\n");
        assertEquals(1.501f, settings.groundOffsetBlocks(), 1.0e-6);
    }

    @Test
    @DisplayName("grounded clips keep the vertical hip channel and the jump clip does not")
    void rootPolicyDiffersForTheJump() throws IOException {
        var settings = write("enabled: true\n");
        for (WuxiaLocomotion.Clip clip : new WuxiaLocomotion.Clip[]{WuxiaLocomotion.Clip.IDLE,
                WuxiaLocomotion.Clip.WALK, WuxiaLocomotion.Clip.RUN, WuxiaLocomotion.Clip.CROUCH_WALK}) {
            assertEquals(WuxiaRig.RootPolicy.LOCK_HORIZONTAL, settings.rootPolicy(clip), clip.name());
        }
        assertEquals(WuxiaRig.RootPolicy.LOCK_ALL, settings.rootPolicy(WuxiaLocomotion.Clip.JUMP));
    }

    @Test
    @DisplayName("the jump sub-range maps phase 0..1 onto the configured slice of the clip")
    void jumpPhaseMapsOntoTheSubRange() throws IOException {
        var settings = write("enabled: true\nanimation:\n  jump:\n    clip-start: 0.0\n    clip-end: 0.35\n");
        float authored = 1.9583f;
        assertEquals(0f, settings.clipTimeSeconds(WuxiaLocomotion.Clip.JUMP, 0f, authored), 1.0e-5);
        assertEquals(authored * .35f,
                settings.clipTimeSeconds(WuxiaLocomotion.Clip.JUMP, 1f, authored), 1.0e-4);
        assertEquals(authored * .175f,
                settings.clipTimeSeconds(WuxiaLocomotion.Clip.JUMP, .5f, authored), 1.0e-4);
        // A gait clip wraps instead, so a phase of 1.25 is a quarter of the way through.
        assertEquals(authored * .25f,
                settings.clipTimeSeconds(WuxiaLocomotion.Clip.WALK, 1.25f, authored), 1.0e-4);
    }

    @Test
    @DisplayName("equipment compatibility ships off, because armour follows Mojang's model")
    void equipmentCompatibilityShipsOff() throws IOException {
        var settings = write("enabled: true\n");
        assertFalse(settings.allowHeldItems());
        assertFalse(settings.allowArmour());
    }
}
