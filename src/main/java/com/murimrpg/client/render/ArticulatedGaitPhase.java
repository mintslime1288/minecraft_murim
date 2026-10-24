package com.murimrpg.client.render;

/** Turns Minecraft's monotonically growing limb-swing distance into a signed gait phase. */
final class ArticulatedGaitPhase {
    private static final float TWO_PI = (float) (Math.PI * 2d);
    private static final float MAXIMUM_STEP = 8f;
    private float lastWalkAnimationPos = Float.NaN;
    private float phase;

    float advance(float walkAnimationPos, float limbSwingScale, boolean reversed) {
        if (!Float.isFinite(walkAnimationPos) || !Float.isFinite(limbSwingScale)) return phase;
        float delta = Float.isNaN(lastWalkAnimationPos) ? 0f : walkAnimationPos - lastWalkAnimationPos;
        lastWalkAnimationPos = walkAnimationPos;
        if (!(delta >= 0f) || delta > MAXIMUM_STEP) delta = 0f;
        phase += delta * limbSwingScale * (reversed ? -1f : 1f);
        if (!Float.isFinite(phase)) phase = 0f;
        phase -= TWO_PI * (float) Math.floor(phase / TWO_PI);
        return phase;
    }

    void reset() { lastWalkAnimationPos = Float.NaN; }
}
