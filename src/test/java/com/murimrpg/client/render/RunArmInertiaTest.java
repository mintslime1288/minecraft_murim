package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunArmInertiaTest {
    private static final float LIMIT = (float) Math.toRadians(3);

    @Test void disabledAndIneligibleStatesLeaveAuthoredPoseUntouched() {
        var lag = new RunArmInertia();
        assertFalse(lag.advance(0, 0, LIMIT, 1, .3f, -.3f));
        assertFalse(lag.advance(0, 1, 0, 1, .3f, -.3f));
        assertFalse(lag.advance(0, 1, LIMIT, 0, .3f, -.3f));
        assertEquals(0, RunArmInertia.weight(0, 1, 1, false));
        assertEquals(0, RunArmInertia.weight(1, 0, 1, false));
        assertEquals(0, RunArmInertia.weight(1, 1, 0, false));
        assertEquals(0, RunArmInertia.weight(1, 1, 1, true));
        assertEquals(.125f, RunArmInertia.weight(.5f, .5f, .5f, false));
    }

    @Test void correctionIsBoundedAndArmsKeepOppositePhaseThroughReversals() {
        var lag = new RunArmInertia();
        for (int i = 0; i < 1000; i++) {
            float target = (float) Math.sin(i * .07) * .65f;
            assertTrue(lag.advance(i * .1f, .75f, LIMIT, 1, target, -target));
            assertEquals(-lag.right(), lag.left(), 1e-6f);
            assertTrue(Math.abs(lag.right() - target) <= LIMIT + 1e-6f);
            assertTrue(Math.abs(lag.right()) <= .65f + 1e-6f);
        }
    }

    @Test void repeatedSubmissionAtTheSameAgeDoesNotAdvanceAgain() {
        var lag = new RunArmInertia();
        lag.advance(0, 1, LIMIT, 1, 0, 0);
        lag.advance(.25f, 1, LIMIT, 1, .02f, -.02f);
        float first = lag.right();
        lag.advance(.25f, 1, LIMIT, 1, .02f, -.02f);
        assertEquals(first, lag.right(), 0f);
    }

    @Test void constantTargetResponseIsIndependentOfRenderFrameRate() {
        float[] results = new float[3];
        int[] frames = {30, 60, 144};
        for (int rate = 0; rate < frames.length; rate++) {
            var lag = new RunArmInertia();
            lag.advance(0, 2, LIMIT, 1, 0, 0);
            for (int frame = 1; frame <= frames[rate]; frame++)
                lag.advance(frame * 4f / frames[rate], 2, LIMIT, 1, .03f, -.03f);
            results[rate] = lag.right();
        }
        assertEquals(results[0], results[1], 1e-6f);
        assertEquals(results[0], results[2], 1e-6f);
    }

    @Test void runBlendScalesOnlyTheCorrection() {
        var full = new RunArmInertia();
        var half = new RunArmInertia();
        full.advance(0, 1, LIMIT, 1, 0, 0);
        half.advance(0, 1, LIMIT, .5f, 0, 0);
        full.advance(.2f, 1, LIMIT, 1, .1f, -.1f);
        half.advance(.2f, 1, LIMIT, .5f, .1f, -.1f);
        assertEquals((full.right() - .1f) * .5f, half.right() - .1f, 1e-6f);
    }

    @Test void resetJumpOrFallbackReseedsOnTheNewPose() {
        var lag = new RunArmInertia();
        lag.advance(10, 2, LIMIT, 1, .4f, -.4f);
        lag.advance(11, 2, LIMIT, 1, -.4f, .4f);
        lag.reset();
        lag.advance(12, 2, LIMIT, 1, .2f, -.2f);
        assertEquals(.2f, lag.right(), 0f);
        assertEquals(-.2f, lag.left(), 0f);
        lag.advance(100, 2, LIMIT, 1, -.1f, .1f);
        assertEquals(-.1f, lag.right(), 0f);
        lag.advance(1, 2, LIMIT, 1, .1f, -.1f);
        assertEquals(.1f, lag.right(), 0f);
    }

    @Test void invalidInputsFailClosed() {
        var lag = new RunArmInertia();
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(lag.advance(invalid, 1, LIMIT, 1, 0, 0));
            assertFalse(lag.advance(0, invalid, LIMIT, 1, 0, 0));
            assertFalse(lag.advance(0, 1, invalid, 1, 0, 0));
            assertFalse(lag.advance(0, 1, LIMIT, invalid, 0, 0));
            assertFalse(lag.advance(0, 1, LIMIT, 1, invalid, 0));
            assertFalse(lag.advance(0, 1, LIMIT, 1, 0, invalid));
        }
        assertFalse(lag.advance(0, 1, 1, 1, 0, 0));
        assertTrue(lag.advance(0, 1, (float) Math.toRadians(6), 1, 0, 0));
    }

    @Test void runtimeYamlDefaultsAndValidation(@TempDir Path directory) throws Exception {
        Path yaml = directory.resolve("articulated-player.yml");
        Files.writeString(yaml, "enabled: true\n");
        assertEquals(0f, ArticulatedPlayerSettings.load(directory).runArmFollowTicks());
        Files.writeString(yaml, "run:\n  arm-follow-ticks: 0.75\n  arm-lag-degrees: 3.0\n");
        var settings = ArticulatedPlayerSettings.load(directory);
        assertEquals(.75f, settings.runArmFollowTicks());
        assertEquals(3f, settings.runArmLagDegrees());
        Files.writeString(yaml, "run:\n  arm-lag-degrees: 6.1\n");
        assertThrows(IllegalArgumentException.class, () -> ArticulatedPlayerSettings.load(directory));
    }
}
