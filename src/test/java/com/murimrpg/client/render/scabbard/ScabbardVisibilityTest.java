package com.murimrpg.client.render.scabbard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.client.render.scabbard.ScabbardVisibility.Arm;
import com.murimrpg.client.render.scabbard.ScabbardVisibility.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every ruling the spec asks to be explicit about, pinned down here rather than in game. */
class ScabbardVisibilityTest {

    /** The normal case: feature on, model present, sword in a right-handed main hand, off hand empty. */
    private static ScabbardVisibility.Input holding() {
        return new ScabbardVisibility.Input(true, true, true, true, false, true);
    }

    @Test
    @DisplayName("sword in the right hand and an empty left hand puts the scabbard on the left")
    void swordInRightHandShowsScabbardOnLeft() {
        var decision = ScabbardVisibility.decide(holding());
        assertEquals(Arm.LEFT, decision.arm());
        assertEquals(Reason.SHOWN, decision.reason());
        assertTrue(decision.visible());
    }

    @Test
    @DisplayName("a left-handed player gets the scabbard on the right arm, not the left")
    void leftHandedPlayerIsMirrored() {
        var input = new ScabbardVisibility.Input(true, true, true, true, false, false);
        var decision = ScabbardVisibility.decide(input);
        assertEquals(Arm.RIGHT, decision.arm(),
                "with the left-hand option the sword is in the left hand, so the scabbard is on the right");
        assertEquals(Reason.SHOWN, decision.reason());
    }

    @Test
    @DisplayName("a real off-hand item wins and the scabbard is not drawn at all")
    void realOffHandItemSuppressesTheScabbard() {
        var input = new ScabbardVisibility.Input(true, true, true, false, false, true);
        var decision = ScabbardVisibility.decide(input);
        assertEquals(Arm.NONE, decision.arm(), "two models in one hand must never happen");
        assertEquals(Reason.OFF_HAND_HOLDS_A_REAL_ITEM, decision.reason());
        assertFalse(decision.visible());
    }

    @Test
    @DisplayName("putting the sword away or swapping items removes the scabbard")
    void anyOtherMainHandItemRemovesTheScabbard() {
        var input = new ScabbardVisibility.Input(true, true, false, true, false, true);
        var decision = ScabbardVisibility.decide(input);
        assertEquals(Arm.NONE, decision.arm());
        assertEquals(Reason.MAIN_HAND_IS_NOT_THE_SWORD, decision.reason());
    }

    @Test
    @DisplayName("without the model installed nothing is drawn, rather than a missing-model cube")
    void missingModelDrawsNothing() {
        var input = new ScabbardVisibility.Input(true, false, true, true, false, true);
        assertEquals(Reason.MODEL_NOT_INSTALLED, ScabbardVisibility.decide(input).reason());
        assertFalse(ScabbardVisibility.decide(input).visible());
    }

    @Test
    @DisplayName("spectators get no scabbard")
    void spectatorsGetNothing() {
        var input = new ScabbardVisibility.Input(true, true, true, true, true, true);
        assertEquals(Reason.SPECTATOR, ScabbardVisibility.decide(input).reason());
    }

    @Test
    @DisplayName("the config switch beats every other condition")
    void disabledBeatsEverything() {
        var input = new ScabbardVisibility.Input(false, true, true, true, false, true);
        var decision = ScabbardVisibility.decide(input);
        assertEquals(Arm.NONE, decision.arm());
        assertEquals(Reason.DISABLED_IN_CONFIG, decision.reason());
    }

    @Test
    @DisplayName("the config switch is checked before the model, so turning it off never logs a miss")
    void configIsCheckedFirst() {
        var input = new ScabbardVisibility.Input(false, false, false, false, true, true);
        assertEquals(Reason.DISABLED_IN_CONFIG, ScabbardVisibility.decide(input).reason());
    }

    @Test
    @DisplayName("the scabbard never lands on the same arm as the sword, over all 64 input combinations")
    void scabbardNeverSharesTheSwordArm() {
        int shown = 0;
        for (int bits = 0; bits < 64; bits++) {
            var input = new ScabbardVisibility.Input(
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0);
            var decision = ScabbardVisibility.decide(input);
            if (!decision.visible()) continue;
            shown++;
            Arm swordArm = input.mainArmIsRight() ? Arm.RIGHT : Arm.LEFT;
            assertFalse(decision.arm() == swordArm,
                    "the scabbard shared the sword's arm for input " + input);
            assertTrue(input.featureEnabled() && input.modelAvailable()
                            && input.mainHandIsConfiguredSword() && input.offHandEmpty()
                            && !input.spectator(),
                    "the scabbard was shown without every precondition met: " + input);
        }
        assertEquals(2, shown, "exactly the right-handed and left-handed holding cases may show it");
    }

    @Test
    @DisplayName("every input that fails a precondition reports which one, never SHOWN")
    void everyHiddenCaseHasARealReason() {
        for (int bits = 0; bits < 64; bits++) {
            var input = new ScabbardVisibility.Input(
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0);
            var decision = ScabbardVisibility.decide(input);
            assertEquals(decision.visible(), decision.reason() == Reason.SHOWN,
                    "reason and visibility disagreed for " + input);
        }
    }
}
