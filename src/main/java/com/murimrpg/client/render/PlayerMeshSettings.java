package com.murimrpg.client.render;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Visual-only dimensions for the external player mesh. */
record PlayerMeshSettings(boolean enabled, float widthScale, float heightScale, float depthScale,
                          boolean flipVertical, float yawDegrees, float groundOffset, float locomotionBlendSeconds) {
    static PlayerMeshSettings load(Path directory) {
        Path file = directory.resolve("player-model.yml");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) {
                try (var input = PlayerMeshSettings.class.getResourceAsStream("/player-model.yml")) {
                    if (input == null) throw new IOException("Missing player-model.yml");
                    Files.copy(input, file);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not load player-model.yml", error);
        }
        var yaml = YamlDocument.load(file);
        return new PlayerMeshSettings(bool(yaml, "enabled", true), value(yaml, "scale.width", 1f),
                value(yaml, "scale.height", 1f), value(yaml, "scale.depth", 1f),
                bool(yaml, "coordinate-correction.flip-vertical", true),
                value(yaml, "coordinate-correction.yaw-degrees", 180f),
                value(yaml, "coordinate-correction.ground-offset", 1.501f),
                value(yaml, "animation.locomotion-blend-seconds", .20f));
    }

    private static boolean bool(YamlDocument yaml, String key, boolean fallback) {
        Object raw = yaml.get(key); return raw instanceof Boolean value ? value : fallback;
    }
    private static float value(YamlDocument yaml, String key, float fallback) {
        Object raw = yaml.get(key); float value = raw instanceof Number number ? number.floatValue() : fallback;
        float minimum = key.endsWith("yaw-degrees") ? -180f : key.endsWith("blend-seconds") ? .05f : .25f;
        float maximum = key.endsWith("yaw-degrees") ? 180f : key.endsWith("blend-seconds") ? 1f : 2f;
        if (!Float.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("player-model.yml." + key + " must be within " + minimum + ".." + maximum);
        return value;
    }
}
