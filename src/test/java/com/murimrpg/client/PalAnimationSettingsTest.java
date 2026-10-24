package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PalAnimationSettingsTest {
    @Test void defaultConfigurationEnablesMurimPalAndKeepsItInControl(@TempDir Path directory) {
        var settings = PalAnimationSettings.load(directory);

        assertTrue(settings.enabled(), "The shipped default uses the validated PAL and Bendable Cuboids path");
        org.junit.jupiter.api.Assertions.assertFalse(settings.preferThirdPartyProvider(),
                "An installed optional provider must not silently bypass MurimRPG's own animation clips");
        assertTrue(settings.boxingRandomHands());
        org.junit.jupiter.api.Assertions.assertEquals(60, settings.boxingGuardHoldTicks());
        org.junit.jupiter.api.Assertions.assertTrue(settings.movingExitThreshold() < settings.movingThreshold(),
                "The stop threshold must be below the start threshold to prevent gait flicker");
        org.junit.jupiter.api.Assertions.assertTrue(settings.fastRunExitThreshold() < settings.fastRunThreshold(),
                "The fast-run exit threshold must prevent RUN/FAST_RUN oscillation");
        assertTrue(settings.fullBodyFollowEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(.35, settings.shoulderPitchFollow(), 1.0e-9);
    }

    @Test void explicitProviderOptInIsStillSupported(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("pal-player-animation.yml"), """
                enabled: true
                third-party-provider:
                  prefer-when-installed: true
                  mod-id: cpa
                """);

        assertTrue(PalAnimationSettings.load(directory).preferThirdPartyProvider());
    }
}
