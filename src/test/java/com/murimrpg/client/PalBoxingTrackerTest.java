package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PalBoxingTrackerTest {
    @Test void handIsChosenOnceAndGuardPersistsAfterSwing() {
        var tracker = new PalBoxingTracker();
        assertEquals(PalBoxingTracker.Pose.PUNCH_LEFT,
                tracker.select(true, true, true, 0.1F, 100, 60, true, false));
        assertEquals(PalBoxingTracker.Pose.PUNCH_LEFT,
                tracker.select(true, true, true, 0.6F, 101, 60, true, true));
        assertEquals(PalBoxingTracker.Pose.GUARD,
                tracker.select(true, true, false, 0.0F, 130, 60, true, true));
        assertEquals(PalBoxingTracker.Pose.NONE,
                tracker.select(true, true, false, 0.0F, 161, 60, true, true));
    }

    @Test void incompatibleOrHeldItemCancelsGuard() {
        var tracker = new PalBoxingTracker();
        tracker.select(true, true, true, 0.1F, 10, 60, true, true);
        assertEquals(PalBoxingTracker.Pose.NONE,
                tracker.select(true, false, false, 0.0F, 11, 60, true, false));
        assertEquals(PalBoxingTracker.Pose.NONE,
                tracker.select(true, true, false, 0.0F, 12, 60, true, false));
    }

    @Test void phaseRestartCanChooseAnotherHandWithoutAFalseFrame() {
        var tracker = new PalBoxingTracker();
        tracker.select(true, true, true, 0.8F, 20, 60, true, true);
        assertEquals(PalBoxingTracker.Pose.PUNCH_LEFT,
                tracker.select(true, true, true, 0.1F, 21, 60, true, false));
    }
}
