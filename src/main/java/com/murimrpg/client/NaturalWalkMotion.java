package com.murimrpg.client;

/** Pure rotation-only native gait math; it has no GLB, entity, or renderer dependency. */
public final class NaturalWalkMotion {
    private NaturalWalkMotion() { }

    public record Pose(float rightLeg, float leftLeg, float rightArm, float leftArm,
                       float torsoPitch, float shoulderYaw, float bodyRoll, float armRoll) {
        Pose scaled(float factor) {
            return new Pose(rightLeg * factor, leftLeg * factor, rightArm * factor, leftArm * factor,
                    torsoPitch * factor, shoulderYaw * factor, bodyRoll * factor, armRoll * factor);
        }
    }

    public static Pose sample(float phase, float intensity, float stride, float armSwing, float torsoLean,
                              float shoulder, float sideSway, float armRoll, float swingHarmonic,
                              float shoulderCounterYaw, float maximumLimbRadians, float maximumBodyRadians,
                              float maximumShoulderYawRadians) {
        float weight = Math.clamp(intensity, 0f, 1f);
        // The third harmonic makes the swing slow slightly at foot plant/reversal
        // while preserving exact left/right opposition: f(phase + PI) == -f(phase).
        float swing = GaitMath.shapedSwing(phase, swingHarmonic);
        float leg = GaitMath.clamp(swing * stride * weight, maximumLimbRadians);
        float arm = GaitMath.clamp(-swing * armSwing * weight, maximumLimbRadians);
        float lateral = (float) Math.sin(phase) * weight;
        return new Pose(leg, -leg, arm, -arm,
                GaitMath.clamp(torsoLean * weight, maximumBodyRadians),
                GaitMath.clamp(-lateral * shoulderCounterYaw, maximumShoulderYawRadians),
                GaitMath.clamp(lateral * sideSway, maximumBodyRadians),
                GaitMath.clamp(lateral * armRoll, maximumLimbRadians));
    }

}
