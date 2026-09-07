package com.murimrpg.client.render;

/**
 * Local render-only jump phase tracker. It observes already-authoritative client motion;
 * it never writes player position, velocity, collision or any network state.
 */
final class ArticulatedJumpTracker {
    enum Phase { GROUND, RISE, APEX, FALL, LAND }

    private boolean wasAirborne;
    private float airborneStartAge;
    private float landingAge = Float.NaN;
    private float landingProgress;

    /**
     * @param ageInTicks fractional render age, so the landing release advances smoothly
     *                   between ticks instead of stepping once per tick.
     */
    Phase update(float ageInTicks, boolean onGround, double verticalVelocity, double riseThreshold,
                 double apexThreshold, float landingDurationTicks, float minimumAirborneTicks) {
        if (!Float.isFinite(ageInTicks)) {
            reset();
            return Phase.GROUND;
        }
        if (!onGround) {
            if (!wasAirborne) {
                wasAirborne = true;
                airborneStartAge = ageInTicks;
            }
            landingAge = Float.NaN;
            landingProgress = 0f;
            if (verticalVelocity > riseThreshold) return Phase.RISE;
            if (verticalVelocity < -apexThreshold) return Phase.FALL;
            return Phase.APEX;
        }
        if (wasAirborne) {
            wasAirborne = false;
            // A brief slab/stair step-off is not a jump landing; avoid a walking twitch.
            float airborne = ageInTicks - airborneStartAge;
            landingAge = airborne >= minimumAirborneTicks ? ageInTicks : Float.NaN;
        }
        if (Float.isNaN(landingAge)) {
            landingProgress = 0f;
            return Phase.GROUND;
        }
        float elapsed = ageInTicks - landingAge;
        if (elapsed < 0f || elapsed >= landingDurationTicks) {
            landingAge = Float.NaN;
            landingProgress = 0f;
            return Phase.GROUND;
        }
        landingProgress = Math.clamp(elapsed / landingDurationTicks, 0f, 1f);
        return Phase.LAND;
    }

    /** 0 at ground contact, 1 when the landing window has fully released. */
    float landingProgress() { return landingProgress; }

    private void reset() {
        wasAirborne = false;
        landingAge = Float.NaN;
        landingProgress = 0f;
    }
}
