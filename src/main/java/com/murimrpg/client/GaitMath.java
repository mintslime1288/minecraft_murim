package com.murimrpg.client;

/** Pure math for the rotation-only biped gait. */
final class GaitMath {
    private GaitMath() { }

    /**
     * Uses only an odd harmonic. This guarantees
     * shapedSwing(phase + PI) == -shapedSwing(phase), so opposite limbs remain mirrored.
     */
    static float shapedSwing(float phase, float thirdHarmonic) {
        float harmonic = Math.clamp(thirdHarmonic, 0f, .20f);
        return (float) ((Math.sin(phase) + harmonic * Math.sin(3f * phase)) / (1f + harmonic));
    }

    static float clamp(float value, float maximum) {
        float safeMaximum = Math.max(0f, maximum);
        return Math.clamp(value, -safeMaximum, safeMaximum);
    }
}
