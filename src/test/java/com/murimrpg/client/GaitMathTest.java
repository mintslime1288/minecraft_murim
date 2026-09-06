package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GaitMathTest {
    @Test void thirdHarmonicPreservesExactOppositeLimbSymmetry() {
        for (float phase = -7f; phase < 7f; phase += .07f) {
            assertEquals(-GaitMath.shapedSwing(phase, .06f),
                    GaitMath.shapedSwing(phase + (float) Math.PI, .06f), .00001f);
        }
    }
}
