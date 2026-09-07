package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ArticulatedJumpTrackerTest {
    @Test void selectsAirbornePhasesAndKeepsOnlyAShortLandingPose() {
        var tracker = new ArticulatedJumpTracker();
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(10, true, 0, .035, .035, 3));
        assertEquals(ArticulatedJumpTracker.Phase.RISE, tracker.update(11, false, .42, .035, .035, 3));
        assertEquals(ArticulatedJumpTracker.Phase.APEX, tracker.update(12, false, .01, .035, .035, 3));
        assertEquals(ArticulatedJumpTracker.Phase.FALL, tracker.update(13, false, -.12, .035, .035, 3));
        assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(14, true, 0, .035, .035, 3));
        assertEquals(ArticulatedJumpTracker.Phase.LAND, tracker.update(16, true, 0, .035, .035, 3));
        assertEquals(ArticulatedJumpTracker.Phase.GROUND, tracker.update(17, true, 0, .035, .035, 3));
    }
}
