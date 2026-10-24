package com.murimrpg.client.render.scabbard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the shipped assets rather than the code.
 *
 * <p>The sword and scabbard item models live in a resource pack while they are being reviewed,
 * so the two model tests skip when they are not on the classpath. The attack clip ships inside
 * the mod and is always checked.
 */
class SwordScabbardAssetTest {

    private static final String SWORD_MODEL = "/assets/murimrpg/models/item/top_straight_sword.json";
    private static final String SCABBARD_MODEL = "/assets/murimrpg/models/item/extended_black_scabbard.json";
    private static final String RIGHT_ARM_CLIP =
            "/assets/murimrpg/player_animations/pal_sword_swing_right_arm.json";
    private static final String ORIGINAL_CLIP =
            "/assets/murimrpg/player_animations/pal_sword_swing.json";

    /** Every display context the renderer relies on, which is every context the game asks for. */
    private static final List<String> REQUIRED_CONTEXTS = List.of(
            "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand",
            "gui", "ground", "fixed");

    private static JsonObject read(String resource) throws IOException {
        InputStream stream = SwordScabbardAssetTest.class.getResourceAsStream(resource);
        Assumptions.assumeTrue(stream != null, resource + " is not on this build's classpath");
        try (stream) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static JsonObject readRequired(String resource) throws IOException {
        InputStream stream = SwordScabbardAssetTest.class.getResourceAsStream(resource);
        assertTrue(stream != null, resource + " must ship inside the mod");
        try (stream) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    @Test
    @DisplayName("the attack clip the scabbard needs keys only the right arm")
    void rightArmClipKeysOnlyTheRightArm() throws IOException {
        JsonObject clip = readRequired(RIGHT_ARM_CLIP);
        assertEquals("pal_sword_swing_right_arm", clip.get("name").getAsString());
        JsonObject emote = clip.getAsJsonObject("emote");
        Set<String> bones = new TreeSet<>();
        int moves = 0;
        for (var element : emote.getAsJsonArray("moves")) {
            JsonObject move = element.getAsJsonObject();
            moves++;
            for (String key : move.keySet()) {
                if (!key.equals("tick") && !key.equals("easing")) bones.add(key);
            }
        }
        assertEquals(Set.of("rightArm"), bones,
                "an attack must not key the arm that carries the scabbard, nor the torso");
        assertEquals(5, moves, "the clip keeps the five keyframes the user previewed");
        assertFalse(emote.get("isLoop").getAsBoolean(), "an attack plays once");
    }

    @Test
    @DisplayName("the original clip still keys both arms, so turning the switch off restores it")
    void originalClipIsUntouched() throws IOException {
        JsonObject clip = readRequired(ORIGINAL_CLIP);
        Set<String> bones = new TreeSet<>();
        for (var element : clip.getAsJsonObject("emote").getAsJsonArray("moves")) {
            for (String key : element.getAsJsonObject().keySet()) {
                if (!key.equals("tick") && !key.equals("easing")) bones.add(key);
            }
        }
        assertEquals(Set.of("leftArm", "rightArm", "torso"), bones,
                "the pre-existing clip must not be edited; the new one is a separate file");
    }

    @Test
    @DisplayName("both item models carry every display context, including third-person left hand")
    void modelsDefineEveryDisplayContext() throws IOException {
        for (String resource : List.of(SWORD_MODEL, SCABBARD_MODEL)) {
            JsonObject model = read(resource);
            assertTrue(model.has("display"), resource + " has no display block");
            JsonObject display = model.getAsJsonObject("display");
            for (String context : REQUIRED_CONTEXTS) {
                assertTrue(display.has(context),
                        resource + " is missing the " + context + " display entry; the renderer "
                                + "relies on the game applying it, so an absent entry means an "
                                + "untransformed model in that view");
            }
        }
    }

    @Test
    @DisplayName("the scabbard's left-hand transforms are the saved values, not defaults")
    void scabbardLeftHandTransformsAreAuthored() throws IOException {
        JsonObject display = read(SCABBARD_MODEL).getAsJsonObject("display");

        JsonObject third = display.getAsJsonObject("thirdperson_lefthand");
        assertEquals(List.of(0.0, 90.0, 3.0), numbers(third, "rotation"));
        assertEquals(List.of(0.0, -18.25, 0.75), numbers(third, "translation"));
        assertFalse(third.has("scale"), "the save left third-person scale at 1, which must stay 1");

        JsonObject first = display.getAsJsonObject("firstperson_lefthand");
        assertEquals(List.of(0.0, 90.0, -12.0), numbers(first, "rotation"));
        assertEquals(List.of(1.0, 5.0, 1.0), numbers(first, "translation"));
        assertEquals(List.of(0.65, 0.65, 0.65), numbers(first, "scale"));
    }

    @Test
    @DisplayName("the sword's own transforms are untouched by this change")
    void swordTransformsAreUnchanged() throws IOException {
        JsonObject display = read(SWORD_MODEL).getAsJsonObject("display");
        JsonObject third = display.getAsJsonObject("thirdperson_righthand");
        assertEquals(List.of(0.0, -90.0, 0.0), numbers(third, "rotation"));
        assertEquals(List.of(0.0, 0.25, 1.5), numbers(third, "translation"));
        JsonObject first = display.getAsJsonObject("firstperson_righthand");
        assertEquals(List.of(0.65, 0.65, 0.65), numbers(first, "scale"));
    }

    @Test
    @DisplayName("the two models are separate meshes with their own textures")
    void modelsAreSeparateWithTheirOwnTextures() throws IOException {
        JsonObject sword = read(SWORD_MODEL);
        JsonObject scabbard = read(SCABBARD_MODEL);
        assertEquals("murimrpg:item/top_straight_sword",
                sword.getAsJsonObject("textures").get("0").getAsString());
        assertEquals("murimrpg:item/extended_black_scabbard",
                scabbard.getAsJsonObject("textures").get("0").getAsString());
        assertEquals(1668, sword.getAsJsonArray("elements").size(),
                "the sword keeps the cube count the user saved");
        assertEquals(1134, scabbard.getAsJsonArray("elements").size(),
                "the scabbard keeps the cube count the user saved");
    }

    private static List<Double> numbers(JsonObject object, String key) {
        var array = object.getAsJsonArray(key);
        return List.of(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }
}
