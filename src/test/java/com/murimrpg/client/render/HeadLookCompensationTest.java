package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Whether the head still looks exactly where the mouse points once it hangs off a leaning torso is
 * pure rotation algebra, so it can be settled here rather than at F5. The convention these tests
 * hold the solver to is the one read out of Minecraft 26.2's own ModelPart - rotation
 * {@code Rz(zRot) * Ry(yRot) * Rx(xRot)}, a child composed on the right of its parent - and the
 * matrices below are built up from three separate axis rotations rather than from the solver's own
 * helper, so a sign error in that helper cannot hide inside a test that agrees with it.
 *
 * <p>What this cannot decide: whether the head reads as connected to the neck at F5, whether the
 * hat layer and the skin's face pixels still land right, and how the head looks while the camera
 * itself is turning. Those are in the F5 matrix in CLAUDE_HEAD_FOLLOWS_TORSO_V1.md.
 */
class HeadLookCompensationTest {
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");
    /** Float rounding of sin, cos, atan2 and nine multiply-adds, and nothing else. */
    private static final double ROUNDING_TOLERANCE_DEGREES = 0.05d;

    private static double[][] aboutX(double a) {
        return new double[][] {{1, 0, 0}, {0, Math.cos(a), -Math.sin(a)}, {0, Math.sin(a), Math.cos(a)}};
    }
    private static double[][] aboutY(double a) {
        return new double[][] {{Math.cos(a), 0, Math.sin(a)}, {0, 1, 0}, {-Math.sin(a), 0, Math.cos(a)}};
    }
    private static double[][] aboutZ(double a) {
        return new double[][] {{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}};
    }
    private static double[][] times(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 3; column++)
                for (int k = 0; k < 3; k++) result[row][column] += a[row][k] * b[k][column];
        return result;
    }
    /** What ModelPart makes of a part's three rotation fields. */
    private static double[][] part(double xRot, double yRot, double zRot) {
        return times(aboutZ(zRot), times(aboutY(yRot), aboutX(xRot)));
    }
    /**
     * The geodesic angle between two rotations, in degrees: zero only when they are the same
     * rotation, whatever Euler triple either of them was written as. The trace of {@code a^T b} is
     * {@code 1 + 2 cos(angle)}, and the columns of {@code a^T} are the columns of {@code a}.
     */
    private static double separation(double[][] a, double[][] b) {
        double trace = 0d;
        for (int column = 0; column < 3; column++)
            for (int row = 0; row < 3; row++) trace += a[row][column] * b[row][column];
        return Math.toDegrees(Math.acos(Math.max(-1d, Math.min(1d, (trace - 1d) / 2d))));
    }

    /** The world rotation the head ends up with when the solver is asked to compensate this body. */
    private static double[][] solvedWorld(float bodyX, float bodyY, float bodyZ, float pitch, float yaw) {
        float[] out = new float[HeadLookCompensation.ANGLE_COUNT];
        assertTrue(HeadLookCompensation.solve(bodyX, bodyY, bodyZ, pitch, yaw, out),
                () -> "the solver refused a finite input: body " + bodyX + "/" + bodyY + "/" + bodyZ);
        return times(part(bodyX, bodyY, bodyZ), part(out[0], out[1], out[2]));
    }

    /** Every body rotation the clips and the YAML ceilings can actually produce. */
    private static float[][] bodyRotations() {
        float[] leans = {0f, 4.050f, 4.950f, 8.250f, 9.450f, 12f};
        float[] rolls = {-12f, -3f, -1.5f, 0f, 1.5f, 3f, 12f};
        float[] yaws = {-12f, 0f, 12f};
        float[][] result = new float[leans.length * rolls.length * yaws.length][3];
        int at = 0;
        for (float lean : leans)
            for (float roll : rolls)
                for (float yaw : yaws)
                    result[at++] = new float[] {radians(lean), radians(yaw), radians(roll)};
        return result;
    }

    private static float radians(double degrees) { return (float) Math.toRadians(degrees); }

    @Test void aTorsoAtRestGivesBackExactlyTheRotationThatShipsToday() {
        // The gate for "default off is bit-identical" is the flag itself, but a standing player with
        // the flag on must also be bit-identical, because the authored torso is exactly zero at a
        // standstill and the torso easing settles to exactly zero.
        float[] out = new float[HeadLookCompensation.ANGLE_COUNT];
        for (int yawDegrees = -180; yawDegrees <= 180; yawDegrees += 5) {
            for (int pitchDegrees = -90; pitchDegrees <= 90; pitchDegrees += 5) {
                float pitch = radians(pitchDegrees), yaw = radians(yawDegrees);
                assertTrue(HeadLookCompensation.solve(0f, 0f, 0f, pitch, yaw, out));
                assertEquals(pitch, out[0], 0f, "a torso at rest must leave the look pitch untouched");
                assertEquals(yaw, out[1], 0f, "a torso at rest must leave the look yaw untouched");
                assertEquals(0f, out[2], 0f, "a torso at rest must leave the head roll at exactly zero");
            }
        }
    }

    @Test void theWorldLookStaysTheMouseLookAtEveryBodyRotationTheClipsProduce() {
        double worst = 0d;
        for (float[] body : bodyRotations()) {
            for (int yawDegrees = -180; yawDegrees <= 180; yawDegrees += 5) {
                for (int pitchDegrees = -90; pitchDegrees <= 90; pitchDegrees += 5) {
                    float pitch = radians(pitchDegrees), yaw = radians(yawDegrees);
                    double[][] wanted = part(pitch, yaw, 0f);
                    worst = Math.max(worst, separation(solvedWorld(body[0], body[1], body[2], pitch, yaw), wanted));
                }
            }
        }
        final double measured = worst;
        assertTrue(measured <= ROUNDING_TOLERANCE_DEGREES,
                () -> "the head's world rotation drifted " + measured + " degrees from the mouse look");
    }

    @Test void componentwiseSubtractionIsNotAnAlternative() {
        // Recorded so the rejected design stays rejected for a reason: Euler angles do not subtract,
        // and the residual grows with head yaw because the lean is removed in the wrong frame.
        float lean = radians(9.450d), roll = radians(3d);
        double worstNearCentre = 0d, worstOverall = 0d;
        for (int yawDegrees = -180; yawDegrees <= 180; yawDegrees += 5) {
            for (int pitchDegrees = -90; pitchDegrees <= 90; pitchDegrees += 5) {
                float pitch = radians(pitchDegrees), yaw = radians(yawDegrees);
                double[][] wanted = part(pitch, yaw, 0f);
                double[][] naive = times(part(lean, 0f, roll), part(pitch - lean, yaw, -roll));
                double error = separation(naive, wanted);
                worstOverall = Math.max(worstOverall, error);
                if (Math.abs(yawDegrees) <= 15) worstNearCentre = Math.max(worstNearCentre, error);
            }
        }
        final double centre = worstNearCentre, overall = worstOverall;
        assertTrue(centre > 2d, () -> "subtraction was expected to be wrong even inside 15 degrees of yaw, measured " + centre);
        assertTrue(overall > 15d, () -> "subtraction was expected to be far worse at large yaw, measured " + overall);
        // And the exact solver has to beat it by orders of magnitude on the same input.
        assertTrue(separation(solvedWorld(lean, 0f, roll, radians(30d), radians(120d)), part(radians(30d), radians(120d), 0f))
                <= ROUNDING_TOLERANCE_DEGREES, "the exact solver must not share subtraction's error");
    }

    @Test void theYawSingularityStillRendersTheRightLook() {
        // Head yaw is the middle angle of a ZYX chart, so 90 degrees is where that chart is
        // ill-conditioned. The angles there may be large; the composed rotation may not be wrong.
        float lean = radians(9.450d), roll = radians(3d);
        for (double yawDegrees : new double[] {-90.1d, -90d, -89.99d, 89.99d, 90d, 90.01d, 90.1d}) {
            for (double pitchDegrees : new double[] {-90d, -45d, 0d, 45d, 90d}) {
                float pitch = radians(pitchDegrees), yaw = radians(yawDegrees);
                double error = separation(solvedWorld(lean, 0f, roll, pitch, yaw), part(pitch, yaw, 0f));
                assertTrue(error <= ROUNDING_TOLERANCE_DEGREES,
                        () -> "yaw " + yawDegrees + " pitch " + pitchDegrees + " drifted " + error + " degrees");
            }
        }
    }

    @Test void theWorldLookIsContinuousAcrossAFullYawSweep() {
        // Large jumps in the returned angles are allowed near the singularity; a jump in what the
        // player actually sees is not. One degree of mouse movement may not move the head more.
        float lean = radians(9.450d), roll = radians(3d);
        for (double pitchDegrees : new double[] {-85d, -45d, 0d, 45d, 85d}) {
            double[][] previous = null;
            for (double yawDegrees = -180d; yawDegrees <= 180d; yawDegrees += 1d) {
                double[][] world = solvedWorld(lean, 0f, roll, radians(pitchDegrees), radians(yawDegrees));
                if (previous != null) {
                    double step = separation(previous, world);
                    final double at = yawDegrees;
                    assertTrue(step <= 1.1d, () -> "the head jumped " + step + " degrees at yaw " + at);
                }
                previous = world;
            }
        }
    }

    @Test void aNonFiniteInputIsRefusedRatherThanPropagated() {
        float[] out = new float[HeadLookCompensation.ANGLE_COUNT];
        out[0] = 7f; out[1] = 8f; out[2] = 9f;
        for (float broken : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(HeadLookCompensation.solve(broken, 0f, 0f, 0.1f, 0.2f, out), "a broken body lean must be refused");
            assertFalse(HeadLookCompensation.solve(0f, broken, 0f, 0.1f, 0.2f, out), "a broken body yaw must be refused");
            assertFalse(HeadLookCompensation.solve(0f, 0f, broken, 0.1f, 0.2f, out), "a broken body roll must be refused");
            assertFalse(HeadLookCompensation.solve(0.1f, 0f, 0f, broken, 0.2f, out), "a broken look pitch must be refused");
            assertFalse(HeadLookCompensation.solve(0.1f, 0f, 0f, 0.1f, broken, out), "a broken look yaw must be refused");
        }
        assertEquals(7f, out[0], 0f, "a refused solve must not write anything");
        assertEquals(8f, out[1], 0f, "a refused solve must not write anything");
        assertEquals(9f, out[2], 0f, "a refused solve must not write anything");
        assertFalse(HeadLookCompensation.solve(0.1f, 0f, 0f, 0.1f, 0.2f, null), "a missing output buffer must be refused");
        assertFalse(HeadLookCompensation.solve(0.1f, 0f, 0f, 0.1f, 0.2f, new float[2]), "a short output buffer must be refused");
    }

    @Test void everyAngleTheSolverReturnsIsFinite() {
        float[] out = new float[HeadLookCompensation.ANGLE_COUNT];
        for (float[] body : bodyRotations()) {
            for (int yawDegrees = -180; yawDegrees <= 180; yawDegrees += 3) {
                for (int pitchDegrees = -90; pitchDegrees <= 90; pitchDegrees += 3) {
                    assertTrue(HeadLookCompensation.solve(body[0], body[1], body[2],
                            radians(pitchDegrees), radians(yawDegrees), out));
                    for (float angle : out) assertTrue(Float.isFinite(angle), "the solver returned a non-finite angle");
                }
            }
        }
    }

    @Test void theNeckOffsetPutsTheHeadPivotBackOnTheModelOrigin() {
        // The whole point of the offset: a standing player's head must occupy exactly the point it
        // occupies today, or turning the flag on would move the head before anything leaned.
        assertEquals(ArticulatedTorsoHierarchy.NECK_Y,
                ArticulatedTorsoHierarchy.HIP_Y + HeadLookCompensation.NECK_OFFSET_Y, 1e-6f,
                "hip pivot plus neck offset must land on the neck");
        assertEquals(-ArticulatedTorsoHierarchy.TORSO_HEIGHT, HeadLookCompensation.NECK_OFFSET_Y, 1e-6f,
                "the neck is one torso height above the hip");
    }

    @Test void theNeckTravelsFarEnoughToBeWorthFixing() {
        // The defect itself, in pixels: how far the neck anchor leaves a root-parented head behind.
        for (double[] lean : new double[][] {{4.950d, 1.0d}, {9.450d, 1.9d}}) {
            double radians = Math.toRadians(lean[0]);
            double forward = ArticulatedTorsoHierarchy.TORSO_HEIGHT * Math.sin(radians);
            assertTrue(forward > lean[1],
                    () -> "the neck moves only " + forward + " px at " + lean[0] + " degrees of lean");
        }
    }

    @Test void theSettingIsOffByDefaultAndDocumentsItsRestart() throws IOException {
        String yaml = Files.readString(SETTINGS);
        assertTrue(yaml.contains("torso-head-follow: false"),
                "articulated-player.yml must ship locomotion.torso-head-follow off, so F5 starts on today's baseline");
        int key = yaml.indexOf("torso-head-follow:");
        String comment = yaml.substring(Math.max(0, key - 700), key);
        assertTrue(comment.contains("client restart"), "the key must say it is only read at client start");
        assertTrue(comment.contains("torso-hip-pivot"), "the key must say it needs the hip-pivoted layout");
    }
}
