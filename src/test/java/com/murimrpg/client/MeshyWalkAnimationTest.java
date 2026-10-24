package com.murimrpg.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

class MeshyWalkAnimationTest {
    @Test void readsAndSamplesRealRiggedWalkingKeyframes() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/walking.glb")) {
            Assumptions.assumeTrue(input != null, "licensed walking GLB is not in this checkout");
            var animation = MeshyWalkAnimation.parse(input);
            var poseA = animation.sample(1.5f, .286f);
            var poseB = animation.sample(5.5f, .286f);
            assertTrue(Float.isFinite(poseA.rightArm().x));
            assertTrue(Float.isFinite(poseA.leftLeg().x));
            assertNotEquals(poseA.rightLeg().x, poseB.rightLeg().x, .0001f,
                    "different walk phases must produce different leg rotations");
            assertTrue(Math.abs(poseA.rightArm().x) + Math.abs(poseA.leftArm().x) > .02f,
                    "real GLB arm animation must reach the Minecraft model");
        }
    }

    @Test void stoppingFadesRetargetedAnimationToZero() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/walking.glb")) {
            Assumptions.assumeTrue(input != null, "licensed walking GLB is not in this checkout");
            var pose = MeshyWalkAnimation.parse(input).sample(2f, 0f);
            assertEquals(0f, pose.rightArm().length(), .00001f);
            assertEquals(0f, pose.leftLeg().length(), .00001f);
            assertEquals(0f, pose.body().length(), .00001f);
        }
    }

    @Test void readsAndSamplesRealRiggedRunningKeyframes() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/running.glb")) {
            Assumptions.assumeTrue(input != null, "licensed running GLB is not in this checkout");
            var animation = MeshyWalkAnimation.parse(input);
            var poseA = animation.sample(1.2f, .35f);
            var poseB = animation.sample(4.8f, .35f);
            assertTrue(Float.isFinite(poseA.body().x));
            assertNotEquals(poseA.leftLeg().x, poseB.leftLeg().x, .0001f);
        }
    }

    @Test void readsTheUserProvidedFastRunningClipWithLowerLimbBones() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/run_fast.glb")) {
            Assumptions.assumeTrue(input != null, "licensed fast-running GLB is not in this checkout");
            var animation = MeshyWalkAnimation.parse(input);
            var pose = animation.sample(2.4f, .4f);
            assertTrue(Float.isFinite(pose.rightForeArm().x));
            assertTrue(Float.isFinite(pose.leftLowerLeg().x));
        }
    }

    @Test void nativeMurimClipsProvideSafeSeparateElbowAndKneeRotations() throws Exception {
        for (String name : new String[]{"native_walk.glb", "native_run.glb", "native_fast_run.glb"}) {
            try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/" + name)) {
                Assumptions.assumeTrue(input != null, name + " is not in this checkout");
                var animation = MeshyWalkAnimation.parse(input);
                var pose = animation.sample(4.8f, .4f);
                assertTrue(pose.rightForeArm().length() < 1.5f);
                assertTrue(pose.leftLowerLeg().length() < 1.5f);
            }
        }
    }

    @Test void userProvidedHumanoidMeshyWalkIsPackagedAndRetargetsSafely() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/meshy_humanoid_walk.glb")) {
            Assumptions.assumeTrue(input != null, "licensed Humanoid-rigged walk GLB is not in this checkout");
            var animation = MeshyWalkAnimation.parse(input);
            float greatestArmRotation = 0f;
            float greatestLegRotation = 0f;
            for (int frame = 0; frame < 60; frame++) {
                var pose = animation.sample(frame / 10f, .40f);
                assertTrue(Float.isFinite(pose.rightForeArm().x));
                assertTrue(Float.isFinite(pose.leftLowerLeg().x));
                assertTrue(pose.rightArm().length() < 1.5f);
                assertTrue(pose.leftLeg().length() < 1.5f);
                assertTrue(pose.body().length() < .6f);
                greatestArmRotation = Math.max(greatestArmRotation, Math.abs(pose.rightArm().x));
                greatestLegRotation = Math.max(greatestLegRotation, Math.abs(pose.leftLeg().x));
            }
            assertTrue(greatestArmRotation > .10f, "Humanoid walk must visibly rotate an upper arm");
            assertTrue(greatestLegRotation > .10f, "Humanoid walk must visibly rotate an upper leg");
            var start = animation.sample(0f, .40f);
            var middle = animation.sample(4.71f, .40f);
            assertNotEquals(start.rightArm().x, middle.rightArm().x, .02f,
                    "the retargeted upper arm must change across a walk cycle");
            assertNotEquals(start.leftLeg().x, middle.leftLeg().x, .02f,
                    "the retargeted upper leg must change across a walk cycle");
        }
    }

    @Test void retargetedPoseStaysWithinSafeHumanLimits() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/murimrpg/models_glb/player/running.glb")) {
            Assumptions.assumeTrue(input != null, "licensed running GLB is not in this checkout");
            var animation = MeshyWalkAnimation.parse(input);
            for (int frame = 0; frame < 60; frame++) {
                var pose = animation.sample(frame / 10f, .35f);
                assertTrue(pose.rightArm().length() < 1.5f);
                assertTrue(pose.leftLeg().length() < 1.5f);
                assertTrue(pose.body().length() < .6f);
            }
        }
    }
}
