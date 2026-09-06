package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PalAnimationResourceTest {
    private static final List<String> ANIMATIONS = List.of(
            "pal_idle", "pal_walk", "pal_run", "pal_fast_run",
            "pal_jump_takeoff", "pal_jump_rise", "pal_jump_apex", "pal_jump_fall", "pal_jump_land",
            "pal_punch", "pal_sword_swing");
    private static final Pattern ROOT_TRANSFORM = Pattern.compile("\\\"(?:x|y|z|scale)\\\"\\s*:");
    private static final Pattern ARM_BEND = Pattern.compile(
            "\\\"(?:rightArm|leftArm)\\\"\\s*:\\s*\\{[^}]*\\\"bend\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LEG_ROLL = Pattern.compile(
            "\\\"(rightLeg|leftLeg)\\\"\\s*:\\s*\\{\\\"roll\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    @Test void allPalResourcesExistWithoutRootTranslationOrScale() throws IOException {
        Path directory = Path.of("src/main/resources/assets/murimrpg/player_animations");
        for (String name : ANIMATIONS) {
            Path file = directory.resolve(name + ".json");
            assertTrue(Files.isRegularFile(file), () -> "Missing PAL animation: " + file);
            String json = Files.readString(file);
            assertTrue(json.contains("\"name\": \"" + name + "\""), () -> "Wrong animation name: " + file);
            assertFalse(ROOT_TRANSFORM.matcher(json).find(), () -> "Root translation/scale is forbidden: " + file);
            var armBends = ARM_BEND.matcher(json);
            while (armBends.find()) {
                double bend = Double.parseDouble(armBends.group(1));
                assertTrue(bend <= 0.0, () -> "Arm bend must hinge backward in PAL model space: " + file);
            }
        }
    }

    @Test void groundedStancesKeepLegsSlightlyAndSymmetricallyApart() throws IOException {
        Path directory = Path.of("src/main/resources/assets/murimrpg/player_animations");
        Map<String, Double> expected = Map.of(
                "pal_idle", .018, "pal_walk", .028, "pal_run", .040, "pal_fast_run", .050);
        for (var entry : expected.entrySet()) {
            String json = Files.readString(directory.resolve(entry.getKey() + ".json"));
            var matcher = LEG_ROLL.matcher(json);
            int found = 0;
            while (matcher.find()) {
                double roll = Double.parseDouble(matcher.group(2));
                double signedExpected = matcher.group(1).equals("rightLeg") ? entry.getValue() : -entry.getValue();
                assertTrue(Math.abs(roll - signedExpected) < 0.000001,
                        () -> "Leg stance is not symmetric in " + entry.getKey());
                found++;
            }
            assertTrue(found >= 2, () -> "No leg stance rolls found in " + entry.getKey());
        }
    }
}
