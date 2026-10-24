package com.murimrpg.client.render;

/** Eases between the walk and run clips instead of swapping them in one frame. */
final class ArticulatedGaitBlend {
    private static final float MAXIMUM_STEP = 40f;
    private float lastAge = Float.NaN;
    private float weight;

    float advance(float ageInTicks, boolean running, float blendTicks) {
        float target = running ? 1f : 0f;
        if (!Float.isFinite(ageInTicks) || !Float.isFinite(blendTicks)) return smoothstep(weight);
        if (blendTicks <= 0f) {
            lastAge = ageInTicks;
            weight = target;
            return smoothstep(weight);
        }
        float delta = Float.isNaN(lastAge) ? 0f : ageInTicks - lastAge;
        lastAge = ageInTicks;
        if (!(delta >= 0f) || delta > MAXIMUM_STEP) delta = 0f;
        float step = delta / blendTicks;
        weight = weight < target ? Math.min(target, weight + step) : Math.max(target, weight - step);
        return smoothstep(weight);
    }

    void reset() { lastAge = Float.NaN; }

    private static float smoothstep(float amount) {
        float value = Math.clamp(amount, 0f, 1f);
        return value * value * (3f - 2f * value);
    }
}
