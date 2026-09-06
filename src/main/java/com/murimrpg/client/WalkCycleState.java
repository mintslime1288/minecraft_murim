package com.murimrpg.client;

/** Per-player native walk state. It never changes entity position or velocity. */
final class WalkCycleState {
    private final WalkBlendController blend = new WalkBlendController();
    private final WalkSpeedGate speedGate = new WalkSpeedGate();
    private float phase;
    private float lastAge = Float.NaN;

    NaturalWalkMotion.Pose update(float ageInTicks, float distancePerTick, boolean walkEligible,
                                  NaturalWalkSettings settings) {
        float deltaTicks = Float.isFinite(lastAge) ? Math.clamp(ageInTicks - lastAge, 0f, 2f) : 0f;
        lastAge = ageInTicks;
        boolean walking = walkEligible && speedGate.update(distancePerTick,
                settings.walkEnterSpeed(), settings.walkExitSpeed());
        if (!walkEligible) speedGate.reset();
        float intensity = blend.update(deltaTicks / 20f, walking,
                settings.blendInSeconds(), settings.blendOutSeconds());
        if (walking) {
            phase = wrap(phase + (distancePerTick / settings.walkStrideLengthBlocks())
                    * (float) (Math.PI * 2.0) * deltaTicks);
        }
        return NaturalWalkMotion.sample(phase, intensity,
                .72f * settings.walkLegStrength(), .54f * settings.walkArmStrength(),
                settings.walkTorsoLeanRadians(), .030f * settings.walkBodyStrength(),
                settings.walkSideSwayRadians(), settings.walkArmRollRadians(),
                settings.walkSwingHarmonic(), settings.walkShoulderCounterYawRadians(),
                settings.maximumLegRadians(), settings.maximumBodyRadians(), settings.maximumShoulderYawRadians());
    }

    boolean inactive() { return blend.intensity() < .0001f; }

    private static float wrap(float value) {
        float period = (float) (Math.PI * 2.0);
        float result = value % period;
        return result < 0f ? result + period : result;
    }
}
