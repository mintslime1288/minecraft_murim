package com.murimrpg.client.render;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxingConfigurationTest {
    @Test void shippedConfigurationLoadsWithoutSilentlyDisablingCombat() {
        var settings = ArticulatedPlayerSettings.load(Path.of("src/main/resources"));
        assertNull(settings.combatMotionFailure());
        assertNull(settings.combatMotion().stanceFailure());
        assertTrue(settings.limbSwingScale() < .6662f);
        assertTrue(settings.runLimbSwingScale() > .6662f);
        assertEquals(70f, settings.runElbowDegrees());
        assertTrue(settings.combatMotion().randomHands());
        assertEquals(10f, settings.combatMotion().inwardYawDegrees());
    }
}
