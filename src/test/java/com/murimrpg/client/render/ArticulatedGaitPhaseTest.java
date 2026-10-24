package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ArticulatedGaitPhaseTest {
    private static final float TOLERANCE = 1.0e-4f;
    private static final float SCALE = .6662f;
    private static final float TWO_PI = (float) (Math.PI * 2d);

    @Test void ignoresTheFirstSampleBecauseThereIsNoPreviousPosition() {
        var gait = new ArticulatedGaitPhase();
        assertEquals(0f, gait.advance(10f, SCALE, false), TOLERANCE);
    }

    @Test void advancesForwardAndStepsBackWhenReversed() {
        var gait = new ArticulatedGaitPhase();
        gait.advance(10f, SCALE, false);
        assertEquals(.6662f, gait.advance(11f, SCALE, false), TOLERANCE);
        assertEquals(1.3324f, gait.advance(12f, SCALE, false), TOLERANCE);
        assertEquals(.6662f, gait.advance(13f, SCALE, true), TOLERANCE);
        assertEquals(0f, gait.advance(14f, SCALE, true), TOLERANCE);
    }

    @Test void staysContinuousAcrossADirectionChange() {
        var gait = new ArticulatedGaitPhase();
        gait.advance(100f, SCALE, false);
        float before = gait.advance(100.5f, SCALE, false);
        float after = gait.advance(101f, SCALE, true);
        assertTrue(Math.abs(after - before) <= SCALE * .5f + TOLERANCE);
    }

    @Test void wrapsIntoASingleTurnSoLongSessionsKeepPrecision() {
        var gait = new ArticulatedGaitPhase();
        gait.advance(0f, SCALE, false);
        for (int step = 1; step <= 40; step++) {
            float phase = gait.advance(step, SCALE, false);
            assertTrue(phase >= 0f && phase < TWO_PI);
        }
    }

    @Test void ignoresAResetOrBackwardsWalkAnimationPosition() {
        var gait = new ArticulatedGaitPhase();
        gait.advance(50f, SCALE, false);
        float phase = gait.advance(51f, SCALE, false);
        assertEquals(phase, gait.advance(0f, SCALE, false), TOLERANCE);
        assertEquals(phase, gait.advance(400f, SCALE, false), TOLERANCE);
    }

    @Test void holdsThePhaseWhenGivenNonFiniteInput() {
        var gait = new ArticulatedGaitPhase();
        gait.advance(10f, SCALE, false);
        float phase = gait.advance(11f, SCALE, false);
        assertEquals(phase, gait.advance(Float.NaN, SCALE, false), TOLERANCE);
        assertEquals(phase, gait.advance(12f, Float.NaN, false), TOLERANCE);
    }

    @Test void resetForgetsThePreviousSample() {
        var gait = new ArticulatedGaitPhase();
        gait.advance(10f, SCALE, false);
        float phase = gait.advance(11f, SCALE, false);
        gait.reset();
        assertEquals(phase, gait.advance(900f, SCALE, false), TOLERANCE);
    }
}
