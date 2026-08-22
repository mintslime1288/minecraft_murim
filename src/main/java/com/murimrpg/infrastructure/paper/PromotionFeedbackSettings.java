package com.murimrpg.infrastructure.paper;

public record PromotionFeedbackSettings(
        boolean titleEnabled,
        boolean soundEnabled,
        boolean particlesEnabled,
        boolean chatEnabled,
        int particleCount
) {
    public PromotionFeedbackSettings {
        if (particleCount < 0 || particleCount > 50) {
            throw new IllegalArgumentException("promotion-feedback.particle-count must be between 0 and 50");
        }
    }
}
