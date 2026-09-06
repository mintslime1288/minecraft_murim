package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PalAnimationSettingsTest {
    @Test void defaultProviderPreferenceKeepsMurimMotionInControl(@TempDir Path directory) {
        var settings = PalAnimationSettings.load(directory);

        assertFalse(settings.enabled(), "The safe default must keep custom player motion disabled until manually validated");
        assertFalse(settings.preferThirdPartyProvider(),
                "An installed optional provider must not silently bypass MurimRPG's own animation clips");
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
