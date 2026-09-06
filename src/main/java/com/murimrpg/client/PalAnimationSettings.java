package com.murimrpg.client;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only PAL animation selection and safety settings. */
record PalAnimationSettings(boolean enabled, boolean localPlayerOnly, double movingThreshold,
                            double fastRunThreshold, double riseVelocityThreshold,
                            double apexVelocityThreshold, int takeoffDurationTicks,
                            int landingDurationTicks, int locomotionFadeTicks,
                            int airborneFadeTicks, int landingFadeTicks,
                            int locomotionPriority, int combatPriority,
                            boolean preferThirdPartyProvider, String thirdPartyProviderModId) {
    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-PAL");

    static PalAnimationSettings load(Path directory) {
        Path file = directory.resolve("pal-player-animation.yml");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) try (var input = PalAnimationSettings.class.getResourceAsStream("/pal-player-animation.yml")) {
                if (input == null) throw new IOException("Missing pal-player-animation.yml");
                Files.copy(input, file);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not load pal-player-animation.yml", error);
        }

        var yaml = YamlDocument.load(file);
        boolean enabled = bool(yaml, "enabled", true);
        boolean localOnly = bool(yaml, "local-player-only", false);
        double moving = finiteRange(yaml, "movement.moving-threshold", 0.008, 0.0001, 0.1);
        double fast = finiteRange(yaml, "movement.fast-run-threshold", 0.13, 0.02, 0.5);
        double rise = finiteRange(yaml, "airborne.rise-velocity-threshold", 0.035, 0.001, 1.0);
        double apex = finiteRange(yaml, "airborne.apex-velocity-threshold", 0.035, 0.001, 0.25);
        int takeoffTicks = integerRange(yaml, "airborne.takeoff-duration-ticks", 3, 1, 10);
        int landingTicks = integerRange(yaml, "airborne.landing-duration-ticks", 5, 1, 20);
        int locomotionFade = integerRange(yaml, "transitions.locomotion-fade-ticks", 3, 0, 10);
        int airborneFade = integerRange(yaml, "transitions.airborne-fade-ticks", 2, 0, 10);
        int landingFade = integerRange(yaml, "transitions.landing-fade-ticks", 3, 0, 10);
        int locomotion = integerRange(yaml, "layers.locomotion-priority", 200, 0, 10_000);
        int combat = integerRange(yaml, "layers.combat-priority", 400, locomotion + 1, 20_000);
        boolean preferThirdParty = bool(yaml, "third-party-provider.prefer-when-installed", true);
        String providerModId = modId(yaml, "third-party-provider.mod-id", "cpa");
        return new PalAnimationSettings(enabled, localOnly, moving, fast, rise, apex,
                takeoffTicks, landingTicks, locomotionFade, airborneFade, landingFade,
                locomotion, combat, preferThirdParty, providerModId);
    }

    private static boolean bool(YamlDocument yaml, String path, boolean fallback) {
        Object value = yaml.get(path);
        return value instanceof Boolean result ? result : fallback;
    }

    private static double finiteRange(YamlDocument yaml, String path, double fallback, double minimum, double maximum) {
        Object value = yaml.get(path);
        double result = value instanceof Number number ? number.doubleValue() : fallback;
        if (!Double.isFinite(result) || result < minimum || result > maximum) {
            LOGGER.warn("Invalid pal-player-animation.yml.{}={}; using safe default {} (allowed {}..{}).",
                    path, value, fallback, minimum, maximum);
            return fallback;
        }
        return result;
    }

    private static int integerRange(YamlDocument yaml, String path, int fallback, int minimum, int maximum) {
        Object value = yaml.get(path);
        int result = value instanceof Number number ? number.intValue() : fallback;
        if (result < minimum || result > maximum) {
            LOGGER.warn("Invalid pal-player-animation.yml.{}={}; using safe default {} (allowed {}..{}).",
                    path, value, fallback, minimum, maximum);
            return fallback;
        }
        return result;
    }

    private static String modId(YamlDocument yaml, String path, String fallback) {
        Object value = yaml.get(path);
        String result = value instanceof String string ? string.trim() : fallback;
        if (!result.matches("[a-z0-9_.-]+")) {
            LOGGER.warn("Invalid pal-player-animation.yml.{}={}; using safe default {}.", path, value, fallback);
            return fallback;
        }
        return result;
    }
}
