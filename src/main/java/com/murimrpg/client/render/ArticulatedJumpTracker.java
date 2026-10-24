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
    private Phase currentPhase = Phase.GROUND;
    private Phase priorPhase = Phase.GROUND;
    private float phaseChangeAge = Float.NaN;
    private float phaseProgress = 1f;
    private float airborneProgress = 1f;

    /**
     * @param ageInTicks fractional render age, so the landing release advances smoothly
     *                   between ticks instead of stepping once per tick.
     */
    Phase update(float ageInTicks, boolean onGround, double verticalVelocity, double riseThreshold,
                 double apexThreshold, float landingDurationTicks, float minimumAirborneTicks,
                 float phaseBlendTicks, float takeOffBlendTicks) {
        if (!Float.isFinite(ageInTicks)) {
            reset();
            return settle(Phase.GROUND, ageInTicks, phaseBlendTicks);
        }
        if (!onGround) {
            if (!wasAirborne) {
                wasAirborne = true;
                airborneStartAge = ageInTicks;
            }
            landingAge = Float.NaN;
            landingProgress = 0f;
            float airborneFor = ageInTicks - airborneStartAge;
            airborneProgress = takeOffBlendTicks <= 0f || airborneFor < 0f
                    ? 1f : Math.clamp(airborneFor / takeOffBlendTicks, 0f, 1f);
            if (verticalVelocity > riseThreshold) return settle(Phase.RISE, ageInTicks, phaseBlendTicks);
            if (verticalVelocity < -apexThreshold) return settle(Phase.FALL, ageInTicks, phaseBlendTicks);
            return settle(Phase.APEX, ageInTicks, phaseBlendTicks);
        }
        airborneProgress = 1f;
        if (wasAirborne) {
            wasAirborne = false;
            // A brief slab/stair step-off is not a jump landing; avoid a walking twitch.
            float airborne = ageInTicks - airborneStartAge;
            landingAge = airborne >= minimumAirborneTicks ? ageInTicks : Float.NaN;
        }
        if (Float.isNaN(landingAge)) {
            landingProgress = 0f;
            return settle(Phase.GROUND, ageInTicks, phaseBlendTicks);
        }
        float elapsed = ageInTicks - landingAge;
        if (elapsed < 0f || elapsed >= landingDurationTicks) {
            landingAge = Float.NaN;
            landingProgress = 0f;
            return settle(Phase.GROUND, ageInTicks, phaseBlendTicks);
        }
        landingProgress = Math.clamp(elapsed / landingDurationTicks, 0f, 1f);
        return settle(Phase.LAND, ageInTicks, phaseBlendTicks);
    }

    private Phase settle(Phase phase, float ageInTicks, float phaseBlendTicks) {
        if (phase != currentPhase) {
            priorPhase = currentPhase;
            currentPhase = phase;
            phaseChangeAge = ageInTicks;
        }
        if (Float.isNaN(phaseChangeAge) || phaseBlendTicks <= 0f) {
            phaseProgress = 1f;
            return phase;
        }
        float sincePhaseChange = ageInTicks - phaseChangeAge;
        phaseProgress = sincePhaseChange < 0f ? 1f : Math.clamp(sincePhaseChange / phaseBlendTicks, 0f, 1f);
        return phase;
    }

    Phase priorPhase() { return priorPhase; }
    float phaseProgress() { return phaseProgress; }
    float airborneProgress() { return airborneProgress; }

    /** 0 at ground contact, 1 when the landing window has fully released. */
    float landingProgress() { return landingProgress; }

    private void reset() {
        wasAirborne = false;
        landingAge = Float.NaN;
        landingProgress = 0f;
        priorPhase = Phase.GROUND;
        currentPhase = Phase.GROUND;
        phaseChangeAge = Float.NaN;
        phaseProgress = 1f;
        airborneProgress = 1f;
    }
}
