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
                                 float fastRunHorizontalSpeed, float limbSwingScale, boolean logOnce) {
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
        return new ArticulatedPlayerSettings(bool(yaml, "enabled", false), degree(yaml, "walk.stride-degrees", 22f),
                degree(yaml, "run.stride-degrees", 34f), degree(yaml, "walk.knee-degrees", 15f),
                degree(yaml, "run.knee-degrees", 29f), degree(yaml, "walk.elbow-degrees", 9f),
                degree(yaml, "run.elbow-degrees", 22f), degree(yaml, "walk.body-turn-degrees", 2.5f),
                gain(yaml, "retarget.upper-leg-gain", 1.45f), gain(yaml, "retarget.lower-leg-gain", 1.75f),
                gain(yaml, "retarget.upper-arm-gain", 1f), gain(yaml, "retarget.lower-arm-gain", 1f),
                speed(yaml, "fast-run.horizontal-speed-threshold", .13f),
                scale(yaml, "walk.limb-swing-scale", .6662f), bool(yaml, "diagnostics.log-once", false));
    }

    private static boolean bool(YamlDocument yaml, String path, boolean fallback) {
        Object value = yaml.get(path); return value instanceof Boolean bool ? bool : fallback;
    }
    private static float degree(YamlDocument yaml, String path, float fallback) {
        Object value = yaml.get(path); float result = value instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < 0f || result > 55f)
            throw new IllegalArgumentException("articulated-player.yml." + path + " must be within 0..55");
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
}
