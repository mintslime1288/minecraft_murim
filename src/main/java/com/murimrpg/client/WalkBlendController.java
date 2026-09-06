package com.murimrpg.client;

/** Frame-rate-independent intensity smoothing for the native walk pose. */
final class WalkBlendController {
    private float intensity;

    float update(float deltaSeconds, boolean walking, float blendInSeconds, float blendOutSeconds) {
        float target = walking ? 1f : 0f;
        float tau = walking ? blendInSeconds : blendOutSeconds;
        float safeDelta = Math.clamp(deltaSeconds, 0f, .10f);
        float alpha = 1f - (float) Math.exp(-safeDelta / tau);
        intensity += (target - intensity) * alpha;
        intensity = Math.clamp(intensity, 0f, 1f);
        return intensity;
    }

    float intensity() { return intensity; }
}
