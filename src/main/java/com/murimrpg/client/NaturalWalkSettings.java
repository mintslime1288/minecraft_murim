package com.murimrpg.client;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Client presentation settings only; never changes movement speed or hitboxes. */
public record NaturalWalkSettings(boolean enabled, float walkSpeedMultiplier, float walkArmStrength,
        float walkLegStrength, float walkBodyStrength, float walkTorsoLeanRadians,
        float walkSideSwayRadians, float walkArmRollRadians, float walkSwingHarmonic,
        float walkStrideLengthBlocks, float walkEnterSpeed, float walkExitSpeed,
        float walkShoulderCounterYawRadians, float walkMinimumSpeedThreshold,
        float maximumShoulderYawRadians, float runSpeedMultiplier, float runArmStrength,
        float runLegStrength, float runBodyStrength, float blendInSeconds, float blendOutSeconds,
        float maximumLimbRadians, float maximumLegRadians, float maximumArmRadians, float maximumBodyRadians) {
    public static NaturalWalkSettings load(Path directory) {
        Path file = directory.resolve("natural-walk.yml");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) {
                try (var input = NaturalWalkSettings.class.getResourceAsStream("/natural-walk.yml")) {
                    if (input == null) throw new IOException("Missing natural-walk.yml");
                    Files.copy(input, file);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not load natural-walk.yml", error);
        }
        var yaml = YamlDocument.load(file);
        return new NaturalWalkSettings(bool(yaml, "enabled", true),
                value(yaml, "walk.speed-multiplier", .92f, .1f, 3f),
                value(yaml, "walk.arm-strength", .52f, 0f, 1.5f),
                value(yaml, "walk.leg-strength", .68f, 0f, 1.5f),
                value(yaml, "walk.body-strength", .16f, 0f, .75f),
                value(yaml, "walk.torso-lean-radians", .040f, -.15f, .15f),
                value(yaml, "walk.side-sway-radians", .012f, 0f, .08f),
                value(yaml, "walk.arm-roll-radians", .025f, 0f, .12f),
                value(yaml, "walk.swing-harmonic", .10f, 0f, .20f),
                value(yaml, "walk.stride-length-blocks", 3.2f, .5f, 8f),
                value(yaml, "walk.phase-enter-speed", .025f, .001f, .5f),
                value(yaml, "walk.phase-exit-speed", .010f, .0001f, .5f),
                value(yaml, "walk.shoulder-counter-yaw-radians", .016f, 0f, .05f),
                value(yaml, "walk.minimum-speed-threshold", .005f, 0f, .10f),
                value(yaml, "safety.maximum-shoulder-yaw-radians", .05f, .005f, .10f),
                value(yaml, "run.speed-multiplier", 1.12f, .1f, 3f),
                value(yaml, "run.arm-strength", .72f, 0f, 1.5f),
                value(yaml, "run.leg-strength", .88f, 0f, 1.5f),
                value(yaml, "run.body-strength", .22f, 0f, .75f),
                value(yaml, "transition.blend-in-seconds", .20f, .05f, 1f),
                value(yaml, "transition.blend-out-seconds", .18f, .05f, 1f),
                value(yaml, "safety.maximum-limb-radians", 1.20f, .2f, 1.5f),
                value(yaml, "safety.maximum-leg-radians", 1.20f, .2f, 1.5f),
                value(yaml, "safety.maximum-arm-radians", .85f, .2f, 1.5f),
                value(yaml, "safety.maximum-body-radians", .24f, .02f, .75f));
    }

    private static boolean bool(YamlDocument yaml, String key, boolean fallback) {
        Object raw = yaml.get(key);
        return raw instanceof Boolean value ? value : fallback;
    }

    private static float value(YamlDocument yaml, String key, float fallback, float min, float max) {
        Object raw = yaml.get(key);
        float result = raw instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < min || result > max) {
            throw new IllegalArgumentException("natural-walk.yml." + key + " must be within " + min + ".." + max);
        }
        return result;
    }
}
