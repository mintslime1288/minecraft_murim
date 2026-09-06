package com.murimrpg.client;

/** Per-player visual blend state. It does not alter gameplay movement. */
final class LocomotionBlendState {
    float walkWeight;
    float runWeight;
    float lastAge = Float.NaN;
    MeshyWalkAnimation.Pose cached = MeshyWalkAnimation.Pose.ZERO;

    void update(float age, float walkTarget, float runTarget, NaturalWalkSettings settings) {
        float seconds = Float.isFinite(lastAge) ? Math.clamp((age - lastAge) / 20f, 0f, .10f) : 0f;
        lastAge = age;
        walkWeight = approach(walkWeight, walkTarget, seconds / (walkTarget > walkWeight
                ? settings.blendInSeconds() : settings.blendOutSeconds()));
        runWeight = approach(runWeight, runTarget, seconds / (runTarget > runWeight
                ? settings.blendInSeconds() : settings.blendOutSeconds()));
    }

    private static float approach(float current, float target, float amount) {
        return current + Math.clamp(target - current, -amount, amount);
    }
}
