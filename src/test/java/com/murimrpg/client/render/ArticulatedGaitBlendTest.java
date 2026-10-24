package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ArticulatedGaitBlendTest {
    private static final float TOLERANCE = 1.0e-4f;
    private static final float BLEND = 3f;

    @Test void startsWalkingAndReachesTheRunClipOnlyAfterTheBlendWindow() {
        var blend = new ArticulatedGaitBlend();
        assertEquals(0f, blend.advance(0f, false, BLEND), TOLERANCE);
        assertTrue(blend.advance(1f, true, BLEND) > 0f);
        assertTrue(blend.advance(2f, true, BLEND) < 1f);
        assertEquals(1f, blend.advance(3f, true, BLEND), TOLERANCE);
        assertEquals(1f, blend.advance(9f, true, BLEND), TOLERANCE);
    }

    @Test void easesBackToTheWalkClipWhenSprintStops() {
        var blend = new ArticulatedGaitBlend();
        blend.advance(0f, false, BLEND);
        blend.advance(3f, true, BLEND);
        float first = blend.advance(4f, false, BLEND);
        assertTrue(first < 1f && first > 0f);
        assertEquals(0f, blend.advance(7f, false, BLEND), TOLERANCE);
    }

    @Test void advancesMonotonicallyWhileTheTargetIsUnchanged() {
        var blend = new ArticulatedGaitBlend();
        blend.advance(100f, true, BLEND);
        float previous = -1f;
        for (float age = 100.5f; age <= 103f; age += .5f) {
            float weight = blend.advance(age, true, BLEND);
            assertTrue(weight >= previous);
            previous = weight;
        }
        assertEquals(1f, previous, TOLERANCE);
    }

    @Test void swapsImmediatelyWhenTheWindowIsZero() {
        var blend = new ArticulatedGaitBlend();
        blend.advance(0f, false, 0f);
        assertEquals(1f, blend.advance(1f, true, 0f), TOLERANCE);
        assertEquals(0f, blend.advance(2f, false, 0f), TOLERANCE);
    }

    @Test void ignoresAJumpInRenderAgeAndNonFiniteInput() {
        var blend = new ArticulatedGaitBlend();
        blend.advance(10f, false, BLEND);
        blend.advance(11f, true, BLEND);
        float weight = blend.advance(1000f, true, BLEND);
        assertTrue(weight < 1f);
        assertEquals(weight, blend.advance(Float.NaN, true, BLEND), TOLERANCE);
        assertEquals(weight, blend.advance(1001f, true, Float.NaN), TOLERANCE);
    }

    @Test void resetForgetsThePreviousAge() {
        var blend = new ArticulatedGaitBlend();
        blend.advance(10f, false, BLEND);
        float weight = blend.advance(11f, true, BLEND);
        blend.reset();
        assertEquals(weight, blend.advance(9000f, true, BLEND), TOLERANCE);
    }
}
