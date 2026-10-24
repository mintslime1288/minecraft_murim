package com.murimrpg.client.render;

/** Bounded shoulder-pitch lag. No extra oscillator, translations or per-frame allocations. */
final class RunArmInertia {
    static final float MAXIMUM_DEGREES = 6f;
    private final ProceduralTorsoFollow follow = new ProceduralTorsoFollow();
    private float right;
    private float left;

    boolean advance(float age, float ticks, float maximumRadians, float weight,
                    float rightTarget, float leftTarget) {
        if (!Float.isFinite(weight) || weight <= 0f || !Float.isFinite(maximumRadians)
                || maximumRadians <= 0f || maximumRadians > (float) Math.toRadians(MAXIMUM_DEGREES)
                || !follow.advance(age, ticks, rightTarget, leftTarget, 0f)) {
            reset();
            return false;
        }
        float blend = Math.clamp(weight, 0f, 1f);
        // Blend toward a lagged target, never beyond it. Bound the difference, not the pose:
        // both arms keep their authored range and the existing limb safety ceilings still apply.
        right = rightTarget + Math.clamp(follow.x() - rightTarget, -maximumRadians, maximumRadians) * blend;
        left = leftTarget + Math.clamp(follow.y() - leftTarget, -maximumRadians, maximumRadians) * blend;
        return true;
    }

    static float weight(float runWeight, float speed, float forwardWeight, boolean reversed) {
        if (reversed || !Float.isFinite(runWeight) || !Float.isFinite(speed)
                || !Float.isFinite(forwardWeight)) return 0f;
        return Math.clamp(runWeight, 0f, 1f) * Math.clamp(speed, 0f, 1f)
                * Math.clamp(forwardWeight, 0f, 1f);
    }

    float right() { return right; }
    float left() { return left; }
    void reset() { follow.reset(); right = 0f; left = 0f; }
}
