package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The vanilla-silhouette layer is pure arithmetic on nine rotations, so everything except how it
 * looks can be settled here. What cannot be settled here is in the F5 matrix in
 * CLAUDE_VANILLA_SILHOUETTE_LOCOMOTION_V1.md: whether the stance reads as a person, whether the
 * run reads as a run, and whether the shoulder seam offset is visible at all.
 */
class VanillaSilhouettePoseTest {
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");
    private static final Path RENDERER = Path.of("src/main/java/com/murimrpg/client/render/VanillaSilhouetteRenderer.java");
    private static final int N = VanillaSilhouetteDriver.VALUE_COUNT;

    /** Everything on, with the shipped numbers, so the tests exercise the real defaults. */
    private static VanillaSilhouetteSettings all(boolean gait, boolean idle, boolean jump) {
        return new VanillaSilhouetteSettings(true, 4f, gait, 1f, 1f, 45f, 3f, 10f, 3f, .25f, 1f,
                idle, .12f, 1.5f, 2f, 3f, .35f, 4f, jump, .55f, 22f, 8f, 14f, 3f);
    }

    private static float[] vanillaSample() {
        return new float[] {0.05f, 0.01f, -0.02f, 0.30f, -0.30f, 0.04f, -0.04f, -0.25f, 0.25f};
    }

    private static float radians(double degrees) { return (float) Math.toRadians(degrees); }

    // ---------------------------------------------------------------- identity

    @Test void aZeroWeightIsBitIdenticalToWhateverMojangWrote() {
        float[] vanilla = vanillaSample(), out = new float[N];
        VanillaSilhouettePose.assemble(all(true, true, true), vanilla, out, 0f, 1f, 1f, 40f,
                false, 1f, .2f, 0f, .5f, -.5f, -.4f, .4f, 0f, 0f);
        for (int slot = 0; slot < N; slot++) {
            assertEquals(vanilla[slot], out[slot], 0f, "slot " + slot + " must be untouched at weight 0");
        }
    }

    @Test void theBlockBeingOffIsBitIdenticalEvenAtFullWeight() {
        var off = new VanillaSilhouetteSettings(false, 4f, true, 1f, 1f, 45f, 3f, 10f, 3f, .25f, 1f,
                true, .12f, 1.5f, 2f, 3f, .35f, 4f, true, .55f, 22f, 8f, 14f, 3f);
        float[] vanilla = vanillaSample(), out = new float[N];
        VanillaSilhouettePose.assemble(off, vanilla, out, 1f, 1f, 1f, 40f, false, 1f, .2f, 0f,
                .5f, -.5f, -.4f, .4f, 0f, 0f);
        for (int slot = 0; slot < N; slot++) assertEquals(vanilla[slot], out[slot], 0f, "slot " + slot);
    }

    @Test void everySubFeatureOffLeavesTheVanillaPoseAlone() {
        float[] vanilla = vanillaSample(), out = new float[N];
        VanillaSilhouettePose.assemble(all(false, false, false), vanilla, out, 1f, 1f, 1f, 40f,
                false, 1f, .2f, 0f, .5f, -.5f, -.4f, .4f, 0f, 0f);
        for (int slot = 0; slot < N; slot++) assertEquals(vanilla[slot], out[slot], 1e-6f, "slot " + slot);
    }

    // ---------------------------------------------------------------- fades and bounds

    @Test void theFadeIsBoundedAndAlwaysArrives() {
        float current = 0f;
        for (int tick = 0; tick < 4; tick++) {
            float next = VanillaSilhouettePose.approach(current, 1f, .25f);
            assertTrue(next - current <= .25f + 1e-6f, "the fade moved more than one step");
            current = next;
        }
        assertEquals(1f, current, 1e-6f, "the fade must reach the target");
        assertEquals(0f, VanillaSilhouettePose.approach(1f, 0f, 5f), 0f, "an oversized step lands exactly");
        assertEquals(.5f, VanillaSilhouettePose.approach(Float.NaN, .5f, .1f), 0f, "a broken current takes the target");
    }

    @Test void noRotationEverPassesItsHardCeiling() {
        float[] vanilla = new float[N], out = new float[N];
        var settings = new VanillaSilhouetteSettings(true, 4f, true, 1.6f, 1.6f, 62f, 18f, 18f, 6f, 1f, 1f,
                true, .5f, 6f, 6f, 10f, 1.5f, 2f, true, 1f, 60f, 18f, 60f, 4f);
        for (int step = 0; step <= 200; step++) {
            float t = step / 200f;
            VanillaSilhouettePose.assemble(settings, vanilla, out, 1f, t, t, step * 3f,
                    step % 2 == 0, t, 1.5f - 3f * t, 1.5f - 3f * t,
                    radians(80 * (t - .5f)), radians(-80 * (t - .5f)),
                    radians(-70 * (t - .5f)), radians(70 * (t - .5f)), radians(35), radians(-35));
            for (int slot = 0; slot < N; slot++) assertTrue(Float.isFinite(out[slot]), "slot " + slot + " went non-finite");
            assertTrue(Math.abs(out[VanillaSilhouetteDriver.BODY_X]) <= radians(VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[VanillaSilhouetteDriver.BODY_Y]) <= radians(VanillaSilhouettePose.MAXIMUM_YAW_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[VanillaSilhouetteDriver.BODY_Z]) <= radians(VanillaSilhouettePose.MAXIMUM_ROLL_DEGREES) + 1e-5f);
            for (int slot : new int[] {VanillaSilhouetteDriver.RIGHT_ARM_X, VanillaSilhouetteDriver.LEFT_ARM_X,
                    VanillaSilhouetteDriver.RIGHT_LEG_X, VanillaSilhouetteDriver.LEFT_LEG_X}) {
                assertTrue(Math.abs(out[slot]) <= radians(VanillaSilhouettePose.MAXIMUM_LIMB_DEGREES) + 1e-5f,
                        "limb slot " + slot + " passed the hard ceiling");
            }
        }
    }

    @Test void aBrokenInputIsRefusedRatherThanPropagated() {
        float[] out = new float[N];
        float[] broken = vanillaSample();
        broken[VanillaSilhouetteDriver.BODY_X] = Float.NaN;
        VanillaSilhouettePose.assemble(all(true, true, true), broken, out, 1f, .5f, .5f, 10f,
                false, 1f, .1f, 0f, Float.NaN, Float.POSITIVE_INFINITY, .1f, -.1f, 0f, 0f);
        for (int slot = 0; slot < N; slot++) assertTrue(Float.isFinite(out[slot]), "slot " + slot + " went non-finite");
        VanillaSilhouettePose.assemble(all(true, true, true), null, out, 1f, 1f, 1f, 1f, false, 1f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        VanillaSilhouettePose.assemble(all(true, true, true), new float[2], out, 1f, 1f, 1f, 1f, false, 1f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        for (int slot = 0; slot < N; slot++) assertTrue(Float.isFinite(out[slot]), "a short array must be refused");
    }

    // ---------------------------------------------------------------- gait

    @Test void aRunLeansFurtherForwardThanAWalkAndAStandstillNotAtAll() {
        assertEquals(0f, VanillaSilhouettePose.lean(3f, 10f, 0f, 0f), 0f, "a standing player must not lean");
        float walk = VanillaSilhouettePose.lean(3f, 10f, 0f, 1f);
        float run = VanillaSilhouettePose.lean(3f, 10f, 1f, 1f);
        assertTrue(run > walk + radians(5), () -> "a run must read as a run: walk " + Math.toDegrees(walk)
                + " vs run " + Math.toDegrees(run));
        for (int step = 0; step <= 20; step++) {
            float previous = VanillaSilhouettePose.lean(3f, 10f, step / 20f, 1f);
            float next = VanillaSilhouettePose.lean(3f, 10f, Math.min(1f, (step + 1) / 20f), 1f);
            assertTrue(next >= previous - 1e-6f, "the walk to run lean must be monotone, so the transition cannot snap");
        }
    }

    @Test void theStrideCeilingHoldsAndTheScaleOnlyChangesLength() {
        float authored = radians(40);
        assertTrue(VanillaSilhouettePose.strideAngle(authored, 1.6f, 45f) <= radians(45) + 1e-6f);
        assertTrue(VanillaSilhouettePose.strideAngle(authored, 9f, 45f) <= radians(45) + 1e-6f, "an out-of-range scale is clamped");
        assertTrue(VanillaSilhouettePose.strideAngle(authored, 1.2f, 62f) > VanillaSilhouettePose.strideAngle(authored, 1f, 62f));
        assertEquals(0f, VanillaSilhouettePose.strideAngle(Float.NaN, 1f, 45f), 0f);
        // The sign is the clip's, so left and right stay in the phase the clip authored.
        assertTrue(VanillaSilhouettePose.strideAngle(-authored, 1f, 62f) < 0f);
    }

    @Test void theShoulderFollowsTheArmThatIsSwingingForward() {
        // A hanging limb's lower end goes to +z for a positive xRot, so the more negative pitch is
        // the forward arm; a positive body yaw carries the right shoulder backwards. Right arm
        // forward must therefore give a negative yaw.
        float rightForward = VanillaSilhouettePose.shoulderYaw(radians(-30), radians(30), .25f, 3f);
        float leftForward = VanillaSilhouettePose.shoulderYaw(radians(30), radians(-30), .25f, 3f);
        assertTrue(rightForward < 0f, "the right shoulder must come forward with the right arm");
        assertTrue(leftForward > 0f, "and the left with the left");
        assertEquals(-rightForward, leftForward, 1e-6f, "the two sides must mirror exactly");
        for (int degrees = -180; degrees <= 180; degrees += 3) {
            float yaw = VanillaSilhouettePose.shoulderYaw(radians(degrees), radians(-degrees), 1f, 3f);
            assertTrue(Math.abs(yaw) <= radians(3) + 1e-6f, "the yaw passed its ceiling");
        }
        assertEquals(0f, VanillaSilhouettePose.shoulderYaw(radians(-30), radians(30), .25f, 0f), 0f,
                "0 degrees must be off");
    }

    @Test void theShoulderYawMovesNothingSidewaysOrUpAndDown() {
        // body's pivot is the model origin and head is a sibling, not a child, so a body yaw cannot
        // move the head at all. What it does move is the torso's own side face, and that is the
        // only cost: 4*sin(yaw) px of seam offset against arms that stay exactly where Mojang put
        // them. At the hard ceiling that is well under a third of a pixel.
        double worst = 4d * Math.sin(Math.toRadians(VanillaSilhouettePose.MAXIMUM_YAW_DEGREES));
        assertTrue(worst < 0.42d, () -> "the shoulder seam would offset " + worst + " px");
        // And no slot in this layer is a translation or a scale.
        assertEquals(9, VanillaSilhouetteDriver.VALUE_COUNT, "the layer writes nine rotations and nothing else");
    }

    // ---------------------------------------------------------------- idle

    @Test void theStanceFadesOutAsSoonAsTheGaitStarts() {
        assertEquals(1f, VanillaSilhouettePose.idleWeight(0f, .12f), 1e-6f);
        assertEquals(0f, VanillaSilhouettePose.idleWeight(.12f, .12f), 1e-6f);
        float previous = 1f;
        for (int step = 0; step <= 40; step++) {
            float weight = VanillaSilhouettePose.idleWeight(.12f * step / 40f, .12f);
            assertTrue(weight <= previous + 1e-6f, "the stance must fade out monotonically");
            previous = weight;
        }
        assertEquals(0f, VanillaSilhouettePose.idleWeight(Float.NaN, .12f), 0f);
    }

    @Test void breathingIsABoundedRotationWithNoBounce() {
        float worst = 0f;
        for (int tick = 0; tick <= 2000; tick++) {
            float value = VanillaSilhouettePose.breath(tick * 0.5f, 4f, .35f);
            worst = Math.max(worst, Math.abs(value));
            assertTrue(Float.isFinite(value));
        }
        assertTrue(worst <= radians(.35) + 1e-6f, "breathing passed its amplitude");
        assertEquals(VanillaSilhouettePose.breath(13f, 4f, .35f),
                VanillaSilhouettePose.breath(13f + 80f, 4f, .35f), 1e-5f, "one period must repeat");
        assertEquals(0f, VanillaSilhouettePose.breath(10f, 4f, 0f), 0f, "0 degrees must be silent");
        // The only thing breathing touches is body.xRot; there is no y slot for it to bob with.
        float[] vanilla = new float[N], out = new float[N];
        VanillaSilhouettePose.assemble(all(false, true, false), vanilla, out, 1f, 0f, 0f, 37f,
                false, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        assertTrue(Math.abs(out[VanillaSilhouetteDriver.BODY_X]) > 0f, "the stance must actually do something");
        assertEquals(0f, out[VanillaSilhouetteDriver.BODY_Z], 1e-6f, "the stance must not roll");
    }

    // ---------------------------------------------------------------- jump

    @Test void aJumpKeepsTheStrideItLeftTheGroundWith() {
        float takeOffRight = radians(30), takeOffLeft = radians(-30);
        float[] vanilla = new float[N], out = new float[N];
        float previousSplit = 0f;
        for (int step = 0; step <= 20; step++) {
            float progress = step / 20f;
            VanillaSilhouettePose.assemble(all(true, true, true), vanilla, out, 1f, 1f, 1f, step * 2f,
                    true, progress, .28f, 0f,
                    // the clip meanwhile scissors the other way, which is what used to show
                    radians(-35), radians(35), radians(25), radians(-25), takeOffRight, takeOffLeft);
            float split = out[VanillaSilhouetteDriver.RIGHT_LEG_X] - out[VanillaSilhouetteDriver.LEFT_LEG_X];
            assertTrue(split > 0f, () -> "the front and back leg swapped sides in mid air at progress " + progress);
            if (step > 0) assertTrue(Math.abs(split - previousSplit) < radians(12),
                    "the airborne legs must not jump from frame to frame");
            previousSplit = split;
        }
    }

    @Test void aHoldFractionOfZeroIsTheBehaviourThatShipsToday() {
        var noHold = new VanillaSilhouetteSettings(true, 4f, true, 1f, 1f, 45f, 3f, 10f, 3f, .25f, 1f,
                false, .12f, 1.5f, 2f, 3f, .35f, 4f, true, 0f, 0f, 8f, 0f, 3f);
        float[] vanilla = new float[N], grounded = new float[N], airborne = new float[N];
        VanillaSilhouettePose.assemble(noHold, vanilla, grounded, 1f, 1f, 1f, 10f, false, 1f, 0f, 0f,
                radians(-35), radians(35), radians(25), radians(-25), radians(30), radians(-30));
        VanillaSilhouettePose.assemble(noHold, vanilla, airborne, 1f, 1f, 1f, 10f, true, 1f, 0f, 0f,
                radians(-35), radians(35), radians(25), radians(-25), radians(30), radians(-30));
        assertEquals(grounded[VanillaSilhouetteDriver.RIGHT_LEG_X], airborne[VanillaSilhouetteDriver.RIGHT_LEG_X], 1e-6f,
                "hold 0 with no travel lean must leave the airborne pose exactly as it is today");
        assertEquals(grounded[VanillaSilhouetteDriver.BODY_X], airborne[VanillaSilhouetteDriver.BODY_X], 1e-6f);
    }

    @Test void travellingLeansIntoTheDirectionOfTravelAndStandingStillDoesNot() {
        assertEquals(0f, VanillaSilhouettePose.travelLean(0f, 22f, 8f), 0f);
        assertTrue(VanillaSilhouettePose.travelLean(.28f, 22f, 8f) > 0f, "forward travel must lean forward");
        assertTrue(VanillaSilhouettePose.travelLean(-.28f, 22f, 8f) < 0f, "reverse travel must lean the other way");
        assertTrue(Math.abs(VanillaSilhouettePose.travelLean(9f, 60f, 8f)) <= radians(8) + 1e-6f);
        assertEquals(0f, VanillaSilhouettePose.travelLean(Float.NaN, 22f, 8f), 0f);
    }

    // ---------------------------------------------------------------- cadence and wiring

    @Test void cadenceIsMojangsOwnMultiplierByDefault() {
        assertEquals(VanillaSilhouettePose.VANILLA_LIMB_SWING_SCALE, VanillaSilhouettePose.cadence(1f), 0f,
                "scale 1.0 must be exactly the multiplier HumanoidModel uses");
        assertTrue(VanillaSilhouettePose.cadence(.5f) < VanillaSilhouettePose.cadence(1f));
        assertTrue(VanillaSilhouettePose.cadence(9f) <= VanillaSilhouettePose.VANILLA_LIMB_SWING_SCALE * 1.5f + 1e-6f);
        assertEquals(VanillaSilhouettePose.VANILLA_LIMB_SWING_SCALE, VanillaSilhouettePose.cadence(Float.NaN), 0f);
    }

    @Test void theHeadIsNeverWrittenByThisLayer() throws IOException {
        String source = Files.readString(RENDERER);
        assertFalse(source.contains("head."), "the world look is Mojang's; this layer must not touch the head");
        assertFalse(source.contains(".x =") || source.contains(".y =") || source.contains(".z ="),
                "no part offset may be written: this layer is rotation only");
        assertFalse(source.contains("Scale ="), "no part scale may be written");
    }

    @Test void theBlockShipsOffAndSaysSo() throws IOException {
        String yaml = Files.readString(SETTINGS);
        int block = yaml.indexOf("vanilla-silhouette:");
        assertTrue(block > 0, "articulated-player.yml must carry the block");
        String body = yaml.substring(block, yaml.indexOf("\ncombat-motion:", block));
        assertTrue(body.contains("\n  enabled: false"), "the master switch must ship off");
        assertEquals(3, body.split("\n    enabled: false", -1).length - 1,
                "gait, idle and jump must each ship off so they can be compared one at a time");
        assertTrue(body.contains("restart"), "the block must say it is only read at client start");
        assertTrue(body.contains("cannot bend an elbow"), "the block must state the cost of the mode");
    }
}
