package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WalkSpeedGateTest {
    @Test void holdsWalkingStateInsideTheHysteresisBand() {
        var gate = new WalkSpeedGate();
        assertFalse(gate.update(.020f, .025f, .010f));
        assertTrue(gate.update(.030f, .025f, .010f));
        assertTrue(gate.update(.015f, .025f, .010f));
        assertFalse(gate.update(.009f, .025f, .010f));
    }

    @Test void rejectsAnInvalidThresholdOrder() {
        var gate = new WalkSpeedGate();
        assertThrows(IllegalArgumentException.class, () -> gate.update(.02f, .01f, .02f));
    }
}
