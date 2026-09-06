package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PalAnimationStateSelectorTest {
    @Test void selectsStableLocomotionStates() {
        assertEquals(PalAnimationStateSelector.Locomotion.VANILLA,
                state(false, true, false, false, false, .08, 0));
        assertEquals(PalAnimationStateSelector.Locomotion.IDLE,
                state(true, true, false, false, false, .001, 0));
        assertEquals(PalAnimationStateSelector.Locomotion.WALK,
                state(true, true, false, false, false, .08, 0));
        assertEquals(PalAnimationStateSelector.Locomotion.RUN,
                state(true, true, true, false, false, .11, 0));
        assertEquals(PalAnimationStateSelector.Locomotion.FAST_RUN,
                state(true, true, true, false, false, .16, 0));
        assertEquals(PalAnimationStateSelector.Locomotion.VANILLA,
                state(true, true, true, false, false, Double.NaN, 0));
        assertEquals(PalAnimationStateSelector.Locomotion.VANILLA,
                state(true, false, true, false, false, .1, Double.POSITIVE_INFINITY));
    }

    @Test void selectsEveryAirborneAndLandingPhase() {
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_TAKEOFF,
                state(true, false, false, true, false, .08, .42));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_RISE,
                state(true, false, false, false, false, .08, .20));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_APEX,
                state(true, false, false, false, false, .08, .01));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_FALL,
                state(true, false, false, false, false, .08, -.20));
        assertEquals(PalAnimationStateSelector.Locomotion.JUMP_LAND,
                state(true, true, false, false, true, .08, 0));
    }

    @Test void givesSwordAndPunchDistinctCombatStates() {
        assertEquals(PalAnimationStateSelector.Combat.NONE,
                PalAnimationStateSelector.combat(true, false, false));
        assertEquals(PalAnimationStateSelector.Combat.PUNCH,
                PalAnimationStateSelector.combat(true, true, false));
        assertEquals(PalAnimationStateSelector.Combat.SWORD_SWING,
                PalAnimationStateSelector.combat(true, true, true));
    }

    private static PalAnimationStateSelector.Locomotion state(
            boolean compatible, boolean onGround, boolean sprinting,
            boolean takeoff, boolean landing, double horizontal, double vertical) {
        return PalAnimationStateSelector.locomotion(compatible, onGround, sprinting, takeoff, landing,
                horizontal, vertical, .008, .13, .035, .035);
    }
}
