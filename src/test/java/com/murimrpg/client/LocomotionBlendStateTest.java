package com.murimrpg.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocomotionBlendStateTest {
    private static final NaturalWalkSettings SETTINGS = new NaturalWalkSettings(true, .92f, .52f, .68f, .16f,
            .040f, .012f, .025f,
            .10f, 3.20f, .025f, .010f, .016f, .005f, .05f,
            1.12f, .72f, .88f, .22f, .20f, .18f, 1.20f, 1.20f, .85f, .24f);

    @Test void blendsWithoutSnappingAndPreservesBoundedWeights() {
        var state = new LocomotionBlendState();
        state.update(0f, 1f, 0f, SETTINGS);
        state.update(1f, 1f, 0f, SETTINGS);
        assertTrue(state.walkWeight > 0f && state.walkWeight < 1f);
        float walking = state.walkWeight;
        state.update(2f, 0f, 1f, SETTINGS);
        assertTrue(state.walkWeight < walking);
        assertTrue(state.runWeight > 0f && state.runWeight < 1f);
    }
}
