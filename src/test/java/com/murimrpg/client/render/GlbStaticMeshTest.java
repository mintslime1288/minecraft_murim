package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class GlbStaticMeshTest {
    @Test
    void loadsTheBundledTrainingLogMeshAndBaseColorTexture() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "assets/murimrpg/models_glb/training_dummy_log_pillar/model.glb")) {
            assertNotNull(input, "Bundled training-dummy GLB is missing");
            var model = GlbStaticMesh.load(input, "bundled training dummy");
            assertTrue(model.mesh().triangleCount() > 1_000, "Training log should retain detailed geometry");
            assertTrue(model.mesh().triangleCount() <= 2_500, "Training log must remain practical for the real-time client renderer");
            assertNotNull(model.baseColorImage(), "Training log needs an embedded base-color texture");
            assertTrue(model.baseColorImage().length > 10_000, "Training log texture should not be an empty placeholder");
        }
    }

    @Test
    void bundlesTheContinuousBarkTextureUsedByTheClientRenderer() {
        assertNotNull(getClass().getClassLoader().getResource(
                "assets/murimrpg/textures/entity/training_dummy_log_continuous.png"),
                "Continuous bark texture is missing");
    }

    @Test
    void closedTrainingLogMeshHasAPracticalFullyCappedQuadBudget() {
        var mesh = new TrainingLogPillarMesh();
        assertTrue(mesh.quadCount() >= 400, "Log needs a smooth cylindrical silhouette");
        assertTrue(mesh.quadCount() < 500, "Log must remain cheap when several are installed");
    }
}
