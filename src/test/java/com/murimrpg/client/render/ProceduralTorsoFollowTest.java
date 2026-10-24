package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ProceduralTorsoFollowTest {
    private static final float TORSO = .0864f;

    @Test void aFollowTimeOfZeroLeavesTheAuthoredPoseAlone() {
        var follow = new ProceduralTorsoFollow();
        assertFalse(follow.advance(0f, 0f, TORSO, 0f, TORSO));
        assertFalse(follow.advance(1f, 0f, TORSO, 0f, TORSO));
    }
    @Test void anOutOfRangeFollowTimeIsRefusedRatherThanClamped() {
        var follow = new ProceduralTorsoFollow();
        assertFalse(follow.advance(0f, -1f, TORSO, 0f, 0f));
        assertFalse(follow.advance(1f, Float.NaN, TORSO, 0f, 0f));
        assertFalse(follow.advance(2f, Float.POSITIVE_INFINITY, TORSO, 0f, 0f));
        assertFalse(follow.advance(3f, ProceduralTorsoFollow.MAXIMUM_FOLLOW_TICKS + .001f, TORSO, 0f, 0f));
        assertTrue(follow.advance(4f, ProceduralTorsoFollow.MAXIMUM_FOLLOW_TICKS, TORSO, 0f, 0f));
    }
    @Test void nonFiniteInputFallsBackToTheAuthoredPose() {
        var follow = new ProceduralTorsoFollow();
        assertFalse(follow.advance(0f, 2f, Float.NaN, 0f, 0f));
        assertFalse(follow.advance(1f, 2f, Float.POSITIVE_INFINITY, 0f, 0f));
        assertFalse(follow.advance(2f, 2f, 0f, 0f, Float.NaN));
        assertFalse(follow.advance(Float.NaN, 2f, 0f, 0f, 0f));
    }
    @Test void theFirstFrameSeedsExactlyOnTheAuthoredPose() {
        var follow = new ProceduralTorsoFollow();
        assertTrue(follow.advance(10f, 2f, TORSO, 0f, -TORSO));
        assertEquals(TORSO, follow.x(), 0f); assertEquals(0f, follow.y(), 0f); assertEquals(-TORSO, follow.z(), 0f);
    }
    @Test void aStandstillSettlesOnExactlyZero() {
        var follow = new ProceduralTorsoFollow(); follow.advance(0f, 2f, TORSO, 0f, TORSO);
        float age = 0f;
        for (int frame = 0; frame < 400 && (follow.x() != 0f || follow.z() != 0f); frame++) {
            age += 1f; follow.advance(age, 2f, 0f, 0f, 0f);
        }
        assertEquals(0f, follow.x(), 0f); assertEquals(0f, follow.y(), 0f); assertEquals(0f, follow.z(), 0f);
    }
    @Test void theOutputNeverLeavesTheRangeTheClipsReached() {
        var follow = new ProceduralTorsoFollow(); float age = 0f; float worst = 0f;
        for (int frame = 0; frame < 2000; frame++) {
            age += .5f + (frame % 3) * .25f;
            float pitch = (float) (TORSO * Math.sin(frame * .37));
            float roll = (float) (TORSO * .3 * Math.cos(frame * .11));
            assertTrue(follow.advance(age, 2f, pitch, 0f, roll));
            assertTrue(Float.isFinite(follow.x()) && Float.isFinite(follow.y()) && Float.isFinite(follow.z()));
            assertEquals(0f, follow.y(), 0f);
            worst = Math.max(worst, Math.max(Math.abs(follow.x()), Math.abs(follow.z())));
        }
        assertTrue(worst <= TORSO + 1e-6f);
    }
    @Test void anImpossibleTimeStepReseedsInsteadOfSnappingThrough() {
        var follow = new ProceduralTorsoFollow();
        follow.advance(100f, 2f, 0f, 0f, 0f); follow.advance(101f, 2f, TORSO * .5f, 0f, 0f);
        follow.advance(50f, 2f, TORSO, 0f, 0f); assertEquals(TORSO, follow.x(), 0f);
        follow.advance(51f, 2f, TORSO * .2f, 0f, 0f); follow.advance(9999f, 2f, TORSO * .5f, 0f, 0f);
        assertEquals(TORSO * .5f, follow.x(), 0f);
    }
    @Test void everyFrameMovesNoFurtherThanTheDistanceToTheTarget() {
        var follow = new ProceduralTorsoFollow(); float age = 0f; follow.advance(age, 3f, 0f, 0f, 0f);
        for (int frame = 0; frame < 500; frame++) {
            float before = follow.x(); float target = (float) (TORSO * Math.sin(frame * .21)); age += 1f;
            follow.advance(age, 3f, target, 0f, 0f);
            assertTrue(Math.abs(follow.x() - before) <= Math.abs(target - before) + 1e-6f);
        }
    }
    @Test void aLongerFollowTimeMovesLessInTheSameFrame() {
        var quick = new ProceduralTorsoFollow(); var slow = new ProceduralTorsoFollow();
        quick.advance(0f, 1f, 0f, 0f, 0f); slow.advance(0f, 6f, 0f, 0f, 0f);
        quick.advance(1f, 1f, TORSO, 0f, 0f); slow.advance(1f, 6f, TORSO, 0f, 0f);
        assertTrue(slow.x() < quick.x()); assertTrue(quick.x() < TORSO);
    }
    @Test void resetDropsStaleTorsoStateForJumpAndLanding() {
        var follow = new ProceduralTorsoFollow(); follow.advance(0f, 2f, TORSO, 0f, TORSO);
        follow.advance(1f, 2f, TORSO, 0f, TORSO); follow.reset();
        assertTrue(follow.advance(2f, 2f, TORSO * .1f, 0f, -TORSO * .1f));
        assertEquals(TORSO * .1f, follow.x(), 0f); assertEquals(-TORSO * .1f, follow.z(), 0f);
    }
}
