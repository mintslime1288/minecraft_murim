package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.murimrpg.infrastructure.config.YamlDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocomotionStyleTest {
    private static float rad(float degrees) { return LocomotionStyle.radians(degrees); }
    private static JsonObject runBones() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/murimrpg/player_motion/murim_run_v3_torso.json")))
                .getAsJsonObject().getAsJsonObject("bones");
    }
    private static float pitch(JsonObject bones, String name, int frame) {
        return bones.getAsJsonArray(name).get(frame).getAsJsonArray().get(0).getAsFloat();
    }

    @Test void rightForwardArmMovesRightShoulderForwardRatherThanSideways() {
        float yaw = LocomotionStyle.shoulderYaw(rad(-30), rad(30), .1f, 5);
        // Same ZYX convention verified in mapped 26.2 ModelPart.translateAndRotate.
        var turn = new Matrix3f().rotationZYX(0, yaw, rad(13));
        var right = turn.transform(new Vector3f(-5, -10, 0));
        var left = turn.transform(new Vector3f(5, -10, 0));
        assertTrue(right.z < left.z, "model forward is -Z; right shoulder must lead");
        assertEquals(right.y, left.y, 1e-6f, "yaw does not tilt one shoulder sideways/down");
        assertEquals(10f, right.distance(left), 1e-5f, "shoulders retain their separation");
        assertEquals(-yaw, LocomotionStyle.shoulderYaw(rad(30), rad(-30), .1f, 5), 1e-6f);
    }

    @Test void yawStaysBoundedContinuousAndFollowsActualArmThroughAFullCycle() {
        float previous = 0;
        for (int i = 0; i <= 1000; i++) {
            float right = rad(32) * (float) Math.sin(i * Math.PI * 2 / 1000);
            float yaw = LocomotionStyle.shoulderYaw(right, -right, .1f, 5);
            assertTrue(Math.abs(yaw) <= rad(5));
            assertEquals(Math.signum(right), Math.signum(yaw));
            assertTrue(Math.abs(yaw - previous) < rad(.05f));
            previous = yaw;
        }
        assertEquals(0, LocomotionStyle.shoulderYaw(.2f, .2f, .1f, 5));
        assertEquals(0, LocomotionStyle.shoulderYaw(.2f, -.2f, .1f, 0));
    }

    @Test void originalClipHasShallowRunningElbowsAndStyleMakesThemDistinct() throws Exception {
        var bones = runBones();
        for (int f = 0; f <= 16; f++) {
            float original = pitch(bones, "lower_arm.R", f);
            assertTrue(Math.abs(original) <= rad(12.01f));
            float styled = LocomotionStyle.running(original, 1, -40, 1, 1);
            assertTrue(styled <= -rad(42.99f) && styled >= -rad(52.01f));
            float knee = LocomotionStyle.running(pitch(bones, "shin.R", f), 1.5f, 0, 1, 1) * 1.35f;
            assertTrue(knee > 0 && knee < rad(55));
            float lean = pitch(bones, "spine", f) + pitch(bones, "chest", f);
            float styledLean = LocomotionStyle.running(lean, 1, 4, 1, 1);
            assertTrue(styledLean > rad(12) && styledLean < rad(15));
        }
    }

    @Test void runKeepsSourceHalfCycleSymmetryAndSmallAuthoredElbowAsymmetry() throws Exception {
        var bones = runBones();
        for (String stem : new String[]{"thigh", "shin", "upper_arm", "lower_arm"}) {
            float gain = switch (stem) { case "shin" -> 1.5f; case "thigh" -> 1.12f;
                case "upper_arm" -> 1.15f; default -> 1f; };
            float bias = stem.equals("lower_arm") ? -40 : 0;
            for (int f = 0; f < 16; f++) {
                float right = LocomotionStyle.running(pitch(bones, stem + ".R", f), gain, bias, 1, 1);
                float left = LocomotionStyle.running(pitch(bones, stem + ".L", (f + 8) % 16), gain, bias, 1, 1);
                if (stem.equals("lower_arm")) {
                    // Source elbows differ by up to 0.47 degrees; preserve rather than
                    // silently rewrite that authored asymmetry to satisfy an assumed symmetry.
                    float sourceDifference = pitch(bones, stem + ".R", f)
                            - pitch(bones, stem + ".L", (f + 8) % 16);
                    assertTrue(Math.abs(sourceDifference) < rad(.48f));
                    assertEquals(sourceDifference, right - left, 1e-6f);
                } else {
                    assertEquals(right, left, 1e-5f);
                }
            }
            assertEquals(LocomotionStyle.running(pitch(bones, stem + ".R", 0), gain, bias, 1, 1),
                    LocomotionStyle.running(pitch(bones, stem + ".R", 16), gain, bias, 1, 1), 1e-6f);
        }
    }

    @Test void zeroDirectionIsExactBypassAndGaitBlendIsContinuous() {
        assertEquals(.25f, LocomotionStyle.running(.25f, 1.5f, 40, 1, 0), 0f);
        float old = -.2f;
        float enhanced = LocomotionStyle.running(old, 1, -40, 1, 1);
        for (int i = 0; i <= 100; i++) {
            float w = i / 100f;
            float mixed = LocomotionStyle.running(old, 1, -40, 1, w);
            assertEquals(old + (enhanced - old) * w, mixed, 1e-6f);
            assertTrue(mixed <= old && mixed >= enhanced);
        }
        assertEquals(0, LocomotionStyle.running(0, 1.5f, -40, 0, 1));
    }

    @Test void idleFadesSmoothlyOutBeforeWalkingAndDoesNotUseStrafeWeight() {
        assertEquals(1, LocomotionStyle.idleWeight(0, .2f));
        assertEquals(0, LocomotionStyle.idleWeight(.2f, .2f));
        assertEquals(0, LocomotionStyle.idleWeight(1, .2f));
        float previous = 1;
        for (int i = 0; i <= 100; i++) {
            float weight = LocomotionStyle.idleWeight(i * .002f, .2f);
            assertTrue(weight <= previous + 1e-6f);
            assertTrue(Math.abs(weight - previous) < .02f);
            previous = weight;
        }
    }

    @Test void breathingHasTheConfiguredPeriodAndBoundedPitch() {
        for (int i = 0; i < 80; i++) {
            float value = LocomotionStyle.breath(i, 4, .45f);
            assertTrue(Math.abs(value) <= rad(.45f));
            assertEquals(value, LocomotionStyle.breath(i + 80, 4, .45f), 1e-6f);
        }
        assertEquals(rad(.45f), LocomotionStyle.breath(20, 4, .45f), 1e-6f);
    }

    @Test void fullStyledTorsoPreservesMouseHeadOrientation() {
        for (int i = 0; i < 80; i++) {
            float right = rad(31) * (float) Math.sin(i * Math.PI * 2 / 80);
            float yaw = LocomotionStyle.shoulderYaw(right, -right, .1f, 5);
            float[] head = new float[3];
            assertTrue(HeadLookCompensation.solve(rad(13.45f), yaw, rad(.54f), rad(35), rad(80), head));
            var actual = new Matrix3f().rotationZYX(rad(.54f), yaw, rad(13.45f))
                    .mul(new Matrix3f().rotationZYX(head[2], head[1], head[0]));
            var wanted = new Matrix3f().rotationZYX(0, rad(80), rad(35));
            assertTrue(actual.equals(wanted, 1e-5f));
        }
    }

    @Test void configurationIsOptInAndRejectsNonFiniteTuning(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.yml");
        Files.writeString(file, "motion-style: {}\n");
        var settings = LocomotionStyle.Settings.load(YamlDocument.load(file));
        assertFalse(settings.runEnabled()); assertFalse(settings.torsoEnabled()); assertFalse(settings.idleEnabled());
        Files.writeString(file, "motion-style:\n  torso:\n    roll-scale: .NaN\n");
        assertThrows(IllegalArgumentException.class, () -> LocomotionStyle.Settings.load(YamlDocument.load(file)));
        assertEquals(0, LocomotionStyle.shoulderYaw(Float.NaN, 0, .1f, 5));
        assertEquals(0, LocomotionStyle.breath(Float.NaN, 4, .45f));
        assertEquals(0, LocomotionStyle.idleWeight(Float.NaN, .2f));
    }
}
