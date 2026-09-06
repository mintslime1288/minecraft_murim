package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PalLocomotionTrackerTest {
    private static final PalAnimationSettings SETTINGS = new PalAnimationSettings(
            true, false, .008, .13, .035, .035, 3, 5, 3, 2, 3, 200, 400,
            true, "cpa");

    @Test void preservesArticulationFromTakeoffThroughLanding() {
        var tracker = new PalLocomotionTracker();
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,
                tracker.select(true, true, false, .08, 0, 100, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_TAKEOFF,
                tracker.select(true, false, false, .08, .42, 101, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_RISE,
                tracker.select(true, false, false, .08, .20, 104, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_APEX,
                tracker.select(true, false, false, .08, .01, 105, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_FALL,
                tracker.select(true, false, false, .08, -.20, 106, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_LAND,
                tracker.select(true, true, false, .08, 0, 107, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_LAND,
                tracker.select(true, true, false, .08, 0, 111, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,
                tracker.select(true, true, false, .08, 0, 112, SETTINGS));
    }

    @Test void incompatibleStateResetsEdgesWithoutFalseLanding() {
        var tracker = new PalLocomotionTracker();
        tracker.select(true, true, false, .08, 0, 10, SETTINGS);
        assertEquals(PalAnimationStateSelector.Locomotion.VANILLA,
                tracker.select(false, false, false, .08, .1, 11, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_RISE,
                tracker.select(true, false, false, .08, .1, 12, SETTINGS));
    }
}
