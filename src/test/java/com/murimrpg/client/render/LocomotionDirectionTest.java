package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class LocomotionDirectionTest {
    private static final float TOLERANCE = 1.0e-4f;
    private static final float STRAFE_WEIGHT = .35f;
    private static final float DEADBAND = .012f;

    private static LocomotionDirection of(double x, double z, float yaw, boolean previouslyReversed) {
        return LocomotionDirection.of(x, z, yaw, STRAFE_WEIGHT, DEADBAND, previouslyReversed);
    }

    @Test void treatsMotionAlongTheBodyFacingAsForward() {
        var forward = of(0d, .2d, 0f, false);
        assertEquals(.2f, forward.forward(), TOLERANCE);
        assertEquals(0f, forward.strafe(), TOLERANCE);
        assertFalse(forward.reversed());
        assertEquals(1f, forward.gaitWeight(), TOLERANCE);
        var backward = of(0d, -.2d, 0f, false);
        assertEquals(-.2f, backward.forward(), TOLERANCE);
        assertTrue(backward.reversed());
        assertEquals(1f, backward.gaitWeight(), TOLERANCE);
    }

    @Test void followsTheBodyYawRatherThanTheWorldAxes() {
        assertTrue(of(0d, .2d, 180f, false).reversed());
        var west = of(-.2d, 0d, 90f, false);
        assertEquals(.2f, west.forward(), TOLERANCE);
        assertFalse(west.reversed());
    }

    @Test void reportsSidewaysTravelAsStrafeAndDampsTheGait() {
        var right = of(-.2d, 0d, 0f, false);
        assertEquals(0f, right.forward(), TOLERANCE);
        assertEquals(.2f, right.strafe(), TOLERANCE);
        assertEquals(STRAFE_WEIGHT, right.gaitWeight(), TOLERANCE);
        assertFalse(right.reversed());
        var left = of(.2d, 0d, 0f, false);
        assertEquals(-.2f, left.strafe(), TOLERANCE);
        assertEquals(STRAFE_WEIGHT, left.gaitWeight(), TOLERANCE);
    }

    @Test void blendsTheGaitWeightForDiagonalTravel() {
        var diagonal = of(-.1414d, .1414d, 0f, false);
        assertEquals(.1414f, diagonal.forward(), TOLERANCE);
        assertEquals(.1414f, diagonal.strafe(), TOLERANCE);
        assertEquals(.9546f, diagonal.gaitWeight(), 1.0e-3f);
        assertFalse(diagonal.reversed());
    }

    @Test void keepsThePreviousDecisionInsideTheDeadband() {
        assertTrue(of(0d, .005d, 0f, true).reversed());
        assertFalse(of(0d, -.005d, 0f, false).reversed());
        assertTrue(of(0d, -.05d, 0f, false).reversed());
        assertFalse(of(0d, .05d, 0f, true).reversed());
    }

    @Test void reportsNoGaitWhenStandingStill() {
        var still = of(0d, 0d, 37f, true);
        assertEquals(0f, still.gaitWeight(), TOLERANCE);
        assertTrue(still.reversed());
    }

    @Test void survivesNonFiniteInput() {
        var broken = LocomotionDirection.of(Double.NaN, 0d, 0f, STRAFE_WEIGHT, DEADBAND, true);
        assertEquals(0f, broken.forward(), TOLERANCE);
        assertEquals(0f, broken.gaitWeight(), TOLERANCE);
        assertTrue(broken.reversed());
        assertEquals(0f, LocomotionDirection.of(0d, 0d, Float.NaN, STRAFE_WEIGHT, DEADBAND, false).gaitWeight(),
                TOLERANCE);
    }
}
