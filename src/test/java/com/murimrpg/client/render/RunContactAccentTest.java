package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Where the contacts are, and that the accent placed on them cannot misbehave, are both decidable
 * from the clip on disk and from pure arithmetic, so they are decided here rather than at F5.
 *
 * <p>The clip is read with a small reader of this file's own rather than through
 * AuthoredRigWalkAnimation, because that one needs a Minecraft ResourceManager. It reads the same
 * bytes the game loads.
 *
 * <p>What this cannot decide: whether the accent reads as weight rather than a twitch, how big it
 * should be, and whether the torso follow's lag on top of it feels right. Those are in the F5
 * matrix in CLAUDE_RUN_CONTACT_ACCENT_V1.md.
 */
class RunContactAccentTest {
    private static final Path CLIP = Path.of("src/main/resources/assets/murimrpg/player_motion/murim_run_v3_torso.json");
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");
    private static final Path MODEL = Path.of("src/main/java/com/murimrpg/client/render/ArticulatedHumanoidModel.java");
    private static final float TWO_PI = (float) (Math.PI * 2d);

    // ---------------------------------------------------------------- the clip, read from disk

    /** Dependency-free reader for the pretty-printed rotation-only clip: one bone's X track. */
    private static double[] track(String bone) throws IOException {
        String json = Files.readString(CLIP);
        int key = json.indexOf('"' + bone + '"');
        assertTrue(key >= 0, () -> "Missing bone " + bone + " in " + CLIP);
        int open = json.indexOf('[', key);
        int depth = 0, end = -1;
        for (int at = open; at < json.length(); at++) {
            char character = json.charAt(at);
            if (character == '[') depth++;
            else if (character == ']' && --depth == 0) { end = at; break; }
        }
        assertTrue(end > open, "Unterminated bone track");
        List<Double> numbers = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")
                .matcher(json.substring(open, end));
        while (matcher.find()) numbers.add(Double.parseDouble(matcher.group()));
        assertEquals(0, numbers.size() % 3, "Every authored frame is a three-component rotation");
        double[] x = new double[numbers.size() / 3];
        for (int frame = 0; frame < x.length; frame++) x[frame] = numbers.get(frame * 3);
        return x;
    }

    private static double setting(String block, String key) throws IOException {
        String yaml = Files.readString(SETTINGS);
        int start = yaml.indexOf("\n" + block + ":");
        assertTrue(start >= 0, () -> "Missing block " + block);
        int found = yaml.indexOf("\n  " + key + ":", start);
        assertTrue(found >= 0, () -> "Missing key " + block + "." + key);
        String line = yaml.substring(found + 1, yaml.indexOf('\n', found + 1));
        return Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
    }

    /** The joint angle the renderer actually poses: authored value, gain, then the YAML ceiling. */
    private static double limited(double radians, double gain, double ceilingDegrees) {
        double ceiling = Math.toRadians(ceilingDegrees);
        return Math.max(-ceiling, Math.min(ceiling, radians * gain));
    }

    private static int loopFrames(double[] track) { return track.length - 1; }

    private static int minimumFrame(double[] track) {
        int best = 0;
        for (int frame = 1; frame < loopFrames(track); frame++) if (track[frame] < track[best]) best = frame;
        return best;
    }

    // ---------------------------------------------------------------- contacts

    @Test void theContactPhasesAreTheOnesTheRunClipActuallyAuthors() throws IOException {
        double[] right = track("thigh.R"), left = track("thigh.L");
        int loop = loopFrames(right);
        assertEquals(16, loop, "the accent's frame arithmetic is quoted against a sixteen frame loop");
        // A knee only folds backwards and shin is positive in every frame, so a positive rotation
        // carries a hanging limb behind the joint. The thigh's minimum is therefore the leg reaching
        // furthest forward, and the instant it stops reaching is the strike.
        for (double shin : track("shin.R")) assertTrue(shin >= -1e-9, "shin must never fold forwards");
        for (double elbow : track("lower_arm.R")) assertTrue(elbow <= 1e-9, "lower_arm must never fold backwards");
        int rightContact = minimumFrame(right), leftContact = minimumFrame(left);
        assertEquals(0, rightContact, "the right foot must strike at the start of the loop");
        assertEquals(loop / 2, leftContact, "the left foot must strike exactly half a cycle later");
        assertEquals(phaseOfFrame(rightContact, loop), RunContactAccent.RIGHT_CONTACT_PHASE, 1e-6f,
                "RIGHT_CONTACT_PHASE no longer matches the clip on disk");
        assertEquals(phaseOfFrame(leftContact, loop), RunContactAccent.LEFT_CONTACT_PHASE, 1e-6f,
                "LEFT_CONTACT_PHASE no longer matches the clip on disk");
    }

    private static float phaseOfFrame(int frame, int loop) { return TWO_PI * frame / loop; }

    @Test void theTwoLegsAreExactlyHalfACycleApartInTheClip() throws IOException {
        double[] rightThigh = track("thigh.R"), leftThigh = track("thigh.L");
        double[] rightShin = track("shin.R"), leftShin = track("shin.L");
        int loop = loopFrames(rightThigh), half = loop / 2;
        double worst = 0d;
        for (int frame = 0; frame < loop; frame++) {
            worst = Math.max(worst, Math.abs(rightThigh[frame] - leftThigh[(frame + half) % loop]));
            worst = Math.max(worst, Math.abs(rightShin[frame] - leftShin[(frame + half) % loop]));
        }
        final double measured = Math.toDegrees(worst);
        assertTrue(measured < 1.0e-3d,
                () -> "the legs are " + measured + " degrees from being half a cycle apart");
    }

    @Test void theStrikingFootIsTheLowerOfTheTwo() throws IOException {
        double stride = setting("run", "stride-degrees"), knee = setting("run", "knee-degrees");
        double legGain = setting("retarget", "upper-leg-gain"), shinGain = setting("retarget", "lower-leg-gain");
        double[] rightThigh = track("thigh.R"), leftThigh = track("thigh.L");
        double[] rightShin = track("shin.R"), leftShin = track("shin.L");
        int loop = loopFrames(rightThigh);
        for (int contact : new int[] {0, loop / 2}) {
            double depthRight = depth(rightThigh[contact], rightShin[contact], legGain, shinGain, stride, knee);
            double depthLeft = depth(leftThigh[contact], leftShin[contact], legGain, shinGain, stride, knee);
            boolean rightStrikes = contact == 0;
            final int frame = contact;
            assertTrue(rightStrikes ? depthRight > depthLeft : depthLeft > depthRight,
                    () -> "at frame " + frame + " the striking foot is not the lower one: "
                            + depthRight + " vs " + depthLeft);
        }
    }

    /** Foot below the hip, in pixels: two six-pixel segments, the lower one hinged on the upper. */
    private static double depth(double thigh, double shin, double legGain, double shinGain,
                                double strideCeiling, double kneeCeiling) {
        double hip = limited(thigh, legGain, strideCeiling);
        double knee = limited(shin, shinGain, kneeCeiling);
        return 6d * Math.cos(hip) + 6d * Math.cos(hip + knee);
    }

    // ---------------------------------------------------------------- the envelope

    @Test void zeroDegreesIsExactlyZeroEverywhere() {
        for (int step = 0; step <= 4000; step++) {
            float phase = TWO_PI * step / 4000f;
            assertEquals(0f, RunContactAccent.rollRadians(phase, 0f), 0f,
                    "run.contact-roll-degrees 0.0 must leave the authored roll bit-identical");
            assertEquals(0f, RunContactAccent.rollRadians(phase, -0.1f), 0f, "a negative amplitude is off");
            assertEquals(0f, RunContactAccent.rollRadians(phase, Float.NaN), 0f, "a broken amplitude is off");
            assertEquals(0f, RunContactAccent.rollRadians(phase, Float.POSITIVE_INFINITY), 0f, "an infinite amplitude is off");
        }
    }

    @Test void theTwoImpactPeaksAreExactlyHalfACycleApart() {
        int steps = 20000;
        float rightPeak = 0f, leftPeak = 0f, bestRight = 0f, bestLeft = 0f;
        for (int step = 0; step < steps; step++) {
            float phase = TWO_PI * step / steps;
            float value = RunContactAccent.envelope(phase);
            if (value < bestRight) { bestRight = value; rightPeak = phase; }
            if (value > bestLeft) { bestLeft = value; leftPeak = phase; }
        }
        assertEquals(-1f, bestRight, 1e-3f, "the right impact must reach full weight");
        assertEquals(1f, bestLeft, 1e-3f, "the left impact must reach full weight");
        float separation = leftPeak - rightPeak;
        assertEquals((float) Math.PI, separation, 2f * TWO_PI / steps,
                "the two impacts must be exactly half a stride apart");
    }

    @Test void everyPhaseIsMirroredExactlyHalfACycleLater() {
        for (int step = 0; step < 4000; step++) {
            float phase = TWO_PI * step / 4000f;
            float here = RunContactAccent.envelope(phase);
            float halfLater = RunContactAccent.envelope(phase + (float) Math.PI);
            assertEquals(-here, halfLater, 1e-4f, "the other foot must do the mirror of this one");
        }
    }

    @Test void theAccentIsSilentOverHalfOfEveryStep() {
        for (int step = 0; step <= 2000; step++) {
            float sinceContact = RunContactAccent.DECAY_FRACTION
                    + (1f - RunContactAccent.DECAY_FRACTION) * step / 2000f;
            for (int half = 0; half < 4; half++) {
                float phase = (float) Math.PI * (half + Math.min(sinceContact, 0.999999f));
                assertEquals(0f, RunContactAccent.envelope(phase), 0f,
                        "the accent must be exactly zero once the landing is absorbed");
            }
        }
        assertEquals(0f, RunContactAccent.envelope(0f), 0f, "the contact frame itself must be zero");
        assertEquals(0f, RunContactAccent.envelope((float) Math.PI), 0f, "the other contact frame too");
    }

    @Test void theEnvelopeNeverLeavesMinusOneToOneAndTheAccentNeverExceedsItsDegrees() {
        for (float degrees : new float[] {0.25f, 1f, RunContactAccent.MAXIMUM_DEGREES}) {
            float amplitude = (float) Math.toRadians(degrees);
            for (int step = 0; step <= 20000; step++) {
                float phase = TWO_PI * step / 20000f;
                float value = RunContactAccent.envelope(phase);
                assertTrue(value >= -1f && value <= 1f, "the envelope left -1..1");
                float accent = RunContactAccent.rollRadians(phase, amplitude);
                assertTrue(Math.abs(accent) <= amplitude + 1e-7f,
                        () -> "the accent exceeded run.contact-roll-degrees " + degrees);
            }
        }
    }

    @Test void nothingSnapsAcrossAWrapAReversalOrALongPause() {
        // The layer holds no state, so a phase that wraps, runs backwards, repeats or stops has
        // nothing to corrupt. What has to hold is that the value itself never jumps.
        float previous = RunContactAccent.envelope(-TWO_PI);
        float worstStep = 0f;
        for (int step = -8000; step <= 8000; step++) {
            float phase = TWO_PI * step / 4000f;
            float value = RunContactAccent.envelope(phase);
            worstStep = Math.max(worstStep, Math.abs(value - previous));
            previous = value;
        }
        final float measured = worstStep;
        assertTrue(measured < 0.02f, () -> "the envelope jumped " + measured + " in one phase step");
        // Repeating the same phase, as a paused player does, repeats the same answer.
        assertEquals(RunContactAccent.envelope(1.234f), RunContactAccent.envelope(1.234f), 0f);
        // And a value seen after a broken one is the same as if the broken one never happened.
        float clean = RunContactAccent.envelope(0.3f);
        RunContactAccent.envelope(Float.NaN);
        RunContactAccent.envelope(Float.POSITIVE_INFINITY);
        assertEquals(clean, RunContactAccent.envelope(0.3f), 0f, "the layer must be stateless");
    }

    @Test void aBrokenPhaseIsZeroRatherThanPropagated() {
        for (float broken : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(0f, RunContactAccent.envelope(broken), 0f, "a broken phase must be silent");
            assertEquals(0f, RunContactAccent.rollRadians(broken, 0.05f), 0f, "a broken phase must be silent");
        }
    }

    @Test void theAccentPressesTheSameWayTheAuthoredRollAlreadyLeans() throws IOException {
        // The clip already tips the torso toward whichever foot is landing: body.zRot is at its
        // most negative on the right contact frame and its most positive on the left. The accent
        // presses a little further that way while the weight arrives, so its sign has to agree
        // frame for frame, or it would be cancelling the clip instead of accenting it.
        double[] spineZ = componentTrack("spine", 2), chestZ = componentTrack("chest", 2);
        int loop = spineZ.length - 1;
        double rollAtRightContact = spineZ[0] + chestZ[0];
        double rollAtLeftContact = spineZ[loop / 2] + chestZ[loop / 2];
        assertTrue(rollAtRightContact < 0d, "the clip must already tip toward the right foot as it lands");
        assertTrue(rollAtLeftContact > 0d, "the clip must already tip toward the left foot as it lands");
        for (int step = 1; step < 40; step++) {
            float sinceContact = RunContactAccent.DECAY_FRACTION * step / 40f;
            float right = RunContactAccent.envelope((float) Math.PI * sinceContact);
            float left = RunContactAccent.envelope((float) Math.PI * (1f + sinceContact));
            assertTrue(right < 0f && Math.signum(right) == Math.signum((float) rollAtRightContact),
                    "the right landing must press the same way the clip already leans");
            assertTrue(left > 0f && Math.signum(left) == Math.signum((float) rollAtLeftContact),
                    "the left landing must press the same way the clip already leans");
        }
    }

    // ---------------------------------------------------------------- the ceiling and the setting

    @Test void theAuthoredRollPlusTheLargestAccentStaysUnderTheRunCeiling() throws IOException {
        double ceiling = setting("run", "body-degrees");
        double worstAuthored = 0d;
        String json = Files.readString(CLIP);
        assertTrue(json.contains("\"rotation_only\": true"));
        double[] spineZ = componentTrack("spine", 2), chestZ = componentTrack("chest", 2);
        for (int frame = 0; frame < spineZ.length; frame++) {
            worstAuthored = Math.max(worstAuthored, Math.abs(Math.toDegrees(spineZ[frame] + chestZ[frame])));
        }
        final double authored = worstAuthored;
        assertTrue(authored + RunContactAccent.MAXIMUM_DEGREES <= ceiling,
                () -> "authored roll " + authored + " plus the largest accent "
                        + RunContactAccent.MAXIMUM_DEGREES + " would be clamped by run.body-degrees " + ceiling);
    }

    private static double[] componentTrack(String bone, int axis) throws IOException {
        String json = Files.readString(CLIP);
        int key = json.indexOf('"' + bone + '"');
        int open = json.indexOf('[', key);
        int depth = 0, end = -1;
        for (int at = open; at < json.length(); at++) {
            char character = json.charAt(at);
            if (character == '[') depth++;
            else if (character == ']' && --depth == 0) { end = at; break; }
        }
        List<Double> numbers = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")
                .matcher(json.substring(open, end));
        while (matcher.find()) numbers.add(Double.parseDouble(matcher.group()));
        double[] values = new double[numbers.size() / 3];
        for (int frame = 0; frame < values.length; frame++) values[frame] = numbers.get(frame * 3 + axis);
        return values;
    }

    @Test void theSettingShipsOffAndIsBoundedToAnAccent() throws IOException {
        String yaml = Files.readString(SETTINGS);
        assertTrue(yaml.contains("contact-roll-degrees: 0.0"),
                "articulated-player.yml must ship run.contact-roll-degrees off, so F5 starts on today's baseline");
        int key = yaml.indexOf("contact-roll-degrees:");
        String comment = yaml.substring(Math.max(0, key - 900), key);
        assertTrue(comment.contains("client restart"), "the key must say it is only read at client start");
        assertTrue(comment.contains("0..2.0"), "the key must state its validated range");
        assertEquals(2f, RunContactAccent.MAXIMUM_DEGREES, 0f,
                "the documented range and the code ceiling must agree");
    }

    // ---------------------------------------------------------------- where it is wired

    @Test void theAccentIsWiredOnlyIntoTheGroundedRunPath() throws IOException {
        String source = Files.readString(MODEL);
        int declaration = source.indexOf("private void applyContactAccent(");
        assertTrue(declaration > 0, "applyContactAccent must exist");
        int call = source.indexOf("applyContactAccent(phase, speed);");
        assertTrue(call > 0, "applyContactAccent must be called");
        assertEquals(call, source.lastIndexOf("applyContactAccent(phase, speed);"),
                "applyContactAccent must be called exactly once");
        int grounded = source.indexOf("wasAirborneFrame = false;");
        int easing = source.indexOf("torsoFollow.advance(");
        assertTrue(grounded > 0 && easing > grounded, "the grounded path must still precede the torso easing");
        assertTrue(call > grounded && call < easing,
                "the accent belongs after the grounded clip pose and before the torso easing");
        assertTrue(section(source, "private void applyLocomotionPose(").indexOf("applyContactAccent") < 0,
                "applyLocomotionPose is also used by take-off and landing, so it must stay accent-free");
        assertTrue(section(source, "private void applyJumpPose(").indexOf("applyContactAccent") < 0,
                "the jump poses must stay accent-free");
        assertTrue(source.contains("if (reversed || runWeight <= 0f || speed <= 0f) return;"),
                "the accent must be gated on a grounded forward run");
    }

    /** Everything from a method's declaration to the start of the next one, good enough to scan. */
    private static String section(String source, String declaration) {
        int start = source.indexOf(declaration);
        assertTrue(start > 0, () -> "Missing " + declaration);
        int end = source.indexOf("\n    private ", start + declaration.length());
        return source.substring(start, end < 0 ? source.length() : end);
    }
}
