package com.murimrpg.client;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local presentation preferences. They never influence server game rules. */
public record ClientVisualSettings(Quality quality, float idleBreathRadians, float walkArmSwayRadians,
                                   float sprintArmSwayRadians, int footstepIntervalTicks, int maxParticles) {
    public enum Quality { LOW, NORMAL, HIGH }
    public static ClientVisualSettings load(Path directory) {
        Path path = directory.resolve("client-visuals.yml");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(path)) try (InputStream input = ClientVisualSettings.class.getClassLoader().getResourceAsStream("client-visuals.yml")) {
                if (input == null) throw new IOException("Bundled client-visuals.yml is missing");
                Files.copy(input, path);
            }
        } catch (IOException error) { throw new IllegalStateException("MurimRPG client visual settings could not be created", error); }
        YamlDocument yaml = YamlDocument.load(path);
        Quality quality;
        try { quality = Quality.valueOf(value(yaml.string("quality"), "quality").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("client-visuals.yml.quality must be LOW, NORMAL, or HIGH", error); }
        return new ClientVisualSettings(quality, decimal(yaml, "idle-breath-radians"), decimal(yaml, "walk-arm-sway-radians"),
                decimal(yaml, "sprint-arm-sway-radians"), positive(yaml, "footstep-interval-ticks"), positive(yaml, "max-particles"));
    }
    private static String value(String value, String key) { if (value == null || value.isBlank()) throw new IllegalArgumentException("client-visuals.yml." + key + " is required"); return value; }
    private static float decimal(YamlDocument yaml, String key) { Object raw = yaml.get(key); if (!(raw instanceof Number number) || number.floatValue() < 0) throw new IllegalArgumentException("client-visuals.yml." + key + " must be non-negative"); return number.floatValue(); }
    private static int positive(YamlDocument yaml, String key) { int value = yaml.integer(key, -1); if (value < 1) throw new IllegalArgumentException("client-visuals.yml." + key + " must be positive"); return value; }
}
