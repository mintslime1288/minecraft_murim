package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PalLocomotionTrackerTest {
    @Test void smallDownwardStepDoesNotTriggerTakeoffOrLanding() {
        var tracker=new PalLocomotionTracker(4);
        tracker.select(true,true,false,.08,0,1,SETTINGS);
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,tracker.select(true,false,false,.08,-.08,2,SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,tracker.select(true,true,false,.08,0,3,SETTINGS));
    }
    @Test void actualJumpRemainsImmediateAndLongFallStillLands() {
        var tracker=new PalLocomotionTracker(4);
        tracker.select(true,true,true,.14,0,1,SETTINGS);
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_TAKEOFF,tracker.select(true,false,true,.14,.42,2,SETTINGS));
        tracker.select(true,false,true,.14,-.3,8,SETTINGS);
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_LAND,tracker.select(true,true,true,.14,0,9,SETTINGS));
    }
    private static final PalAnimationSettings SETTINGS = new PalAnimationSettings(
            true, false, .008, .004, .13, .11, .035, .035, 3, 5, 3, 2, 3, 200, 400,
            true, 60, true, .35, .55, .30, true, "cpa");

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

    @Test void movementNoiseDoesNotRestartWalkAtTheEntryBoundary() {
        var tracker = new PalLocomotionTracker();
        assertEquals(PalAnimationStateSelector.Locomotion.IDLE,
                tracker.select(true, true, false, .007, 0, 1, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,
                tracker.select(true, true, false, .009, 0, 2, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,
                tracker.select(true, true, false, .006, 0, 3, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.IDLE,
                tracker.select(true, true, false, .003, 0, 4, SETTINGS));
    }

    @Test void sprintNoiseDoesNotAlternateRunAndFastRun() {
        var tracker = new PalLocomotionTracker();
        assertEquals(PalAnimationStateSelector.Locomotion.RUN,
                tracker.select(true, true, true, .12, 0, 1, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.FAST_RUN,
                tracker.select(true, true, true, .14, 0, 2, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.FAST_RUN,
                tracker.select(true, true, true, .12, 0, 3, SETTINGS));
        assertEquals(PalAnimationStateSelector.Locomotion.RUN,
                tracker.select(true, true, true, .10, 0, 4, SETTINGS));
    }
}
