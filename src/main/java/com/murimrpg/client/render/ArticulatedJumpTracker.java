package com.murimrpg.client.render;

/**
 * Local render-only jump phase tracker. It observes already-authoritative client motion;
 * it never writes player position, velocity, collision or any network state.
 */
final class ArticulatedJumpTracker {
    enum Phase { GROUND, RISE, APEX, FALL, LAND }

    private boolean wasAirborne;
    private long landingTick = Long.MIN_VALUE;

    Phase update(long tick, boolean onGround, double verticalVelocity, double riseThreshold,
                 double apexThreshold, int landingDurationTicks) {
        if (!onGround) {
            wasAirborne = true;
            landingTick = Long.MIN_VALUE;
            if (verticalVelocity > riseThreshold) return Phase.RISE;
            if (verticalVelocity < -apexThreshold) return Phase.FALL;
            return Phase.APEX;
        }
        if (wasAirborne) {
            wasAirborne = false;
            landingTick = tick;
        }
        if (landingTick != Long.MIN_VALUE && tick - landingTick < landingDurationTicks) return Phase.LAND;
        return Phase.GROUND;
    }
}
