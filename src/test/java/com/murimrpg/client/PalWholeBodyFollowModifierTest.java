package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PalWholeBodyFollowModifierTest {
    @Test void carriesTorsoRotationIntoShouldersWithinHardLimits() {
        assertEquals(.07f, PalWholeBodyFollowModifier.correction(.20f, .35f, .14f), 1.0e-6f);
        assertEquals(.14f, PalWholeBodyFollowModifier.correction(2f, .35f, .14f), 1.0e-6f);
        assertEquals(-.14f, PalWholeBodyFollowModifier.correction(-2f, .35f, .14f), 1.0e-6f);
    }

    @Test void rejectsNonFiniteOrUnsafeInputs() {
        assertEquals(0f, PalWholeBodyFollowModifier.correction(Float.NaN, .35f, .14f));
        assertEquals(0f, PalWholeBodyFollowModifier.correction(.2f, 1.1f, .14f));
        assertEquals(0f, PalWholeBodyFollowModifier.correction(.2f, .35f, -1f));
    }
}
