package com.murimrpg.client.render;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerMeshSettingsTest {
    @Test void loadsValidatedBlockSpaceMeshDimensions() throws Exception {
        var directory = Files.createTempDirectory("murim-player-model");
        Files.copy(getClass().getResourceAsStream("/player-model.yml"), directory.resolve("player-model.yml"));
        var settings = PlayerMeshSettings.load(directory);
        assertFalse(settings.enabled());
        assertEquals(1f, settings.widthScale(), .0001f);
        assertEquals(1f, settings.heightScale(), .0001f);
        assertEquals(1f, settings.depthScale(), .0001f);
        assertTrue(settings.flipVertical());
        assertEquals(180f, settings.yawDegrees(), .0001f);
        assertEquals(1.501f, settings.groundOffset(), .0001f);
        assertEquals(.20f, settings.locomotionBlendSeconds(), .0001f);
    }
}
