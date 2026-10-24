package com.murimrpg.client.render;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings for the lightweight, skin-textured articulated player renderer. */
record ArticulatedPlayerSettings(boolean enabled, float walkStrideDegrees, float runStrideDegrees,
                                 float walkKneeDegrees, float runKneeDegrees, float walkElbowDegrees,
                                 float runElbowDegrees, float bodyTurnDegrees, float upperLegGain,
                                 float lowerLegGain, float upperArmGain, float lowerArmGain,
                                 float fastRunHorizontalSpeed, float limbSwingScale, float jumpStrideDegrees,
                                 float jumpKneeDegrees, float jumpElbowDegrees, float jumpBodyDegrees,
                                 float jumpRiseVelocityThreshold, float jumpApexVelocityThreshold,
                                 int jumpLandingDurationTicks, int jumpMinimumAirborneTicks,
                                 int jumpPhaseBlendTicks, int jumpTakeOffBlendTicks,
                                 float strafeGaitWeight, float reverseDeadband, float runBodyDegrees,
                                 float runLimbSwingScale, int gaitBlendTicks, float torsoFollowTicks,
                                 boolean torsoHipPivot, boolean torsoHeadFollow,
                                 float runContactRollDegrees, float runArmFollowTicks,
                                 float runArmLagDegrees, LocomotionStyle.Settings motionStyle,
                                 VanillaSilhouetteSettings vanillaSilhouette,
                                 ArticulatedCombatSettings combatMotion,
                                 String combatMotionFailure, boolean logOnce) {
    static ArticulatedPlayerSettings load(Path directory) {
        Path file = directory.resolve("articulated-player.yml");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) try (var input = ArticulatedPlayerSettings.class.getResourceAsStream("/articulated-player.yml")) {
                if (input == null) throw new IOException("Missing articulated-player.yml");
                Files.copy(input, file);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not load articulated-player.yml", error);
        }
        var yaml = YamlDocument.load(file);
        // A mistyped combat angle disables the combat layer for this run and is reported once by
        // the renderer. It must not stop the client from starting, unlike the locomotion values,
        // which have been in use long enough that a bad one there is a genuine mistake.
        String[] combatFailure = new String[1];
        var combat = ArticulatedCombatSettings.loadOrDisabled(yaml, combatFailure);
        return new ArticulatedPlayerSettings(bool(yaml, "enabled", false), degree(yaml, "walk.stride-degrees", 22f),
                degree(yaml, "run.stride-degrees", 34f), degree(yaml, "walk.knee-degrees", 15f),
                degree(yaml, "run.knee-degrees", 29f), degree(yaml, "walk.elbow-degrees", 9f),
                degree(yaml, "run.elbow-degrees", 22f, 90f), degree(yaml, "walk.body-turn-degrees", 2.5f),
                gain(yaml, "retarget.upper-leg-gain", 1.45f), gain(yaml, "retarget.lower-leg-gain", 1.75f),
                gain(yaml, "retarget.upper-arm-gain", 1f), gain(yaml, "retarget.lower-arm-gain", 1f),
                speed(yaml, "fast-run.horizontal-speed-threshold", .13f),
                scale(yaml, "walk.limb-swing-scale", .6662f), degree(yaml, "jump.stride-degrees", 28f),
                degree(yaml, "jump.knee-degrees", 34f), degree(yaml, "jump.elbow-degrees", 18f),
                degree(yaml, "jump.body-degrees", 12f), speed(yaml, "jump.rise-velocity-threshold", .035f),
                speed(yaml, "jump.apex-velocity-threshold", .035f), ticks(yaml, "jump.landing-duration-ticks", 5),
                ticks(yaml, "jump.minimum-airborne-ticks", 3), ticks(yaml, "jump.phase-blend-ticks", 2),
                ticks(yaml, "jump.take-off-blend-ticks", 6),
                fraction(yaml, "locomotion.strafe-gait-weight", .35f),
                threshold(yaml, "locomotion.reverse-deadband", .012f),
                degree(yaml, "run.body-degrees", 9.5f), scale(yaml, "run.limb-swing-scale", .6662f),
                ticks(yaml, "locomotion.gait-blend-ticks", 3),
                easing(yaml, "locomotion.torso-follow-ticks", 0f),
                bool(yaml, "locomotion.torso-hip-pivot", false),
                bool(yaml, "locomotion.torso-head-follow", false),
                accent(yaml, "run.contact-roll-degrees", 0f),
                easing(yaml, "run.arm-follow-ticks", 0f),
                armLag(yaml, "run.arm-lag-degrees", 3f),
                LocomotionStyle.Settings.load(yaml),
                VanillaSilhouetteSettings.load(yaml),
                combat, combatFailure[0],
                bool(yaml, "diagnostics.log-once", false));
    }

    private static boolean bool(YamlDocument yaml, String path, boolean fallback) {
        Object value = yaml.get(path); return value instanceof Boolean bool ? bool : fallback;
    }
    private static float degree(YamlDocument yaml, String path, float fallback) {
        return degree(yaml, path, fallback, 55f);
    }
    private static float degree(YamlDocument yaml, String path, float fallback, float maximum) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > maximum)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0.." + maximum);
        return result;
    }
    private static float speed(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < .02f || result > .5f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within .02..0.5");
        return result;
    }
    private static float scale(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < .05f || result > 4f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0.05..4.0");
        return result;
    }
    private static float gain(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < .1f || result > 3f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0.1..3.0");
        return result;
    }
    private static float fraction(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > 1f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0..1");
        return result;
    }
    private static float threshold(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > .25f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0..0.25");
        return result;
    }
    /** Ticks of easing. Zero means the feature is off, so unlike ticks() this starts at 0. */
    private static float easing(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > 8f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0..8");
        return result;
    }
    /**
     * A contact accent in degrees. Zero is off, so unlike degree() this starts at 0, and the top of
     * the range is RunContactAccent.MAXIMUM_DEGREES rather than degree()'s 55: past that it stops
     * being an accent on the authored roll and becomes a pose of its own.
     */
    private static float accent(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > RunContactAccent.MAXIMUM_DEGREES)
            throw new IllegalArgumentException("articulated-player.yml." + path
                    + " must be within 0.." + RunContactAccent.MAXIMUM_DEGREES);
        return result;
    }
    private static float armLag(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > RunArmInertia.MAXIMUM_DEGREES)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0..6");
        return result;
    }
    private static int ticks(YamlDocument yaml, String path, int fallback) {
        Object value = yaml.get(path); int result = value instanceof Number number ? number.intValue() : fallback;
        if (result < 1 || result > 8) throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 1..8");
        return result;
    }
}
