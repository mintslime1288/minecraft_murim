package com.murimrpg.infrastructure.paper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromotionFeedbackSettingsTest {
    @Test
    void acceptsLimitedParticleCounts() {
        assertDoesNotThrow(() -> new PromotionFeedbackSettings(true, true, true, true, 0));
        assertDoesNotThrow(() -> new PromotionFeedbackSettings(true, true, true, true, 50));
    }

    @Test
    void rejectsUnsafeParticleCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PromotionFeedbackSettings(true, true, true, true, -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PromotionFeedbackSettings(true, true, true, true, 51)
        );
    }
}
