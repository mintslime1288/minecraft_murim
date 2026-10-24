package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Regression guards for the Blender-owned, rotation-only locomotion clips. */
class AuthoredLocomotionClipTest {
    private static final Path DIRECTORY = Path.of("src/main/resources/assets/murimrpg/player_motion");

    private record Clip(String name, Map<String, List<double[]>> tracks) {
        int loopFrames() { return tracks.values().iterator().next().size() - 1; }
        double x(String bone, int frame) { return Math.toDegrees(tracks.get(bone).get(Math.floorMod(frame, loopFrames()))[0]); }
    }

    private static Clip load(String file) throws IOException {
        Path path = DIRECTORY.resolve(file);
        assertTrue(Files.isRegularFile(path), () -> "Missing authored clip: " + path);
        String json = Files.readString(path);
        assertTrue(json.contains("\"rotation_only\": true"));
        assertFalse(json.contains("\"location\"") || json.contains("\"scale\""));
        Map<String, List<double[]>> tracks = new LinkedHashMap<>();
        var bones = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("bones");
        for (var track : bones.entrySet()) {
            List<double[]> frames = new ArrayList<>();
            for (var frame : track.getValue().getAsJsonArray()) {
                var values = frame.getAsJsonArray();
                frames.add(new double[] {values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble()});
            }
            if (!frames.isEmpty()) tracks.put(track.getKey(), frames);
        }
        assertTrue(tracks.size() >= 10, () -> "Expected ten authored bones in " + path + ", found " + tracks.size());
        return new Clip(file, tracks);
    }

    /** Every cyclic locomotion asset on disk. V6/V3 are what the renderer loads; V2 stays under test. */
    private static List<Clip> gaitClips() throws IOException { return List.of(load("murim_walk_v5.json"), load("murim_walk_v6_torso.json"), load("murim_run_v2.json"), load("murim_run_v3_torso.json")); }
    private static List<Clip> allClips() throws IOException { return List.of(load("murim_walk_v5.json"), load("murim_walk_v6_torso.json"), load("murim_run_v2.json"), load("murim_run_v3_torso.json"), load("murim_run.json"), load("murim_jump.json")); }
    /** Run uses a half-cycle hip convention; V5 walk is instantaneously mirrored. */
    private static List<Clip> instantlyMirroredHipClips() throws IOException { return List.of(load("murim_walk_v5.json"), load("murim_walk_v6_torso.json")); }
    private static final int ARM_LAG_TOLERANCE_FRAMES = 2;
    private static final List<String> TORSO = List.of("spine", "chest");
    private static final double MAX_TORSO_STEP_DEGREES = 2.0d;

    @Test void everyClipIsFiniteAndKeepsLimbsOnTheSingleRotationAxis() throws IOException {
        for (Clip clip : allClips()) for (var entry : clip.tracks().entrySet()) for (double[] frame : entry.getValue()) {
            for (double value : frame) assertTrue(Double.isFinite(value));
            if (!entry.getKey().equals("spine") && !entry.getKey().equals("chest")) { assertEquals(0d, frame[1], 1e-9); assertEquals(0d, frame[2], 1e-9); }
        }
    }

    @Test void kneesOnlyEverFlexBackwards() throws IOException {
        for (Clip clip : allClips()) for (String side : List.of("shin.L", "shin.R")) for (double[] frame : clip.tracks().get(side)) assertTrue(frame[0] >= -1e-9);
    }

    @Test void gaitClipsCloseTheirLoop() throws IOException {
        for (Clip clip : gaitClips()) for (var entry : clip.tracks().entrySet()) for (int axis = 0; axis < 3; axis++) assertEquals(entry.getValue().getFirst()[axis], entry.getValue().getLast()[axis], 1e-6, clip.name() + " loop failed for " + entry.getKey());
    }

    @Test void hipsAndShouldersStayExactlyOutOfPhaseLeftToRight() throws IOException {
        for (Clip clip : instantlyMirroredHipClips()) for (String limb : List.of("thigh", "upper_arm")) for (int frame = 0; frame < clip.loopFrames(); frame++) assertEquals(0d, clip.x(limb + ".L", frame) + clip.x(limb + ".R", frame), 1e-6);
    }

    @Test void kneesAndElbowsRepeatHalfACycleApart() throws IOException {
        for (Clip clip : gaitClips()) {
            int half = clip.loopFrames() / 2;
            for (String limb : List.of("shin", "lower_arm")) for (int frame = 0; frame < clip.loopFrames(); frame++) assertEquals(clip.x(limb + ".L", frame + half), clip.x(limb + ".R", frame), 1.0d, clip.name() + " " + limb + " must repeat half a cycle apart");
        }
    }

    @Test void contralateralArmsSwingOppositeTheLegs() throws IOException {
        for (Clip clip : gaitClips()) {
            int half = clip.loopFrames() / 2;
            assertTrue(Math.abs(bestLag(clip, "thigh.R", "upper_arm.R") - half) <= ARM_LAG_TOLERANCE_FRAMES);
        }
    }

    @Test void theDeepestKneeFoldBelongsToTheSwingHalf() throws IOException {
        for (Clip clip : gaitClips()) {
            int loop = clip.loopFrames();
            int contact = extremeFrame(clip, "thigh.L", true);
            int toeOff = extremeFrame(clip, "thigh.L", false);
            int deepestKnee = extremeFrame(clip, "shin.L", true);
            int framesIntoSwing = Math.floorMod(deepestKnee - toeOff, loop);
            assertTrue(framesIntoSwing <= loop / 2, () -> clip.name() + " folds the knee during stance");
            double straightestKnee = clip.tracks().get("shin.L").stream().mapToDouble(frame -> frame[0]).min().orElseThrow();
            double kneeAtContact = clip.tracks().get("shin.L").get(contact)[0];
            assertTrue(kneeAtContact <= straightestKnee + Math.toRadians(3.0d), () -> clip.name() + " bends too far at foot contact");
        }
    }

    @Test void runV2ChangesNothingButTheKnees() throws IOException {
        Clip source = load("murim_run.json"), revised = load("murim_run_v2.json");
        assertEquals(source.tracks().keySet(), revised.tracks().keySet());
        for (String bone : source.tracks().keySet()) if (!bone.startsWith("shin.")) {
            var sourceFrames = source.tracks().get(bone);
            var revisedFrames = revised.tracks().get(bone);
            assertEquals(sourceFrames.size(), revisedFrames.size());
            for (int frame = 0; frame < sourceFrames.size(); frame++) for (int axis = 0; axis < 3; axis++) assertEquals(sourceFrames.get(frame)[axis], revisedFrames.get(frame)[axis], 1e-12, bone + " frame " + frame);
        }
    }

    @Test void runV2KeepsTheSourceClipsHipConvention() throws IOException {
        Clip run = load("murim_run_v2.json");
        int half = run.loopFrames() / 2;
        for (int frame = 0; frame < run.loopFrames(); frame++) assertEquals(run.x("thigh.L", frame + half), run.x("thigh.R", frame), 1e-4);
    }

    @Test void runV3ChangesNothingButTheTorso() throws IOException {
        Clip source = load("murim_run_v2.json"), revised = load("murim_run_v3_torso.json");
        assertEquals(source.tracks().keySet(), revised.tracks().keySet());
        for (String bone : source.tracks().keySet()) if (!TORSO.contains(bone)) {
            var sourceFrames = source.tracks().get(bone);
            var revisedFrames = revised.tracks().get(bone);
            assertEquals(sourceFrames.size(), revisedFrames.size());
            for (int frame = 0; frame < sourceFrames.size(); frame++) for (int axis = 0; axis < 3; axis++) assertEquals(sourceFrames.get(frame)[axis], revisedFrames.get(frame)[axis], 1e-12, bone + " frame " + frame);
        }
    }

    private static double body(Clip clip, int frame, int axis) {
        int index = Math.floorMod(frame, clip.loopFrames());
        return Math.toDegrees(clip.tracks().get("spine").get(index)[axis] + clip.tracks().get("chest").get(index)[axis]);
    }

    @Test void theTorsoNeverSnapsBetweenFrames() throws IOException {
        for (Clip clip : gaitClips()) for (int axis = 0; axis < 3; axis++) for (int frame = 0; frame < clip.loopFrames(); frame++) {
            int measuredAxis = axis;
            double step = Math.abs(body(clip, frame + 1, axis) - body(clip, frame, axis));
            assertTrue(step <= MAX_TORSO_STEP_DEGREES, () -> clip.name() + " torso axis " + measuredAxis + " moves " + step + " degrees in one frame");
        }
    }

    @Test void everyShippedTorsoFitsItsConfiguredCeiling() throws IOException {
        assertTorsoFits(load("murim_walk_v6_torso.json"), setting("walk", "body-turn-degrees"), "walk.body-turn-degrees");
        assertTorsoFits(load("murim_run_v3_torso.json"), setting("run", "body-degrees"), "run.body-degrees");
    }

    private static void assertTorsoFits(Clip clip, double ceiling, String key) {
        for (int axis = 0; axis < 3; axis++) for (int frame = 0; frame < clip.loopFrames(); frame++) {
            int measuredAxis = axis;
            double value = Math.abs(body(clip, frame, axis));
            assertTrue(value <= ceiling, () -> clip.name() + " torso axis " + measuredAxis + " reaches " + value + ", above " + key + " = " + ceiling);
        }
    }

    @Test void theWalkTorsoStaysSmallerThanTheRunTorso() throws IOException {
        Clip walk = load("murim_walk_v6_torso.json"), run = load("murim_run_v3_torso.json");
        for (int axis = 0; axis < 3; axis++) {
            int measuredAxis = axis;
            double walkSwing = torsoSwing(walk, axis), runSwing = torsoSwing(run, axis);
            double walkPeak = torsoPeak(walk, axis), runPeak = torsoPeak(run, axis);
            if (runSwing <= 1e-9) {
                assertEquals(0d, walkSwing, 1e-9, "the run does not move on axis " + measuredAxis + ", so the walk must not either");
                continue;
            }
            assertTrue(walkSwing < runSwing, () -> "walk torso axis " + measuredAxis + " swings " + walkSwing + ", not below the run's " + runSwing);
            assertTrue(walkPeak < runPeak, () -> "walk torso axis " + measuredAxis + " peaks at " + walkPeak + ", not below the run's " + runPeak);
        }
    }

    private static double torsoSwing(Clip clip, int axis) {
        double low = body(clip, 0, axis), high = low;
        for (int frame = 1; frame < clip.loopFrames(); frame++) {
            double value = body(clip, frame, axis);
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        return high - low;
    }

    private static double torsoPeak(Clip clip, int axis) {
        double peak = 0d;
        for (int frame = 0; frame < clip.loopFrames(); frame++) peak = Math.max(peak, Math.abs(body(clip, frame, axis)));
        return peak;
    }

    @Test void walkV6ChangesNothingButTheTorso() throws IOException {
        Clip source = load("murim_walk_v5.json"), revised = load("murim_walk_v6_torso.json");
        assertEquals(source.tracks().keySet(), revised.tracks().keySet());
        for (String bone : source.tracks().keySet()) if (!TORSO.contains(bone)) {
            var sourceFrames = source.tracks().get(bone);
            var revisedFrames = revised.tracks().get(bone);
            assertEquals(sourceFrames.size(), revisedFrames.size());
            for (int frame = 0; frame < sourceFrames.size(); frame++) for (int axis = 0; axis < 3; axis++) assertEquals(sourceFrames.get(frame)[axis], revisedFrames.get(frame)[axis], 1e-12, bone + " frame " + frame);
        }
    }

    @Test void torsoYawStaysZeroWhileItsSignIsUnverified() throws IOException {
        for (Clip clip : gaitClips()) for (int frame = 0; frame < clip.loopFrames(); frame++)
            assertEquals(0d, body(clip, frame, 1), 1e-9, clip.name() + " writes a torso yaw");
    }

    private static double setting(String block, String key) throws IOException {
        Path settings = Path.of("src/main/resources/articulated-player.yml");
        assertTrue(Files.isRegularFile(settings), () -> "Missing settings file: " + settings);
        boolean inside = false;
        for (String line : Files.readAllLines(settings)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) { inside = line.startsWith(block + ":"); continue; }
            String trimmed = line.strip();
            if (inside && trimmed.startsWith(key + ":")) return Double.parseDouble(trimmed.substring(key.length() + 1).strip());
        }
        throw new AssertionError(block + "." + key + " is missing from " + settings);
    }

    @Test void retiredRunClipDoesNotDriftFurtherOutOfSymmetry() throws IOException {
        Clip run = load("murim_run.json");
        int half = run.loopFrames() / 2;
        double hip = 0d, knee = 0d;
        for (int frame = 0; frame < run.loopFrames(); frame++) {
            hip = Math.max(hip, Math.abs(run.x("thigh.L", frame) + run.x("thigh.R", frame)));
            knee = Math.max(knee, Math.abs(run.x("shin.R", frame) - run.x("shin.L", frame + half)));
        }
        double measuredHip = hip;
        double measuredKnee = knee;
        assertTrue(measuredHip <= 5.0d, () -> "run hip asymmetry grew: " + measuredHip);
        assertTrue(measuredKnee <= 1.5d, () -> "run knee mirror error grew: " + measuredKnee);
    }

    private static int extremeFrame(Clip clip, String bone, boolean maximum) {
        int best = 0;
        for (int frame = 1; frame < clip.loopFrames(); frame++) {
            if (maximum ? clip.x(bone, frame) > clip.x(bone, best) : clip.x(bone, frame) < clip.x(bone, best)) best = frame;
        }
        return best;
    }

    private static int bestLag(Clip clip, String first, String second) {
        int best = 0; double bestScore = Double.NEGATIVE_INFINITY;
        for (int shift = 0; shift < clip.loopFrames(); shift++) {
            double score = 0d;
            for (int frame = 0; frame < clip.loopFrames(); frame++) score += clip.x(first, frame) * clip.x(second, frame + shift);
            if (score > bestScore) { bestScore = score; best = shift; }
        }
        return best;
    }
}
