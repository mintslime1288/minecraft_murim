package com.murimrpg.client.render;

import static com.murimrpg.client.render.ArticulatedTorsoHierarchy.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Which parts move when the torso leans is pure forward kinematics on a chain of pivots, and no
 * Minecraft type is needed to work it out. Rotation is about X and nothing varies along X, so the
 * whole layout reduces to points in the Y/Z plane, y growing downward and -z pointing forward.
 *
 * <p>What this cannot decide is anything that needs the client: whether the skin's pixels read
 * well on a leaning torso, where a held item ends up once the arms hang from the body, and how
 * much lean looks right. Those are in the F5 matrix in
 * CLAUDE_TORSO_HIP_CONTINUITY_RUN_LEAN_V1.md.
 */
class ArticulatedTorsoHierarchyTest {
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");

    /** A point in the torso's own space, and where it ends up once the torso has leaned. */
    private static float[] leaned(float localY, float localZ, float pivotY, double lean) {
        float c = (float) Math.cos(lean), s = (float) Math.sin(lean);
        float y = localY - pivotY;
        return new float[] {y * c - localZ * s + pivotY, y * s + localZ * c};
    }

    private static float distance(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /** How far the torso's bottom corner runs away from the thigh's top corner at this lean. */
    private static float hipSeparation(float pivotY, double lean) {
        float worst = 0f;
        for (float z : new float[] {-THIGH_HALF_DEPTH, THIGH_HALF_DEPTH}) {
            worst = Math.max(worst, distance(leaned(HIP_Y, z, pivotY, lean), new float[] {HIP_Y, z}));
        }
        return worst;
    }

    /**
     * How far the shoulder runs away from the arm that is supposed to hang off it. An arm that
     * hangs from the torso travels with the shoulder, so the answer is zero by construction; an
     * arm on root does not move at all, so the answer is the shoulder's own travel.
     */
    private static float shoulderSeparation(float pivotY, boolean armsOnTorso, double lean) {
        float[] shoulder = leaned(SHOULDER_Y, 0f, pivotY, lean);
        float[] arm = armsOnTorso ? shoulder : new float[] {SHOULDER_Y, 0f};
        return distance(shoulder, arm);
    }

    private static double ceilingRadians(String block, String key) throws IOException {
        return Math.toRadians(setting(block, key));
    }

    /** Minimal reader for the two-space-indented blocks articulated-player.yml uses. */
    private static String rawSetting(String block, String key) throws IOException {
        assertTrue(Files.isRegularFile(SETTINGS), () -> "Missing settings file: " + SETTINGS);
        boolean inside = false;
        for (String line : Files.readAllLines(SETTINGS)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) { inside = line.startsWith(block + ":"); continue; }
            String trimmed = line.strip();
            if (inside && trimmed.startsWith(key + ":")) return trimmed.substring(key.length() + 1).strip();
        }
        throw new AssertionError(block + "." + key + " is missing from " + SETTINGS);
    }

    private static double setting(String block, String key) throws IOException {
        return Double.parseDouble(rawSetting(block, key));
    }

    // --- tests ---------------------------------------------------------------------------

    @Test void everyHierarchyConstantIsFiniteAndSane() {
        for (float value : new float[] {TORSO_WIDTH, TORSO_HEIGHT, TORSO_DEPTH, TORSO_HALF_DEPTH,
                SHOULDER_Y, HIP_Y, ARM_OFFSET_X, LEG_OFFSET_X, THIGH_HALF_DEPTH}) {
            assertTrue(Float.isFinite(value) && value > 0f, () -> "bad hierarchy constant: " + value);
        }
        assertEquals(SHOULDER_Y - HIP_Y, ARM_OFFSET_Y_FROM_HIP, 1e-6f);
        assertEquals(-TORSO_HEIGHT, HIP_PIVOTED_BOX_ORIGIN_Y, 1e-6f);
        assertEquals(0f, NECK_PIVOTED_BOX_ORIGIN_Y, 1e-6f);
    }

    @Test void bothLayoutsPlaceEveryPartIdenticallyWhileStanding() {
        for (float[] local : new float[][] {{0f, 0f}, {HIP_Y, 0f}, {HIP_Y, TORSO_HALF_DEPTH},
                {HIP_Y, -TORSO_HALF_DEPTH}, {SHOULDER_Y, 0f}, {TORSO_HEIGHT / 2f, TORSO_HALF_DEPTH}}) {
            float[] neck = leaned(local[0], local[1], NECK_Y, 0d);
            float[] hip = leaned(local[0], local[1], HIP_Y, 0d);
            assertEquals(0f, distance(neck, hip), 1e-6f,
                    "the two layouts must be indistinguishable while standing, so the flag is a safe default");
        }
        assertEquals(0f, hipSeparation(NECK_Y, 0d), 1e-6f);
        assertEquals(0f, hipSeparation(HIP_Y, 0d), 1e-6f);
        assertEquals(0f, shoulderSeparation(NECK_Y, false, 0d), 1e-6f);
        assertEquals(0f, shoulderSeparation(HIP_Y, true, 0d), 1e-6f);
    }

    @Test void theShippedDefaultIsTheLayoutThatAlreadyPassedF5() throws IOException {
        assertEquals("false", rawSetting("locomotion", "torso-hip-pivot"),
                "the bundled default must keep the current build's layout so the flag can be compared against it");
    }

    @Test void turningAtTheNeckPullsTheHipOffTheThighs() throws IOException {
        double runLean = ceilingRadians("run", "body-degrees");
        double walkLean = ceilingRadians("walk", "body-turn-degrees");
        float atRun = hipSeparation(NECK_Y, runLean);
        float atWalk = hipSeparation(NECK_Y, walkLean);
        assertTrue(atRun > 1.5f, () -> "the neck-pivoted hip separation measured only " + atRun
                + " px; if that is now small the premise of this patch has changed");
        assertTrue(atWalk > .5f, () -> "walk hip separation measured " + atWalk);
        assertTrue(atRun > atWalk, "leaning further must separate the hip further");
    }

    @Test void turningAtTheHipKeepsTheTorsoOnTheThighs() throws IOException {
        for (String[] ceiling : new String[][] {{"walk", "body-turn-degrees"}, {"run", "body-degrees"}, {"jump", "body-degrees"}}) {
            double lean = ceilingRadians(ceiling[0], ceiling[1]);
            float neck = hipSeparation(NECK_Y, lean);
            float hip = hipSeparation(HIP_Y, lean);
            assertTrue(hip < neck, () -> ceiling[0] + ": turning at the hip must separate less than turning at the neck");
            assertEquals(hingeWedge(lean), hip, 1e-4f,
                    ceiling[0] + ": what is left at the hip should be the ordinary hinge wedge and nothing more");
        }
    }

    @Test void theArmsOnlyKeepTheirShouldersWhenTheyHangFromTheTorso() throws IOException {
        double runLean = ceilingRadians("run", "body-degrees");
        float hipWithArmsOnTorso = shoulderSeparation(HIP_Y, true, runLean);
        float hipWithArmsOnRoot = shoulderSeparation(HIP_Y, false, runLean);
        float neckWithArmsOnRoot = shoulderSeparation(NECK_Y, false, runLean);
        assertEquals(0f, hipWithArmsOnTorso, 1e-6f, "arms hanging from the torso must travel with the shoulder");
        assertTrue(hipWithArmsOnRoot > neckWithArmsOnRoot, () -> "moving the pivot to the hip without moving the arms "
                + "makes the shoulder worse (" + hipWithArmsOnRoot + " vs " + neckWithArmsOnRoot
                + "), which is why the two are one flag and not two");
    }

    @Test void theHipPivotIsNeverWorseThanTheNeckPivotAtAnyLeanInRange() throws IOException {
        double widest = Math.max(ceilingRadians("run", "body-degrees"), ceilingRadians("jump", "body-degrees"));
        for (int step = 0; step <= 200; step++) {
            double lean = widest * step / 200d;
            float neck = hipSeparation(NECK_Y, lean);
            float hip = hipSeparation(HIP_Y, lean);
            assertTrue(hip <= neck + 1e-6f, () -> "at " + Math.toDegrees(lean) + " degrees the hip pivot separated "
                    + hip + " px against the neck pivot's " + neck);
            assertTrue(Float.isFinite(hip) && Float.isFinite(neck));
        }
    }

    @Test void aPositiveLeanStillMeansShouldersForwardOfHips() throws IOException {
        double runLean = ceilingRadians("run", "body-degrees");
        float neckHipZ = leaned(HIP_Y, 0f, NECK_Y, runLean)[1];
        float neckShoulderZ = leaned(SHOULDER_Y, 0f, NECK_Y, runLean)[1];
        float hipHipZ = leaned(HIP_Y, 0f, HIP_Y, runLean)[1];
        float hipShoulderZ = leaned(SHOULDER_Y, 0f, HIP_Y, runLean)[1];
        assertTrue(neckShoulderZ < neckHipZ, "neck layout: the shoulders must sit forward of the hips");
        assertTrue(hipShoulderZ < hipHipZ, "hip layout: the shoulders must sit forward of the hips");
        assertTrue(hipShoulderZ < 0f, "hip layout: a positive lean must carry the shoulders toward -z, which is forward");
    }

    @Test void theHeadAndTheLegsNeverMoveInEitherLayout() throws IOException {
        double runLean = ceilingRadians("run", "body-degrees");
        // Head and both legs stay on root in both layouts, so their pivots are the layout-independent
        // constants themselves. Asserting that keeps a future edit from quietly reparenting them and
        // swinging the feet out from under the player.
        assertEquals(0f, NECK_Y, 1e-6f, "the head pivot must stay at the model origin");
        assertEquals(HIP_Y, 12f, 1e-6f, "the leg pivots must stay at the hip line");
        assertTrue(runLean > 0d);
    }

    @Test void everyCeilingStaysInsideTheValidatorRange() throws IOException {
        for (String[] ceiling : new String[][] {{"walk", "body-turn-degrees"}, {"run", "body-degrees"}, {"jump", "body-degrees"}}) {
            double degrees = setting(ceiling[0], ceiling[1]);
            assertTrue(degrees >= 0d && degrees <= 55d,
                    () -> ceiling[0] + "." + ceiling[1] + " = " + degrees + " is outside the 0..55 the settings reader allows");
        }
    }
}
