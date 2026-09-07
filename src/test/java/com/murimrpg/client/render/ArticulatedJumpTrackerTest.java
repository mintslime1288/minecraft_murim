package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ArticulatedJumpTrackerTest {
    @Test void selectsAirbornePhasesAndKeepsOnlyAShortLandingPose() {
        var tracker = new ArticulatedJumpTracker();
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(10f, true, 0, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.RISE, tracker.update(11f, false, .42, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.APEX, tracker.update(12f, false, .01, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.FALL, tracker.update(15f, false, -.12, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(16f, true, 0, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(20f, true, 0, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(21f, true, 0, .035, .035, 5f, 3f));
    }

    @Test void releasesTheLandingPoseProgressivelyRatherThanInOneFrame() {
        var tracker = new ArticulatedJumpTracker();
        tracker.update(0f, true, 0, .035, .035, 5f, 3f);
        tracker.update(1f, false, .42, .035, .035, 5f, 3f);
        tracker.update(9f, false, -.5, .035, .035, 5f, 3f);
        assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(10f, true, 0, .035, .035, 5f, 3f));
        assertEquals(0f, tracker.landingProgress());
        float previous = 0f;
        for (float age = 10.5f; age < 15f; age += .5f) {
            assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(age, true, 0, .035, .035, 5f, 3f));
            assertTrue(tracker.landingProgress() > previous);
            assertTrue(tracker.landingProgress() < 1f);
            previous = tracker.landingProgress();
        }
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(15f, true, 0, .035, .035, 5f, 3f));
    }

    @Test void ignoresASteppedOffLedgeThatIsTooBriefToBeAJump() {
        var tracker = new ArticulatedJumpTracker();
        tracker.update(0f, true, 0, .035, .035, 5f, 3f);
        tracker.update(1f, false, -.08, .035, .035, 5f, 3f);
        tracker.update(2f, false, -.16, .035, .035, 5f, 3f);
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(2.5f, true, 0, .035, .035, 5f, 3f));
    }

    @Test void dropsTheLandingWhenRenderAgeMovesBackwards() {
        var tracker = new ArticulatedJumpTracker();
        tracker.update(100f, true, 0, .035, .035, 5f, 3f);
        tracker.update(101f, false, .42, .035, .035, 5f, 3f);
        tracker.update(109f, false, -.5, .035, .035, 5f, 3f);
        assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(110f, true, 0, .035, .035, 5f, 3f));
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(4f, true, 0, .035, .035, 5f, 3f));
    }

    @Test void treatsANonFiniteRenderAgeAsGround() {
        var tracker = new ArticulatedJumpTracker();
        assertEquals(ArticulatedJumpTracker.Phase.GROUND,
                tracker.update(Float.NaN, false, .42, .035, .035, 5f, 3f));
        assertEquals(0f, tracker.landingProgress());
    }
}
