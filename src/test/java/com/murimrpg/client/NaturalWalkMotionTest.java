package com.murimrpg.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NaturalWalkMotionTest {
    @Test void oppositeLimbsAlternateAndLoopWithoutAJump() {
        for (float phase = 0; phase < 7; phase += .1f) {
            var pose = pose(phase, .6f);
            var nextLoop = pose(phase + (float) (2 * Math.PI), .6f);
            assertEquals(-pose.rightLeg(), pose.leftLeg(), .00001f);
            assertEquals(-pose.rightArm(), pose.leftArm(), .00001f);
            assertTrue(pose.rightLeg() * pose.rightArm() <= 0);
            assertEquals(pose.rightLeg(), nextLoop.rightLeg(), .00001f);
            assertEquals(pose.shoulderYaw(), nextLoop.shoulderYaw(), .00001f);
            assertEquals(pose.bodyRoll(), nextLoop.bodyRoll(), .00001f);
        }
    }

    @Test void stoppingRemovesAllMotionAndDoesNotDependOnFrameRate() {
        var pose = pose(2, 0);
        assertEquals(0, pose.rightLeg(), .00001f);
        assertEquals(0, pose.rightArm(), .00001f);
        assertEquals(0, pose.shoulderYaw(), .00001f);
        assertEquals(0, pose.torsoPitch(), .00001f);
        assertEquals(pose(2, .2f), pose(2, .2f));
    }

    @Test void ordinaryMinecraftWalkSpeedProducesAVisibleButBoundedStride() {
        var pose = pose((float) (Math.PI / 2.0), 1f);
        assertTrue(Math.abs(pose.rightLeg()) > .65f, "ordinary walking must remain visibly distinct");
        assertTrue(Math.abs(pose.rightLeg()) < 1.05f, "stride must remain within configured amplitude");
        assertTrue(Math.abs(pose.rightArm()) > .35f, "empty hand must swing naturally");
        assertTrue(pose.torsoPitch() > 0f, "walk uses a bounded forward torso lean");
    }

    private static NaturalWalkMotion.Pose pose(float phase, float intensity) {
        return NaturalWalkMotion.sample(phase, intensity, 1.05f, .62f, .04f, .045f, .012f, .025f,
                .10f, .016f, 1.20f, .24f, .05f);
    }
}
