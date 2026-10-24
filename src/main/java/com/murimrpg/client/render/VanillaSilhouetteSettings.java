package com.murimrpg.client.render;

import com.murimrpg.infrastructure.config.YamlDocument;

/**
 * The {@code vanilla-silhouette} block. Every value is validated against a range that keeps the
 * result recognisable as the ordinary Java Edition player, and the whole block is off by default so
 * a client that has not been told otherwise renders exactly what it renders today.
 *
 * <p>Read once when the Minecraft client starts, like every other block in this file, so a change
 * needs a client restart.
 */
record VanillaSilhouetteSettings(boolean enabled, float fadeTicks,
                                 boolean gaitEnabled, float strideScale, float armStrideScale,
                                 float limbCeilingDegrees, float walkLeanDegrees, float runLeanDegrees,
                                 float shoulderYawDegrees, float shoulderYawGain, float cadenceScale,
                                 boolean idleEnabled, float idleFadeSpeed, float idleLeanDegrees,
                                 float idleArmSpreadDegrees, float idleArmPitchDegrees,
                                 float breathDegrees, float breathSeconds,
                                 boolean jumpEnabled, float jumpHoldFraction,
                                 float jumpLeanPerBlock, float jumpLeanDegrees,
                                 float jumpRollPerBlock, float jumpRollDegrees) {

    static VanillaSilhouetteSettings load(YamlDocument yaml) {
        return new VanillaSilhouetteSettings(
                flag(yaml, "enabled"),
                value(yaml, "fade-ticks", 4f, 0f, 20f),
                flag(yaml, "gait.enabled"),
                value(yaml, "gait.stride-scale", 1f, .5f, 1.6f),
                value(yaml, "gait.arm-stride-scale", 1f, .5f, 1.6f),
                value(yaml, "gait.limb-ceiling-degrees", 45f, 0f, VanillaSilhouettePose.MAXIMUM_LIMB_DEGREES),
                value(yaml, "gait.walk-lean-degrees", 3f, 0f, VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES),
                value(yaml, "gait.run-lean-degrees", 10f, 0f, VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES),
                value(yaml, "gait.shoulder-yaw-degrees", 3f, 0f, VanillaSilhouettePose.MAXIMUM_YAW_DEGREES),
                value(yaml, "gait.shoulder-yaw-gain", .25f, 0f, 1f),
                value(yaml, "gait.cadence-scale", 1f, .5f, 1.5f),
                flag(yaml, "idle.enabled"),
                value(yaml, "idle.fade-speed", .12f, .02f, .5f),
                value(yaml, "idle.lean-degrees", 1.5f, 0f, 6f),
                value(yaml, "idle.arm-spread-degrees", 2f, 0f, 6f),
                value(yaml, "idle.arm-pitch-degrees", 3f, 0f, 10f),
                value(yaml, "idle.breath-degrees", .35f, 0f, 1.5f),
                value(yaml, "idle.breath-seconds", 4f, 2f, 8f),
                flag(yaml, "jump.enabled"),
                value(yaml, "jump.hold-fraction", .55f, 0f, 1f),
                value(yaml, "jump.lean-degrees-per-block", 22f, 0f, 60f),
                value(yaml, "jump.lean-degrees", 8f, 0f, VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES),
                value(yaml, "jump.roll-degrees-per-block", 14f, 0f, 60f),
                value(yaml, "jump.roll-degrees", 3f, 0f, VanillaSilhouettePose.MAXIMUM_ROLL_DEGREES));
    }

    private static boolean flag(YamlDocument yaml, String key) {
        return Boolean.TRUE.equals(yaml.get("vanilla-silhouette." + key));
    }

    private static float value(YamlDocument yaml, String key, float fallback, float minimum, float maximum) {
        Object raw = yaml.get("vanilla-silhouette." + key);
        float result = raw instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < minimum || result > maximum) {
            throw new IllegalArgumentException("articulated-player.yml.vanilla-silhouette." + key
                    + " must be within " + minimum + ".." + maximum);
        }
        return result;
    }
}
