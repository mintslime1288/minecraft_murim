package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The combat layer is a deterministic function of attackTime, so everything except how it looks is
 * decidable here. What is not decidable here is in the F5 matrix of
 * CLAUDE_ARTICULATED_COMBAT_MOTION_V1.md: whether the swing reads as a person, whether the blade
 * clears the body, and where the item actually ends up on screen.
 */
class ArticulatedCombatPoseTest {
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");
    private static final Path RENDERER = Path.of("src/main/java/com/murimrpg/client/render/ModelPartArticulatedPlayerRenderer.java");
    private static final Path MODEL = Path.of("src/main/java/com/murimrpg/client/render/ArticulatedHumanoidModel.java");
    private static final int N = ArticulatedCombatPose.VALUE_COUNT;

    private static ArticulatedCombatSettings.Move barehand() { return ArticulatedCombatSettings.DISABLED.barehand(); }
    private static ArticulatedCombatSettings.Move sword() { return ArticulatedCombatSettings.DISABLED.sword(); }

    private static float radians(double degrees) { return (float) Math.toRadians(degrees); }

    private static float driveOf(ArticulatedCombatSettings.Move move, float t) {
        return ArticulatedCombatPose.drive(t, move.anticipation(), move.acceleration(),
                move.contact(), move.windUp(), move.settle());
    }

    private static float[] solved(ArticulatedCombatSettings.Move move, float t, boolean rightHanded) {
        float[] out = new float[N];
        ArticulatedCombatPose.solve(move, t, rightHanded, 1f, 0f, out);
        return out;
    }

    // ---------------------------------------------------------------- ends and finiteness

    @Test void bothEndsOfASwingAddExactlyNothing() {
        float[] out = new float[N];
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (float t : new float[] {-1f, 0f, 1f, 2f}) {
                java.util.Arrays.fill(out, 7f);
                assertFalse(ArticulatedCombatPose.solve(move, t, true, 1f, 0f, out),
                        () -> "attackTime " + t + " is not a live swing and must be refused");
                for (float value : out) assertEquals(7f, value, 0f, "a refusal must not write anything");
            }
            assertEquals(0f, driveOf(move, 0f), 0f, "the first frame of a swing must add nothing");
            assertEquals(0f, driveOf(move, 1f), 0f, "the last frame must be merged back into the gait");
        }
    }

    @Test void everyPhaseBoundaryAndEveryStepInBetweenIsFinite() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (float t : new float[] {0.0001f, move.anticipation(), move.acceleration(), move.contact(), 0.9999f}) {
                float[] out = solved(move, t, true);
                for (float value : out) assertTrue(Float.isFinite(value), "a phase boundary went non-finite");
            }
            for (int step = 1; step < 4000; step++) {
                float[] out = solved(move, step / 4000f, step % 2 == 0);
                for (float value : out) assertTrue(Float.isFinite(value));
            }
        }
    }

    @Test void aBrokenAttackTimeOrSettingIsRefusedRatherThanPropagated() {
        float[] out = new float[N];
        for (float broken : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(ArticulatedCombatPose.solve(barehand(), broken, true, 1f, 0f, out));
            assertFalse(ArticulatedCombatPose.solve(barehand(), .5f, true, broken, 0f, out));
            assertFalse(ArticulatedCombatPose.solve(barehand(), .5f, true, 1f, broken, out));
            assertEquals(0f, ArticulatedCombatPose.drive(broken, .2f, .4f, .6f, .3f, .1f), 0f);
        }
        assertFalse(ArticulatedCombatPose.solve(null, .5f, true, 1f, 0f, out));
        assertFalse(ArticulatedCombatPose.solve(barehand(), .5f, true, 1f, 0f, null));
        assertFalse(ArticulatedCombatPose.solve(barehand(), .5f, true, 1f, 0f, new float[2]));
    }

    // ---------------------------------------------------------------- continuity

    @Test void noPhaseBoundaryStepsInValueOrInSlope() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            float step = 1f / 20000f;
            float worstJump = 0f, worstBend = 0f;
            float previous = driveOf(move, step), previousSlope = 0f;
            for (int index = 2; index < 20000; index++) {
                float value = driveOf(move, index * step);
                float slope = (value - previous) / step;
                worstJump = Math.max(worstJump, Math.abs(value - previous));
                if (index > 2) worstBend = Math.max(worstBend, Math.abs(slope - previousSlope));
                previous = value;
                previousSlope = slope;
            }
            final float jump = worstJump, bend = worstBend;
            assertTrue(jump < 0.002f, () -> "the drive stepped by " + jump + " between two samples");
            // A smoothstep join has zero slope on both sides, so the slope itself is continuous.
            assertTrue(bend < 1.5f, () -> "the drive's slope stepped by " + bend + " at a join");
        }
    }

    @Test void theSwingWindsBackFirstAndReachesFullExtensionAtTheStrike() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            assertTrue(driveOf(move, move.anticipation()) < -0.2f, "the shoulder must gather back first");
            assertEquals(1f, driveOf(move, move.acceleration()), 1e-4f, "the strike must reach full extension");
            assertTrue(driveOf(move, move.contact()) > 0.5f, "the contact hold must stay out");
            float fastest = 0f;
            for (int index = 1; index < 2000; index++) {
                float a = driveOf(move, (index - 1) / 2000f), b = driveOf(move, index / 2000f);
                if (b - a > fastest) fastest = b - a;
            }
            assertTrue(fastest > 0f, "the strike must actually move");
        }
    }

    @Test void theTorsoLeadsTheShoulderAndTheShoulderLeadsTheForearm() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            float armLag = move.armLag();
            float foreLag = Math.min(armLag + move.elbowLag(), ArticulatedCombatPose.MAXIMUM_CHAIN_LAG);
            int torsoPeak = 0, armPeak = 0, forePeak = 0;
            float bestTorso = -2f, bestArm = -2f, bestFore = -2f;
            for (int index = 0; index <= 4000; index++) {
                float t = index / 4000f;
                float torso = driveOf(move, t);
                float arm = ArticulatedCombatPose.chainedDrive(t, armLag, move.anticipation(),
                        move.acceleration(), move.contact(), move.windUp(), move.settle());
                float fore = ArticulatedCombatPose.chainedDrive(t, foreLag, move.anticipation(),
                        move.acceleration(), move.contact(), move.windUp(), move.settle());
                if (torso > bestTorso) { bestTorso = torso; torsoPeak = index; }
                if (arm > bestArm) { bestArm = arm; armPeak = index; }
                if (fore > bestFore) { bestFore = fore; forePeak = index; }
            }
            assertTrue(armPeak > torsoPeak, "the shoulder must arrive after the weight has turned");
            assertTrue(forePeak > armPeak, "and the forearm after the shoulder");
        }
    }

    @Test void theChainWarpIsMonotoneAndClosesAtBothEndsOfTheSwing() {
        // A plain t - lag tap is not zero at t = 1, so the pose would step the instant the swing
        // clock crossed 1. The warp has to be exactly 0 at 0, exactly 1 at 1, never ahead of t,
        // and never run backwards, or the chain is worse than no chain at all.
        for (float lag : new float[] {0f, 0.01f, 0.05f, 0.12f, 0.24f, 9f}) {
            assertEquals(0f, ArticulatedCombatPose.chainTime(0f, lag), 0f);
            assertEquals(1f, ArticulatedCombatPose.chainTime(1f, lag), 1e-6f);
            float previous = -1f;
            for (int index = 0; index <= 5000; index++) {
                float t = index / 5000f;
                float warped = ArticulatedCombatPose.chainTime(t, lag);
                assertTrue(Float.isFinite(warped), "the warped clock went non-finite");
                assertTrue(warped <= t + 1e-6f, "a link in the chain cannot run ahead of the swing");
                assertTrue(warped >= previous - 1e-6f, "the warped clock must never run backwards");
                previous = warped;
            }
        }
        // Outside a swing it is left exactly alone, so drive() still sees the same refusal values.
        assertEquals(-1f, ArticulatedCombatPose.chainTime(-1f, 0.1f), 0f);
        assertEquals(2f, ArticulatedCombatPose.chainTime(2f, 0.1f), 0f);
    }

    // ---------------------------------------------------------------- signs and symmetry

    @Test void theRightAndLeftAttacksAreExactMirrors() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (int index = 1; index < 400; index++) {
                float t = index / 400f;
                float[] right = solved(move, t, true), left = solved(move, t, false);
                // The arm slots are expressed for the attacking arm, so they are identical; only
                // the axes that have a side - the body yaw and the arm roll - flip.
                assertEquals(right[ArticulatedCombatPose.ATTACK_UPPER_X], left[ArticulatedCombatPose.ATTACK_UPPER_X], 1e-6f);
                assertEquals(right[ArticulatedCombatPose.ATTACK_LOWER_X], left[ArticulatedCombatPose.ATTACK_LOWER_X], 1e-6f);
                assertEquals(right[ArticulatedCombatPose.OFF_UPPER_X], left[ArticulatedCombatPose.OFF_UPPER_X], 1e-6f);
                assertEquals(-right[ArticulatedCombatPose.BODY_Y], left[ArticulatedCombatPose.BODY_Y], 1e-6f,
                        "the torso must turn the other way for the other hand");
                assertEquals(-right[ArticulatedCombatPose.ATTACK_UPPER_Z], left[ArticulatedCombatPose.ATTACK_UPPER_Z], 1e-6f);
                assertEquals(-right[ArticulatedCombatPose.BODY_Z], left[ArticulatedCombatPose.BODY_Z], 1e-6f,
                        "and the weight must go onto the other side for the other hand");
                assertEquals(right[ArticulatedCombatPose.BODY_X], left[ArticulatedCombatPose.BODY_X], 1e-6f,
                        "the forward pitch has no side");
            }
        }
    }

    @Test void theAttackingShoulderComesForwardAndTheElbowOnlyFoldsTheHumanWay() {
        var move = barehand();
        float[] atStrike = solved(move, move.acceleration(), true);
        // -z is forward, so a strike is a negative upper-arm pitch, and a positive body yaw carries
        // the right shoulder backwards, so a right-handed strike needs a negative yaw.
        assertTrue(atStrike[ArticulatedCombatPose.ATTACK_UPPER_X] < 0f, "the fist must go forward, not back");
        assertTrue(atStrike[ArticulatedCombatPose.BODY_Y] < 0f, "the right shoulder must come through");
        float[] atGather = solved(move, move.anticipation(), true);
        assertTrue(atGather[ArticulatedCombatPose.ATTACK_UPPER_X] > 0f, "the gather must go back");
        assertTrue(atGather[ArticulatedCombatPose.BODY_Y] > 0f, "and the torso with it");
        // An elbow only folds forwards, which is negative, at every point of both moves and
        // whatever the gait already wrote.
        float tolerance = radians(ArticulatedCombatPose.ELBOW_BACKWARD_TOLERANCE_DEGREES) + 1e-6f;
        for (var each : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (float gaitElbow : new float[] {radians(-40), radians(-10), 0f, radians(5)}) {
                for (int index = 1; index < 500; index++) {
                    float[] out = new float[N];
                    assertTrue(ArticulatedCombatPose.solve(each, index / 500f, true, 1f, gaitElbow, out));
                    float composed = gaitElbow + out[ArticulatedCombatPose.ATTACK_LOWER_X];
                    assertTrue(composed <= tolerance,
                            () -> "the elbow bent backwards to " + Math.toDegrees(composed) + " degrees");
                }
            }
        }
    }

    @Test void everyRotationStaysUnderItsHardCeiling() {
        var wild = new ArticulatedCombatSettings.Move(.02f, .8f, .95f, 1f, .5f, .4f,
                999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f);
        for (int index = 1; index < 2000; index++) {
            float[] out = new float[N];
            ArticulatedCombatPose.solve(wild, index / 2000f, index % 2 == 0, 1f, radians(-30), out);
            assertTrue(Math.abs(out[ArticulatedCombatPose.BODY_X]) <= radians(ArticulatedCombatPose.MAXIMUM_BODY_PITCH_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.BODY_Y]) <= radians(ArticulatedCombatPose.MAXIMUM_BODY_YAW_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.BODY_Z]) <= radians(ArticulatedCombatPose.MAXIMUM_BODY_ROLL_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.ATTACK_UPPER_X]) <= radians(ArticulatedCombatPose.MAXIMUM_ARM_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.ATTACK_UPPER_Z]) <= radians(ArticulatedCombatPose.MAXIMUM_ARM_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.OFF_UPPER_X]) <= radians(ArticulatedCombatPose.MAXIMUM_ARM_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.ATTACK_LOWER_X]) <= radians(ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[ArticulatedCombatPose.OFF_LOWER_X]) <= radians(ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES) + 1e-5f);
        }
    }

    // ---------------------------------------------------------------- the two moves differ

    @Test void theFistAndTheBladeDoNotTraceTheSameShape() {
        float worst = 0f;
        for (int index = 1; index < 1000; index++) {
            float t = index / 1000f;
            float[] fist = solved(barehand(), t, true), blade = solved(sword(), t, true);
            for (int slot = 0; slot < N; slot++) worst = Math.max(worst, Math.abs(fist[slot] - blade[slot]));
        }
        final float measured = worst;
        assertTrue(measured > radians(6), () -> "the two moves differ by only " + Math.toDegrees(measured) + " degrees");
        // The blade travels a diagonal and the fist does not: that is the roll axis.
        assertEquals(0f, solved(barehand(), barehand().acceleration(), true)[ArticulatedCombatPose.ATTACK_UPPER_Z], 0f);
        assertTrue(Math.abs(solved(sword(), sword().acceleration(), true)[ArticulatedCombatPose.ATTACK_UPPER_Z]) > 0f);
        // And the blade gathers further back than the fist.
        assertTrue(sword().windUp() > barehand().windUp());
    }

    // ---------------------------------------------------------------- combos and the hand anchor

    @Test void aSecondAttackBeforeTheFirstEndsIsSeenAsARestart() {
        assertTrue(ArticulatedCombatPose.swingRestarted(.6f, .02f), "the swing clock going back is a restart");
        assertFalse(ArticulatedCombatPose.swingRestarted(.2f, .3f), "an ordinary advance is not");
        assertFalse(ArticulatedCombatPose.swingRestarted(0f, .1f), "the first frame of a swing is not");
        assertFalse(ArticulatedCombatPose.swingRestarted(Float.NaN, .1f));
        assertFalse(ArticulatedCombatPose.swingRestarted(.5f, .495f), "float noise is not a restart");
    }

    @Test void theHandDirectionPointsAtTheSplitHandAndTheShortfallIsBounded() {
        // A straight arm is exactly the arm's own angle and reaches the full ten pixels.
        for (float pitch : new float[] {radians(-60), 0f, radians(45)}) {
            assertEquals(pitch, ArticulatedCombatPose.handDirection(pitch, 0f), 1e-5f);
            assertEquals(0f, ArticulatedCombatPose.handShortfall(pitch, 0f), 1e-4f);
        }
        // A folded elbow always lands between the upper arm's own direction and the forearm's.
        for (int upper = -80; upper <= 80; upper += 5) {
            for (int elbow = -100; elbow <= 0; elbow += 5) {
                float u = radians(upper), l = radians(elbow);
                float direction = ArticulatedCombatPose.handDirection(u, l);
                assertTrue(Float.isFinite(direction));
                assertTrue(direction <= Math.max(u, u + l) + 1e-4f && direction >= Math.min(u, u + l) - 1e-4f,
                        "the hand cannot be outside the two segments it hangs from");
                float shortfall = ArticulatedCombatPose.handShortfall(u, l);
                assertTrue(shortfall >= -1e-4f && shortfall <= 10f + 1e-4f, "the shortfall left its range");
            }
        }
        assertEquals(0f, ArticulatedCombatPose.handShortfall(Float.NaN, 0f), 0f);
    }

    // ---------------------------------------------------------------- wiring and defaults

    @Test void theLegsAreNotInTheDeltaAtAll() throws IOException {
        assertEquals(8, N, "body pitch, yaw and roll, the attacking arm's three, the other arm's two - and no leg");
        String model = Files.readString(MODEL);
        int start = model.indexOf("private void applyCombatPose(");
        assertTrue(start > 0, "applyCombatPose must exist");
        String body = model.substring(start, model.indexOf("\n    private ", start + 40));
        for (String leg : new String[] {"rightUpperLeg", "leftUpperLeg", "rightLowerLeg", "leftLowerLeg"}) {
            assertFalse(body.contains(leg), () -> "the combat layer must never write " + leg);
        }
        assertFalse(body.contains("head."), "the world look is solved after this and must stay Mojang's mouse");
        assertFalse(body.contains(".x =") || body.contains(".y =") || body.contains(".z ="),
                "rotation only: no part offset may be written");
    }

    @Test void theAttackGateIsOpenedOnlyByTheBlockAndTheOtherFallbacksSurvive() throws IOException {
        String source = Files.readString(RENDERER);
        assertTrue(source.contains("(combat || state.attackTime <= .01f)"),
                "the attack gate must be conditional on the combat block, not simply deleted");
        for (String kept : new String[] {"!state.isCrouching", "!state.isFallFlying", "!state.isVisuallySwimming",
                "!state.isPassenger", "!state.isUsingItem", "state.deathTime == 0"}) {
            assertTrue(source.contains(kept), () -> "the existing fallback " + kept + " must survive");
        }
        assertTrue(source.contains("state.attackArm"), "the swinging hand must come from the render state");
        assertTrue(source.contains("isEmpty()"), "the weapon test must be whether the hand holds anything");
    }

    @Test void everyFlagShipsOffAndEveryShippedValueIsInsideItsRange() throws IOException {
        String yaml = Files.readString(SETTINGS);
        int start = yaml.indexOf("combat-motion:");
        assertTrue(start > 0, "the block must ship");
        String block = yaml.substring(start, yaml.indexOf("\ndiagnostics:", start));
        assertEquals(5, block.split("enabled: false", -1).length - 1,
                "the master switch, hand-anchor, barehand, barehand-stance and sword must all ship off");
        assertTrue(block.contains("log-once: false"), "the combat diagnostic must ship off too");
        assertFalse(block.contains("enabled: true"), "nothing in the block may ship on");
        assertTrue(block.contains("restart"), "the block must say it is read once at client start");
        // The shipped numbers must be the same ones the code falls back to, so the file and the
        // DISABLED constant cannot drift apart.
        for (String[] pair : new String[][] {
                {"barehand", "anticipation", String.valueOf(barehand().anticipation())},
                {"barehand", "upper-arm-degrees", String.valueOf(barehand().upperArmDegrees())},
                {"sword", "wind-up", String.valueOf(sword().windUp())},
                {"sword", "arm-roll-degrees", String.valueOf(sword().armRollDegrees())}}) {
            int at = block.indexOf("\n  " + pair[0] + ":");
            assertTrue(at > 0, () -> "missing " + pair[0]);
            int key = block.indexOf("\n    " + pair[1] + ":", at);
            assertTrue(key > 0, () -> "missing " + pair[0] + "." + pair[1]);
            int end = block.indexOf('\n', key + 1);
            String line = block.substring(key + 1, end < 0 ? block.length() : end);
            double shipped = Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
            assertEquals(Double.parseDouble(pair[2]), shipped, 1e-6d,
                    pair[0] + "." + pair[1] + " in the file disagrees with the code fallback");
        }
    }

    // ---------------------------------------------------------------- S2: the strike itself

    /** The six samples the S2 request names, on both moves and both hands. */
    @Test void theSixNamedSamplesAreFiniteAndInsideEveryCeiling() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (boolean right : new boolean[] {true, false}) {
                for (float t : new float[] {0.0001f, move.anticipation(), move.acceleration(),
                        move.contact(), 0.75f, 0.9999f}) {
                    float[] out = new float[N];
                    assertTrue(ArticulatedCombatPose.solve(move, t, right, 1f, 0f, out),
                            () -> "a live swing sample was refused at " + t);
                    for (float value : out) assertTrue(Float.isFinite(value), "a named sample went non-finite");
                    assertTrue(Math.abs(out[ArticulatedCombatPose.BODY_X])
                            <= radians(ArticulatedCombatPose.MAXIMUM_BODY_PITCH_DEGREES) + 1e-5f);
                    assertTrue(Math.abs(out[ArticulatedCombatPose.BODY_Y])
                            <= radians(ArticulatedCombatPose.MAXIMUM_BODY_YAW_DEGREES) + 1e-5f);
                    assertTrue(Math.abs(out[ArticulatedCombatPose.BODY_Z])
                            <= radians(ArticulatedCombatPose.MAXIMUM_BODY_ROLL_DEGREES) + 1e-5f);
                    assertTrue(Math.abs(out[ArticulatedCombatPose.ATTACK_UPPER_X])
                            <= radians(ArticulatedCombatPose.MAXIMUM_ARM_DEGREES) + 1e-5f);
                    assertTrue(Math.abs(out[ArticulatedCombatPose.ATTACK_LOWER_X])
                            <= radians(ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES) + 1e-5f);
                }
            }
        }
    }

    /**
     * The real render cadence, not an arbitrary sampling: a default six-tick swing at 60 frames per
     * second is three frames per tick, so one frame is 1/18 of the swing. Nothing may move more in
     * one of those than {@link ArticulatedCombatPose#MAXIMUM_FRAME_STEP_DEGREES}.
     */
    @Test void noJointMovesMoreThanTheSafeStepInOneRenderFrame() {
        float frame = 1f / 18f;
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            float[] previous = new float[N], current = new float[N];
            float worst = 0f;
            boolean started = false;
            for (float t = 0f; t <= 1f + 1e-4f; t += frame) {
                java.util.Arrays.fill(current, 0f);
                ArticulatedCombatPose.solve(move, Math.min(t, 1f), true, 1f, 0f, current);
                if (started) {
                    for (int slot = 0; slot < N; slot++) {
                        worst = Math.max(worst, Math.abs(current[slot] - previous[slot]));
                    }
                }
                System.arraycopy(current, 0, previous, 0, N);
                started = true;
            }
            final float measured = worst;
            assertTrue(measured <= radians(ArticulatedCombatPose.MAXIMUM_FRAME_STEP_DEGREES),
                    () -> "a joint stepped " + Math.toDegrees(measured) + " degrees in one frame");
        }
        // And the phase boundaries themselves are not where it happens: the value on either side of
        // each join differs by less than a thousandth of a degree.
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (float join : new float[] {move.anticipation(), move.acceleration(), move.contact()}) {
                float[] before = new float[N], after = new float[N];
                ArticulatedCombatPose.solve(move, join - 1e-5f, true, 1f, 0f, before);
                ArticulatedCombatPose.solve(move, join + 1e-5f, true, 1f, 0f, after);
                for (int slot = 0; slot < N; slot++) {
                    // Two samples a hundred-thousandth of a swing apart: the fastest slope on the
                    // curve covers under a hundredth of a degree in that, so anything above a
                    // twentieth of a degree here is a discontinuity rather than motion.
                    assertTrue(Math.abs(after[slot] - before[slot]) < radians(0.05f),
                            "a phase join stepped");
                }
            }
        }
    }

    @Test void theElbowKeepsABendAtContactAndNeverLocksOrInverts() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            for (int index = 1; index < 2000; index++) {
                float[] out = solved(move, index / 2000f, true);
                assertTrue(out[ArticulatedCombatPose.ATTACK_LOWER_X] <= 1e-6f,
                        "the elbow may only ever fold forwards");
            }
            // Through the whole strike window - out, contact and the first of the recovery - the
            // arm keeps a real bend. A straight arm at contact is the locked look the request bans.
            for (float t = move.acceleration(); t <= move.contact() + 1e-4f; t += 0.005f) {
                float[] out = solved(move, t, true);
                final float bend = -out[ArticulatedCombatPose.ATTACK_LOWER_X];
                assertTrue(bend >= radians(8f),
                        () -> "the elbow locked to " + Math.toDegrees(bend) + " degrees of bend");
            }
            // And the deepest gather is deeper than the contact bend, which is what makes it a
            // gather rather than a slow straightening.
            float deepest = 0f;
            for (int index = 1; index < 2000; index++) {
                deepest = Math.min(deepest, solved(move, index / 2000f, true)[ArticulatedCombatPose.ATTACK_LOWER_X]);
            }
            assertTrue(-deepest > -solved(move, move.contact(), true)[ArticulatedCombatPose.ATTACK_LOWER_X] + radians(10f),
                    "the fist must gather at the chest before it goes out");
        }
    }

    @Test void theWeightTurnsBeforeTheFistAndTheShoulderRollsOntoTheStrike() {
        var move = barehand();
        // At the deepest gather the torso has already begun to come through while the arm is still
        // behind: that is the sequencing the request asks for, stated as a measurable ordering.
        float[] atStrike = solved(move, move.acceleration(), true);
        assertTrue(atStrike[ArticulatedCombatPose.BODY_Z] > 0f,
                "the right-handed strike must roll the striking shoulder up");
        assertTrue(atStrike[ArticulatedCombatPose.BODY_X] > 0f, "and lean very slightly into it");
        // The roll is only ever on the way out; the gather must not rock the body the other way.
        for (int index = 1; index < 2000; index++) {
            float t = index / 2000f;
            assertTrue(solved(move, t, true)[ArticulatedCombatPose.BODY_Z] >= -1e-6f,
                    "the weight transfer must not reverse during the gather");
        }
    }

    // ---------------------------------------------------------------- S2: the handover

    @Test void theStrikesOwnershipRisesHoldsAndIsBackToZeroBeforeTheSwingEnds() {
        for (var move : new ArticulatedCombatSettings.Move[] {barehand(), sword()}) {
            float a = move.acceleration(), c = move.contact();
            assertEquals(0f, ArticulatedCombatPose.ownership(0f, a, c), 0f);
            assertEquals(0f, ArticulatedCombatPose.ownership(1f, a, c), 0f);
            assertEquals(0f, ArticulatedCombatPose.ownership(Float.NaN, a, c), 0f);
            assertEquals(1f, ArticulatedCombatPose.ownership((a + c) / 2f, a, c), 1e-6f);
            float previous = 0f;
            for (int index = 0; index <= 4000; index++) {
                float value = ArticulatedCombatPose.ownership(index / 4000f, a, c);
                assertTrue(value >= 0f && value <= 1f, "ownership left 0..1");
                assertTrue(Math.abs(value - previous) < 0.01f, "ownership stepped");
                previous = value;
            }
        }
    }

    /**
     * The whole point of S2: the punch is not folded into the guard while it is a punch, and the
     * recovery arrives at the guard without passing through the neutral pose. Both layers are
     * composed here exactly as {@code ArticulatedHumanoidModel} composes them, on the real render
     * cadence, with the guard already fully up from a previous swing.
     */
    @Test void theStrikeHandsOverToTheGuardWithoutAPopOrANeutralFrame() {
        var move = barehand();
        var block = BarehandCombatStance.DISABLED;
        float frame = 1f / 18f;
        float[] strike = new float[N];
        float[] guard = new float[BarehandCombatStance.VALUE_COUNT];
        float previousRearUpper = Float.NaN, previousRearLower = Float.NaN;
        float previousFrontUpper = Float.NaN, previousBodyYaw = Float.NaN;
        float worst = 0f, extended = 0f;
        // One extra frame past the end of the swing, which is the frame the guard has to be whole.
        for (float t = 0f; t <= 1f + frame; t += frame) {
            float clock = t > 1f ? 0f : t;
            java.util.Arrays.fill(strike, 0f);
            boolean live = ArticulatedCombatPose.solve(move, clock, true, 1f, 0f, strike);
            float own = live ? ArticulatedCombatPose.guardHandover(move, clock) : 0f;
            if (live) ArticulatedCombatPose.yieldOffArm(strike, 1f, move.offArmGuardYield());
            assertTrue(BarehandCombatStance.pose(block, 1f, 1f, true, 0f, guard));
            BarehandCombatStance.yieldToStrike(guard, own, BarehandCombatStance.STRIKE_BODY_SHARE,
                    BarehandCombatStance.STRIKE_FRONT_SHARE, 1f);
            float rearUpper = strike[ArticulatedCombatPose.ATTACK_UPPER_X]
                    + guard[BarehandCombatStance.REAR_UPPER_X];
            float rearLower = strike[ArticulatedCombatPose.ATTACK_LOWER_X]
                    + guard[BarehandCombatStance.REAR_LOWER_X];
            float frontUpper = strike[ArticulatedCombatPose.OFF_UPPER_X]
                    + guard[BarehandCombatStance.FRONT_UPPER_X];
            float bodyYaw = strike[ArticulatedCombatPose.BODY_Y] + guard[BarehandCombatStance.BODY_Y];
            if (!Float.isNaN(previousRearUpper)) {
                worst = Math.max(worst, Math.abs(rearUpper - previousRearUpper));
                worst = Math.max(worst, Math.abs(rearLower - previousRearLower));
                worst = Math.max(worst, Math.abs(frontUpper - previousFrontUpper));
                worst = Math.max(worst, Math.abs(bodyYaw - previousBodyYaw));
            }
            previousRearUpper = rearUpper;
            previousRearLower = rearLower;
            previousFrontUpper = frontUpper;
            previousBodyYaw = bodyYaw;
            // While the punch is out, the punching arm must actually be out - not folded to the
            // guard's elbow. This is the regression the whole handover exists to prevent.
            if (clock >= move.acceleration() && clock <= move.contact()) {
                extended = Math.min(extended, rearUpper);
                assertTrue(rearLower > radians(-40),
                        "the punching elbow is still folded into the guard");
            }
        }
        final float measured = worst;
        assertTrue(measured <= radians(ArticulatedCombatPose.MAXIMUM_FRAME_STEP_DEGREES),
                () -> "the handover stepped " + Math.toDegrees(measured) + " degrees in one frame");
        assertTrue(extended < radians(-30), "the punch never reached out at all");
        // The last frame of the swing and the first frame after it are the same guard pose.
        java.util.Arrays.fill(strike, 0f);
        assertFalse(ArticulatedCombatPose.solve(move, 1f, true, 1f, 0f, strike));
        assertEquals(0f, ArticulatedCombatPose.guardHandover(move, 0.9999f), 0.02f,
                "the guard must be back before the swing clock reaches 1");
    }

    @Test void theOffArmKeepsItsGuardInsteadOfSwingingAwayFromTheFace() {
        var move = barehand();
        float[] withGuard = new float[N], without = new float[N];
        float peak = 0f;
        for (int index = 1; index < 1000; index++) {
            float t = index / 1000f;
            ArticulatedCombatPose.solve(move, t, true, 1f, 0f, withGuard);
            ArticulatedCombatPose.solve(move, t, true, 1f, 0f, without);
            ArticulatedCombatPose.yieldOffArm(withGuard, 1f, move.offArmGuardYield());
            peak = Math.max(peak, Math.abs(without[ArticulatedCombatPose.OFF_UPPER_X]));
            assertTrue(Math.abs(withGuard[ArticulatedCombatPose.OFF_UPPER_X])
                            <= Math.abs(without[ArticulatedCombatPose.OFF_UPPER_X]) + 1e-6f,
                    "the guard must never make the counter-swing bigger");
        }
        assertTrue(peak > 0f, "the counter-swing must exist when there is no guard to keep");
        // With no guard at all the counter-swing is untouched, so the guard-off behaviour is
        // bit-identical to what shipped before.
        float[] untouched = new float[N];
        ArticulatedCombatPose.solve(move, move.acceleration(), true, 1f, 0f, untouched);
        float before = untouched[ArticulatedCombatPose.OFF_UPPER_X];
        ArticulatedCombatPose.yieldOffArm(untouched, 0f, move.offArmGuardYield());
        assertEquals(before, untouched[ArticulatedCombatPose.OFF_UPPER_X], 0f);
        ArticulatedCombatPose.yieldOffArm(untouched, 1f, 0f);
        assertEquals(before, untouched[ArticulatedCombatPose.OFF_UPPER_X], 0f);
    }

    // ---------------------------------------------------------------- S2: combos and wiring

    @Test void aComboRestartNeitherSnapsNorProducesANonFiniteFrame() {
        var move = barehand();
        float[] delta = new float[N], held = new float[N];
        float restart = 0f, previous = 0f;
        float blend = 2f;
        float worst = 0f;
        float[] last = null;
        // Two swings, the second pressed a third of the way through the first, at the real cadence.
        for (int frame = 0; frame < 40; frame++) {
            float clock = frame < 6 ? frame / 18f : (frame - 6) / 18f;
            if (clock > 1f) clock = 0f;
            boolean restarted = ArticulatedCombatPose.swingRestarted(previous, clock);
            previous = clock;
            restart = Math.clamp(restart - (1f / 3f) / blend, 0f, 1f);
            java.util.Arrays.fill(delta, 0f);
            boolean live = ArticulatedCombatPose.solve(move, clock, true, 1f, 0f, delta);
            if (!live) java.util.Arrays.fill(delta, 0f);
            if (restarted) restart = 1f;
            for (int slot = 0; slot < N; slot++) delta[slot] += (held[slot] - delta[slot]) * restart;
            if (restart <= 0f) System.arraycopy(delta, 0, held, 0, N);
            for (float value : delta) assertTrue(Float.isFinite(value), "a combo frame went non-finite");
            if (last != null) {
                for (int slot = 0; slot < N; slot++) worst = Math.max(worst, Math.abs(delta[slot] - last[slot]));
            }
            last = delta.clone();
        }
        final float measured = worst;
        assertTrue(measured <= radians(ArticulatedCombatPose.MAXIMUM_FRAME_STEP_DEGREES),
                () -> "a combo restart snapped by " + Math.toDegrees(measured) + " degrees");
    }

    @Test void theModelReleasesTheHandoverOnTheSameClockAsThePoseItBelongsTo() throws IOException {
        String model = Files.readString(MODEL);
        int start = model.indexOf("private void applyCombatPose(");
        assertTrue(start > 0);
        String body = model.substring(start, model.indexOf("\n    /**", start + 40));
        assertTrue(body.contains("ownership += (heldOwnership - ownership) * restartWeight;"),
                "the handover weight must be released on the restart clock, like the pose");
        assertTrue(body.contains("strikeOwnership = Math.clamp(ownership, 0f, 1f);"),
                "and it must be clamped before the guard layer reads it");
        assertTrue(body.contains("stance.easedWeight()"),
                "the off arm must know how much guard is up before it counter-swings");
    }

    @Test void onlyAnEmptyHandGetsTheBarehandStrike() throws IOException {
        String model = Files.readString(MODEL);
        assertTrue(model.contains("var move = armed ? combat.sword() : combat.barehand();"),
                "an armed hand must not be given the barehand move");
        assertTrue(model.contains("boolean moveEnabled = armed ? combat.swordEnabled() : combat.barehandEnabled();"),
                "and each move must keep its own switch");
        assertTrue(model.contains("boolean barehand = !attackArmed;"),
                "the guard must stay bare-handed only");
        String yaml = Files.readString(SETTINGS);
        int at = yaml.indexOf("\n  sword:");
        assertTrue(at > 0);
        assertTrue(yaml.indexOf("enabled: false", at) < yaml.indexOf("anticipation:", at),
                "the sword move must still ship off");
    }

    @Test void theStrikeStaysOutOfTheLowerBodyAndTheEntitySwingEdgeSurvives() throws IOException {
        String pose = Files.readString(Path.of("src/main/java/com/murimrpg/client/render/ArticulatedCombatPose.java"));
        int solve = pose.indexOf("static boolean solve(");
        assertTrue(solve > 0);
        String body = pose.substring(solve, pose.indexOf("\n    static float limited(", solve));
        for (String banned : new String[] {"Leg", "Knee", "scale(", "pivot", "translate"}) {
            assertFalse(body.contains(banned), () -> "the strike must never touch " + banned);
        }
        String model = Files.readString(MODEL);
        assertTrue(model.contains("stance.advance(ageInTicks, attackTime, attackSwingActive"),
                "the entity swing edge added by the trigger fix must survive");
        assertTrue(model.contains("void setAttack(float attackTime, boolean rightHanded, boolean armed, boolean swingActive)"),
                "and it must still be pushed in from the render state");
    }

    @Test void aPhaseOrderMistakeDisablesTheLayerInsteadOfKillingTheClient() {
        var disabled = ArticulatedCombatSettings.DISABLED;
        assertFalse(disabled.enabled());
        assertFalse(disabled.barehandEnabled());
        assertFalse(disabled.swordEnabled());
        assertFalse(disabled.handAnchorEnabled());
        // And the fallback move itself is still a valid, ordered, finite shape.
        for (var move : new ArticulatedCombatSettings.Move[] {disabled.barehand(), disabled.sword()}) {
            assertTrue(move.anticipation() < move.acceleration() && move.acceleration() < move.contact(),
                    "the fallback phases must be ordered");
            for (int index = 1; index < 200; index++) {
                for (float value : solved(move, index / 200f, true)) assertTrue(Float.isFinite(value));
            }
        }
    }
}
