package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WalkBlendControllerTest {
    @Test void blendsInAndOutMonotonicallyWithoutOvershooting() {
        var controller = new WalkBlendController();
        float previous = 0f;
        for (int index = 0; index < 20; index++) {
            float current = controller.update(.02f, true, .20f, .18f);
            assertTrue(current >= previous && current <= 1f);
            previous = current;
        }
        previous = controller.intensity();
        for (int index = 0; index < 20; index++) {
            float current = controller.update(.02f, false, .20f, .18f);
            assertTrue(current <= previous && current >= 0f);
            previous = current;
        }
    }

    @Test void equivalentElapsedTimeProducesNearlyEquivalentBlend() {
        var coarse = new WalkBlendController();
        var fine = new WalkBlendController();
        coarse.update(.10f, true, .20f, .18f);
        for (int index = 0; index < 5; index++) fine.update(.02f, true, .20f, .18f);
        assertTrue(Math.abs(coarse.intensity() - fine.intensity()) < .0001f);
    }
}
