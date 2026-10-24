package com.murimrpg.client.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GlbAnimatedPlayerMeshTest {
    @Test void loadsTheRiggedMeshyWalkingAsset() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/walking.glb")) {
            Assumptions.assumeTrue(input != null, "licensed walking GLB is not in this checkout");
            var loaded = GlbAnimatedPlayerMesh.load(input, "test-player-walk");
            // This is a user-provided animation-only clip. Full-body Meshy rendering is
            // disabled by default; the stable contract is a valid animated rig, not a
            // fixed source mesh density or a world-space skinning bound.
            assertTrue(loaded.mesh().vertexCount() > 1_000);
            assertTrue(loaded.mesh().triangleCount() > 1_000);
            assertEquals(24, loaded.mesh().jointCount());
            assertTrue(loaded.mesh().durationSeconds() > .5f);
            assertNotNull(loaded.baseColorImage());
            assertTrue(loaded.baseColorImage().length > 1_000);
            System.out.println("PLAYER_GLB_BOUNDS=" + loaded.mesh().bounds());
        }
    }

    @Test void walkingRunningAndFastRunningAssetsExposeAnimatedHumanoidSkeletons() throws Exception {
        try (var walkInput = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/walking.glb");
             var runInput = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/running.glb");
             var fastInput = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/run_fast.glb")) {
            Assumptions.assumeTrue(walkInput != null && runInput != null && fastInput != null,
                    "licensed locomotion GLBs are not in this checkout");
            var walk = GlbAnimatedPlayerMesh.load(walkInput, "walk").mesh();
            var run = GlbAnimatedPlayerMesh.load(runInput, "run").mesh();
            var fast = GlbAnimatedPlayerMesh.load(fastInput, "fast-run").mesh();
            assertEquals(24, walk.jointCount());
            assertEquals(24, run.jointCount());
            assertEquals(24, fast.jointCount());
            assertTrue(walk.durationSeconds() > .1f);
            assertTrue(run.durationSeconds() > .1f);
            assertTrue(fast.durationSeconds() > .1f);
        }
    }

    @Test void rejectsTheCurrentAssetBeforeBrokenSkinningCanReachTheRenderer() throws Exception {
        Path original = Path.of("assets-source", "meshy", "murim_player_base", "rigged", "walking.glb");
        Assumptions.assumeTrue(Files.isRegularFile(original), "licensed source GLB is not in this checkout");
        try (var input = Files.newInputStream(original)) {
            var mesh = GlbAnimatedPlayerMesh.load(input, "walk").mesh();
            var bounds = mesh.skinnedBounds(0f);
            assertTrue(bounds.height() > 10f, "fixture must demonstrate the unsafe direct skinning result");
            var error = assertThrows(java.io.IOException.class,
                    () -> GlbAssetValidator.validatePlayerClip(mesh, "walking.glb"));
            assertTrue(error.getMessage().contains("skinning validation failed"));
        }
    }

    @Test void blenderRebakedCandidatesHaveSafeSkinnedBounds() throws Exception {
        for (String name : new String[]{"walking-rebaked.glb", "running-rebaked.glb"}) {
            Path candidate = Path.of("assets-source", "meshy", "murim_player_base", "rebaked", name);
            Assumptions.assumeTrue(Files.isRegularFile(candidate), name + " is not in this checkout");
            try (var input = Files.newInputStream(candidate)) {
                var mesh = GlbAnimatedPlayerMesh.load(input, name).mesh();
                GlbAssetValidator.validatePlayerClip(mesh, name);
            }
        }
    }
}
