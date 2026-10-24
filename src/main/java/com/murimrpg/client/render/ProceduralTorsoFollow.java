package com.murimrpg.client.render;

/**
 * Gives the torso a little mass. Instead of landing on the rotation the authored clips
 * produced for this render frame, it eases toward it over a configurable number of ticks.
 *
 * <p>The maths is a first-order lag, {@code value += (target - value) * (1 - exp(-dt / tau))}.
 * No Minecraft type appears here, so its safety properties run under a plain JVM test.
 */
final class ProceduralTorsoFollow {
    /** Beyond this many ticks between frames the gap is a pause or a teleport, not motion. */
    private static final float MAXIMUM_STEP_TICKS = 8f;
    /** Matches the 0..8 range ArticulatedPlayerSettings validates for the YAML value. */
    static final float MAXIMUM_FOLLOW_TICKS = 8f;
    /** About 0.006 degrees. Below this the eased value snaps onto its target. */
    static final float SETTLE_RADIANS = 1.0e-4f;

    private float lastAge = Float.NaN;
    private boolean seeded;
    private float x;
    private float y;
    private float z;

    /**
     * @return true when {@link #x()}, {@link #y()} and {@link #z()} hold a usable rotation;
     *         false means the caller must leave the authored pose exactly as it is.
     */
    boolean advance(float ageInTicks, float followTicks, float targetX, float targetY, float targetZ) {
        if (!Float.isFinite(followTicks) || followTicks <= 0f || followTicks > MAXIMUM_FOLLOW_TICKS) {
            reset();
            return false;
        }
        if (!Float.isFinite(ageInTicks) || !Float.isFinite(targetX)
                || !Float.isFinite(targetY) || !Float.isFinite(targetZ)) {
            reset();
            return false;
        }
        float delta = Float.isNaN(lastAge) ? Float.NaN : ageInTicks - lastAge;
        lastAge = ageInTicks;
        if (!seeded || !Float.isFinite(delta) || delta < 0f || delta > MAXIMUM_STEP_TICKS) {
            seeded = true;
            x = targetX;
            y = targetY;
            z = targetZ;
            return true;
        }
        float amount = 1f - (float) Math.exp(-delta / followTicks);
        if (!Float.isFinite(amount)) {
            reset();
            return false;
        }
        amount = Math.clamp(amount, 0f, 1f);
        float easedX = settle(x, targetX, amount);
        float easedY = settle(y, targetY, amount);
        float easedZ = settle(z, targetZ, amount);
        if (!Float.isFinite(easedX) || !Float.isFinite(easedY) || !Float.isFinite(easedZ)) {
            reset();
            return false;
        }
        x = easedX;
        y = easedY;
        z = easedZ;
        return true;
    }

    private static float settle(float current, float target, float amount) {
        float moved = current + (target - current) * amount;
        return Math.abs(target - moved) <= SETTLE_RADIANS ? target : moved;
    }

    float x() { return x; }
    float y() { return y; }
    float z() { return z; }

    /** Drops the stored torso so the next frame seeds on the authored pose instead of drifting into it. */
    void reset() {
        lastAge = Float.NaN;
        seeded = false;
        x = 0f;
        y = 0f;
        z = 0f;
    }
}
