package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The guard is a tick clock and a set of fixed angles, so everything except how it looks is
 * decidable here. What is not: whether it reads as a fighter, whether the hands sit where the eye
 * wants them, and whether mining bare-handed raising the guard is wanted. Those are in the F5
 * matrix of CLAUDE_BAREHAND_COMBAT_STANCE_V1.md.
 */
class BarehandCombatStanceTest {
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");
    private static final Path MODEL = Path.of("src/main/java/com/murimrpg/client/render/ArticulatedHumanoidModel.java");
    private static final int N = BarehandCombatStance.VALUE_COUNT;

    private static BarehandCombatStance.Settings on() {
        var d = BarehandCombatStance.DISABLED;
        return new BarehandCombatStance.Settings(true, d.holdTicks(), d.blendInTicks(), d.blendOutTicks(),
                d.walkWeight(), d.sprintWeight(), d.airborneWeight(), d.bodyPitchDegrees(), d.bodyYawDegrees(),
                d.frontUpperArmDegrees(), d.frontElbowDegrees(), d.rearUpperArmDegrees(), d.rearElbowDegrees(),
                d.armTuckDegrees(), d.legReadyDegrees(), d.kneeReadyDegrees(), d.breathDegrees(), d.breathSeconds());
    }

    private static float radians(double degrees) { return (float) Math.toRadians(degrees); }

    /** One swing: attackTime rises 0 -> ~1 over the given ticks, then back to 0. */
    private static float swingAt(float tickWithinSwing, float duration) {
        if (tickWithinSwing < 0f || tickWithinSwing >= duration) return 0f;
        return tickWithinSwing / duration;
    }

    // ---------------------------------------------------------------- the timer

    @Test void theTimerStartsExactlyOnceOnTheSwingEdgeAndNotOnEveryFrame() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        // Four render frames inside one six-tick swing, then the swing ends.
        float age = 0f;
        tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        float holdAfterStart = -1f;
        for (int frame = 1; frame <= 12; frame++) {
            age += 0.5f;
            tracker.advance(age, swingAt(age, 6f), true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
            if (frame == 1) holdAfterStart = tracker.holdTicksLeft();
        }
        assertEquals(60f, holdAfterStart, 1e-4f, "the edge must load the full hold");
        // The edge fired at age 0.5 and the last frame is at age 6.0, so 5.5 ticks have been taken
        // off - not reloaded on each of the twelve frames, which would have left it at 60.
        assertEquals(54.5f, tracker.holdTicksLeft(), 1e-3f,
                "the frames inside one swing must not keep pushing the end out");
    }

    @Test void aSecondAttackRefreshesTheTimerFromTheLastSwing() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        float age = 0f;
        tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        age = 1f; tracker.advance(age, 0.2f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        for (int tick = 0; tick < 20; tick++) {
            age += 1f;
            tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        }
        assertEquals(40f, tracker.holdTicksLeft(), 1e-3f);
        age += 1f; // a fresh swing starts
        tracker.advance(age, 0.1f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        assertEquals(60f, tracker.holdTicksLeft(), 1e-4f, "a new attack must reload the full hold");
        // And a swing restarting mid-swing counts too.
        age += 1f; tracker.advance(age, 0.6f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        age += 1f; tracker.advance(age, 0.05f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        assertEquals(60f, tracker.holdTicksLeft(), 1e-4f, "a restarted swing clock must reload it too");
    }

    @Test void theGuardHoldsForTheConfiguredTicksThenFallsMonotonicallyToZero() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        float age = 0f;
        tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        age = 1f; tracker.advance(age, 0.2f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        float peak = 0f;
        for (int tick = 0; tick < 60; tick++) {
            age += 1f;
            peak = Math.max(peak, tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f));
        }
        assertEquals(1f, peak, 1e-4f, "the guard must reach full weight within the hold");
        float previous = 1f;
        for (int tick = 0; tick < 40; tick++) {
            age += 1f;
            float weight = tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
            assertTrue(weight <= previous + 1e-5f, "the release must be monotone");
            previous = weight;
        }
        assertEquals(0f, previous, 1e-5f, "and must reach exactly zero");
    }

    @Test void theResultAtAGivenTickIsTheSameAtAnyFrameRate() {
        var s = on();
        for (float step : new float[] {0.25f, 0.5f, 1f, 2f}) {
            var tracker = new BarehandCombatStance.Tracker();
            float age = 0f;
            tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
            float weightAt80 = 0f;
            for (int frame = 1; frame <= (int) (80f / step); frame++) {
                age = frame * step;
                float attack = age < 1f + step ? 0.2f : 0f;
                float weight = tracker.advance(age, attack, true, true, s.holdTicks(), s.blendInTicks(),
                        s.blendOutTicks(), 1f);
                if (Math.abs(age - 80f) < 1e-4f) weightAt80 = weight;
            }
            assertEquals(0f, weightAt80, 1e-4f, "at tick 80 the guard is long released whatever the frame rate");
        }
        // And at a tick inside the hold, every frame rate agrees.
        Float reference = null;
        for (float step : new float[] {0.25f, 0.5f, 1f}) {
            var tracker = new BarehandCombatStance.Tracker();
            float age = 0f, weight = 0f;
            tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
            for (int frame = 1; frame <= (int) (30f / step); frame++) {
                age = frame * step;
                weight = tracker.advance(age, age <= 1f + 1e-4f ? 0.2f : 0f, true, true,
                        s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
            }
            if (reference == null) reference = weight; else assertEquals(reference, weight, 1e-5f);
        }
    }

    @Test void anArmedSwingNeverRaisesTheBarehandGuard() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        float age = 0f;
        tracker.advance(age, 0f, false, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        for (int tick = 1; tick <= 10; tick++) {
            age += 1f;
            float weight = tracker.advance(age, swingAt(tick, 6f), false, true,
                    s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
            assertEquals(0f, weight, 0f, "a weapon swing must not raise the barehand guard");
        }
        assertEquals(0f, tracker.holdTicksLeft(), 0f);
    }

    @Test void theEntitySwingEdgeRaisesTheGuardWhenTheInterpolatedClockIsStillZero() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        tracker.advance(0f, 0f, false, true, true,
                s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);

        // This is the runtime race the render-only detector could miss: the entity has begun its
        // swing, while getAttackAnim(partialTick) still interpolates to exactly zero.
        tracker.advance(1f, 0f, true, true, true,
                s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        assertEquals(s.holdTicks(), tracker.holdTicksLeft(), 0f);

        float raised = tracker.advance(2f, 0.2f, true, true, true,
                s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        assertTrue(raised > 0f, "the next rendered tick must visibly blend into the guard");
        assertEquals(s.holdTicks() - 1f, tracker.holdTicksLeft(), 1e-5f,
                "seeing the attack clock afterwards must not restart or extend the same swing");
    }

    @Test void anUnsupportedStateCancelsTheTimerAndTheGuardLeaves() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        float age = 0f;
        tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        age = 1f; tracker.advance(age, 0.2f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        for (int tick = 0; tick < 10; tick++) {
            age += 1f;
            tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        }
        assertTrue(tracker.weight() > 0.5f, "the guard must be up before the state changes");
        for (int tick = 0; tick < 20; tick++) {
            age += 1f;
            tracker.advance(age, 0f, true, false, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        }
        assertEquals(0f, tracker.weight(), 1e-5f, "an unsupported state must let the guard go");
        assertEquals(0f, tracker.holdTicksLeft(), 0f, "and cancel the clock rather than pausing it");
    }

    @Test void aTimeJumpOrABrokenClockCannotMoveTheGuard() {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        tracker.advance(0f, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        tracker.advance(1f, 0.2f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        float before = tracker.weight();
        tracker.advance(90_000f, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        assertEquals(before, tracker.weight(), 1e-6f, "an impossible tick step must not move it");
        tracker.advance(-500f, 0f, true, true, s.holdTicks(), s.blendInTicks(), s.blendOutTicks(), 1f);
        assertEquals(before, tracker.weight(), 1e-6f, "nor a clock that went backwards");
        for (float broken : new float[] {Float.NaN, Float.POSITIVE_INFINITY}) {
            float weight = tracker.advance(broken, broken, true, true, s.holdTicks(), s.blendInTicks(),
                    s.blendOutTicks(), broken);
            assertTrue(Float.isFinite(weight), "a broken input must not produce a broken weight");
        }
        tracker.reset();
        assertEquals(0f, tracker.weight(), 0f);
        assertEquals(0f, tracker.holdTicksLeft(), 0f);
    }

    // ---------------------------------------------------------------- the pose

    @Test void aWeightOfZeroAddsNothingAtAll() {
        float[] out = new float[N];
        java.util.Arrays.fill(out, 7f);
        assertFalse(BarehandCombatStance.pose(on(), 0f, 1f, true, 10f, out));
        for (float value : out) assertEquals(7f, value, 0f, "a zero weight must not write anything");
        assertFalse(BarehandCombatStance.pose(null, 1f, 1f, true, 10f, out));
        assertFalse(BarehandCombatStance.pose(on(), 1f, 1f, true, 10f, new float[3]));
    }

    @Test void everyAngleIsFiniteAndUnderItsHardCeiling() {
        var wild = new BarehandCombatStance.Settings(true, 400f, 0f, 0f, 1f, 1f, 1f,
                999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 999f, 2f);
        float[] out = new float[N];
        for (int step = 0; step <= 200; step++) {
            assertTrue(BarehandCombatStance.pose(wild, (step + 1) / 201f, step / 200f, step % 2 == 0, step * 3f, out));
            for (float value : out) assertTrue(Float.isFinite(value));
            assertTrue(Math.abs(out[BarehandCombatStance.BODY_X]) <= radians(BarehandCombatStance.MAXIMUM_BODY_PITCH_DEGREES) + 1e-5f);
            assertTrue(Math.abs(out[BarehandCombatStance.BODY_Y]) <= radians(BarehandCombatStance.MAXIMUM_BODY_YAW_DEGREES) + 1e-5f);
            for (int slot : new int[] {BarehandCombatStance.FRONT_UPPER_X, BarehandCombatStance.REAR_UPPER_X}) {
                assertTrue(Math.abs(out[slot]) <= radians(BarehandCombatStance.MAXIMUM_UPPER_ARM_DEGREES) + 1e-5f);
            }
            for (int slot : new int[] {BarehandCombatStance.FRONT_LOWER_X, BarehandCombatStance.REAR_LOWER_X}) {
                assertTrue(Math.abs(out[slot]) <= radians(BarehandCombatStance.MAXIMUM_ELBOW_DEGREES) + 1e-5f);
            }
            for (int slot : new int[] {BarehandCombatStance.FRONT_LEG_X, BarehandCombatStance.REAR_LEG_X}) {
                assertTrue(Math.abs(out[slot]) <= radians(BarehandCombatStance.MAXIMUM_LEG_DEGREES) + 1e-5f);
            }
            for (int slot : new int[] {BarehandCombatStance.FRONT_KNEE_X, BarehandCombatStance.REAR_KNEE_X}) {
                assertTrue(Math.abs(out[slot]) <= radians(BarehandCombatStance.MAXIMUM_KNEE_DEGREES) + 1e-5f);
            }
        }
    }

    @Test void theModelsFinalSafetyGuardAdmitsTheConfiguredElbowRange() {
        float worstDocumentedElbow = radians(30f + BarehandCombatStance.MAXIMUM_ELBOW_DEGREES);
        assertTrue(worstDocumentedElbow < ArticulatedHumanoidModel.MAX_SAFE_RADIANS,
                "the final model guard must not reject a locomotion elbow plus the valid stance ceiling");
        assertTrue(ArticulatedHumanoidModel.MAX_SAFE_RADIANS < (float) Math.PI,
                "the final guard must still reject wrapped or runaway rotations");
        // With the S2 strike layer in front of it the elbow is composed twice, but each write goes
        // through its own clamp: the strike leaves at most walk 30 + strike 105 = 135 degrees, and
        // the stance write then clamps the sum at walk 30 + stance 135 = 165. So 165 is still the
        // worst legitimate value the final guard has to admit, and it does, with five to spare.
        float worstWithStrike = radians(30f + BarehandCombatStance.MAXIMUM_ELBOW_DEGREES);
        assertTrue(worstWithStrike < ArticulatedHumanoidModel.MAX_SAFE_RADIANS,
                "the strike layer must not push the composed elbow past the final guard");
        assertTrue(radians(170f) <= ArticulatedHumanoidModel.MAX_SAFE_RADIANS + 1e-6f
                        && ArticulatedHumanoidModel.MAX_SAFE_RADIANS <= radians(170f) + 1e-6f,
                "the corrected final limit is exactly 170 degrees, not a wider one");
        // Anything past it, and anything non-finite, is still refused. This mirrors what the
        // model's limited() does with the value once every per-axis clamp has already run.
        for (float broken : new float[] {radians(171f), radians(200f), Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(Float.isFinite(broken)
                            && Math.abs(broken) <= ArticulatedHumanoidModel.MAX_SAFE_RADIANS,
                    "a runaway or non-finite rotation must still be refused");
        }
    }

    // ---------------------------------------------------------------- S2: the strike handover

    @Test void theGuardGivesTheStrikeTheArmItIsPunchingWithAndKeepsTheRest() {
        float[] whole = new float[N], yielded = new float[N];
        assertTrue(BarehandCombatStance.pose(on(), 1f, 1f, true, 0f, whole));
        assertTrue(BarehandCombatStance.pose(on(), 1f, 1f, true, 0f, yielded));
        BarehandCombatStance.yieldToStrike(yielded, 1f, BarehandCombatStance.STRIKE_BODY_SHARE,
                BarehandCombatStance.STRIKE_FRONT_SHARE, 1f);
        // The attacking arm is handed over completely: a fist folded to the guard's elbow is not a
        // punch, which is exactly the fault this exists to fix.
        for (int slot : new int[] {BarehandCombatStance.REAR_UPPER_X, BarehandCombatStance.REAR_UPPER_Z,
                BarehandCombatStance.REAR_LOWER_X}) {
            assertEquals(0f, yielded[slot], 1e-7f, "the punching arm must be fully the strike's");
        }
        // The off arm keeps almost all of its guard, so the other hand stays up.
        assertTrue(Math.abs(yielded[BarehandCombatStance.FRONT_LOWER_X])
                        > 0.8f * Math.abs(whole[BarehandCombatStance.FRONT_LOWER_X]),
                "the off hand must stay in guard while the other one punches");
        // The torso splits the difference, and the legs are never touched at all.
        assertEquals(whole[BarehandCombatStance.BODY_Y] * (1f - BarehandCombatStance.STRIKE_BODY_SHARE),
                yielded[BarehandCombatStance.BODY_Y], 1e-6f);
        for (int slot : new int[] {BarehandCombatStance.FRONT_LEG_X, BarehandCombatStance.REAR_LEG_X,
                BarehandCombatStance.FRONT_KNEE_X, BarehandCombatStance.REAR_KNEE_X}) {
            assertEquals(whole[slot], yielded[slot], 0f, "the ready stance must not twitch on a punch");
        }
    }

    @Test void aStrikeThatOwnsNothingLeavesTheGuardBitIdentical() {
        float[] whole = new float[N], untouched = new float[N];
        assertTrue(BarehandCombatStance.pose(on(), .6f, .4f, false, 11f, whole));
        assertTrue(BarehandCombatStance.pose(on(), .6f, .4f, false, 11f, untouched));
        for (float nothing : new float[] {0f, -1f, Float.NaN, Float.NEGATIVE_INFINITY}) {
            BarehandCombatStance.yieldToStrike(untouched, nothing, BarehandCombatStance.STRIKE_BODY_SHARE,
                    BarehandCombatStance.STRIKE_FRONT_SHARE, 1f);
            for (int slot = 0; slot < N; slot++) {
                assertEquals(whole[slot], untouched[slot], 0f,
                        "with no strike running the guard must be exactly what it was");
            }
        }
        // A short array or a null one is refused rather than half-written.
        BarehandCombatStance.yieldToStrike(null, 1f, .5f, .5f, 1f);
        float[] tooShort = new float[] {1f, 2f};
        BarehandCombatStance.yieldToStrike(tooShort, 1f, .5f, .5f, 1f);
        assertEquals(1f, tooShort[0], 0f);
    }

    @Test void anOffHandSwingWhileTheGuardIsUpHandsOverTheOtherArm() {
        // The guard latches the power hand when it goes up. If the OTHER hand then swings, the arm
        // that has to give way is the one attacking, not the latched rear one.
        float[] frontAttacks = new float[N];
        assertTrue(BarehandCombatStance.pose(on(), 1f, 1f, true, 0f, frontAttacks));
        BarehandCombatStance.yieldToStrike(frontAttacks, 1f, BarehandCombatStance.STRIKE_BODY_SHARE,
                1f, BarehandCombatStance.STRIKE_FRONT_SHARE);
        assertEquals(0f, frontAttacks[BarehandCombatStance.FRONT_LOWER_X], 1e-7f,
                "the front arm must give way when the front arm is the one striking");
        assertTrue(Math.abs(frontAttacks[BarehandCombatStance.REAR_LOWER_X]) > 0f,
                "and the rear arm must keep its guard");
    }

    @Test void theElbowsOnlyFoldTheWayAnElbowFoldsAndTheHandsComeForward() {
        float[] out = new float[N];
        assertTrue(BarehandCombatStance.pose(on(), 1f, 1f, true, 0f, out));
        assertTrue(out[BarehandCombatStance.FRONT_LOWER_X] < 0f, "the lead elbow must fold forwards");
        assertTrue(out[BarehandCombatStance.REAR_LOWER_X] < 0f, "and so must the rear one");
        assertTrue(out[BarehandCombatStance.FRONT_UPPER_X] < 0f, "the lead hand must come forward");
        assertTrue(out[BarehandCombatStance.REAR_LOWER_X] < out[BarehandCombatStance.FRONT_LOWER_X],
                "the rear hand is the tighter guard");
        // The tuck is inward on both sides, which is opposite signs on the two physical arms.
        assertTrue(out[BarehandCombatStance.FRONT_UPPER_Z] * out[BarehandCombatStance.REAR_UPPER_Z] < 0f,
                "both elbows must tuck in, which is opposite rolls on the two arms");
    }

    @Test void theTwoSidesAreExactMirrors() {
        float[] right = new float[N], left = new float[N];
        assertTrue(BarehandCombatStance.pose(on(), 1f, 1f, true, 5f, right));
        assertTrue(BarehandCombatStance.pose(on(), 1f, 1f, false, 5f, left));
        assertEquals(-right[BarehandCombatStance.BODY_Y], left[BarehandCombatStance.BODY_Y], 1e-6f);
        assertEquals(-right[BarehandCombatStance.FRONT_UPPER_Z], left[BarehandCombatStance.FRONT_UPPER_Z], 1e-6f);
        assertEquals(-right[BarehandCombatStance.REAR_UPPER_Z], left[BarehandCombatStance.REAR_UPPER_Z], 1e-6f);
        // The role slots themselves are the same; only the axes with a side flip.
        assertEquals(right[BarehandCombatStance.FRONT_LOWER_X], left[BarehandCombatStance.FRONT_LOWER_X], 1e-6f);
        assertEquals(right[BarehandCombatStance.FRONT_LEG_X], left[BarehandCombatStance.FRONT_LEG_X], 1e-6f);
    }

    @Test void theLegsAreTouchedOnlyWhileStandingStillAndBarelyLeaveTheGround() {
        float[] moving = new float[N];
        assertTrue(BarehandCombatStance.pose(on(), 1f, 0f, true, 0f, moving));
        for (int slot : new int[] {BarehandCombatStance.FRONT_LEG_X, BarehandCombatStance.REAR_LEG_X,
                BarehandCombatStance.FRONT_KNEE_X, BarehandCombatStance.REAR_KNEE_X}) {
            assertEquals(0f, moving[slot], 0f, "moving, the gait owns the legs");
        }
        assertTrue(Math.abs(moving[BarehandCombatStance.FRONT_LOWER_X]) > 0f, "the upper guard still applies");
        var s = on();
        // The front leg goes back on the clip's negative side and the rear forward at 0.7 of the
        // setting, so both real cases are checked rather than an angle nothing uses.
        float front = BarehandCombatStance.footRise(-s.legReadyDegrees(), s.kneeReadyDegrees());
        float rear = BarehandCombatStance.footRise(0.7f * s.legReadyDegrees(), s.kneeReadyDegrees());
        assertTrue(front >= 0f && front < 0.25f, () -> "the lead foot lifts " + front + " px");
        assertTrue(rear >= 0f && rear < 0.25f, () -> "the rear foot lifts " + rear + " px");
        float worst = BarehandCombatStance.footRise(BarehandCombatStance.MAXIMUM_LEG_DEGREES,
                BarehandCombatStance.MAXIMUM_KNEE_DEGREES);
        // At the absolute hard ceiling it is 1.204 px, which is why the shipped values are far
        // below it: the whole point of the ready stance is that the feet stay on the floor.
        assertTrue(worst < 1.3f, () -> "even at the hard ceiling a foot only lifts " + worst + " px");
        assertTrue(front < 0.1f && rear < 0.2f,
                () -> "the shipped stance lifts the feet " + front + " and " + rear + " px");
    }

    @Test void theReleaseIsAsSmoothAsTheConfiguredWindowAllowsAndTheFiveDegreeTargetIsPriced() {
        // The request asks that no joint step more than 5 degrees in a tick at the end. That is a
        // property of the WINDOW, not of the smoothing: the deepest angle in the guard is the rear
        // elbow, and an eased release of A degrees over W ticks peaks at 1.5*A/W per tick, because
        // the derivative of a smoothstep peaks at 1.5. The higher guard now uses a 125 degree
        // elbow: the 10 tick release peaks near 18.75 degrees a tick, and reaching 5 needs 38.
        // Both numbers are measured here rather than assumed, and the trade is in the report.
        float deepest = on().rearElbowDegrees();
        assertEquals(125f, deepest, 0f, "the rear elbow is the joint that sets the pace");
        assertEquals(38f, Math.ceil(1.5f * deepest / 5f), 0f, "5 degrees a tick needs a 38 tick release");
        float shipped = measureWorstStep(on().blendOutTicks());
        assertTrue(shipped < 1.5f * deepest / on().blendOutTicks() + 0.5f,
                () -> "the release stepped " + shipped + " degrees, past what the window allows");
        float patient = measureWorstStep(39f);
        assertTrue(patient <= 5f, () -> "a 39 tick release still stepped " + patient + " degrees");
    }

    /** Worst single-tick change of any joint across a full raise, hold and release. */
    private static float measureWorstStep(float blendOutTicks) {
        var tracker = new BarehandCombatStance.Tracker();
        var s = on();
        float[] previous = new float[N], current = new float[N];
        float age = 0f, worst = 0f;
        tracker.advance(age, 0f, true, true, s.holdTicks(), s.blendInTicks(), blendOutTicks, 1f);
        for (int tick = 1; tick <= 140; tick++) {
            age += 1f;
            float weight = tracker.advance(age, tick == 1 ? 0.2f : 0f, true, true,
                    s.holdTicks(), s.blendInTicks(), blendOutTicks, 1f);
            java.util.Arrays.fill(current, 0f);
            BarehandCombatStance.pose(s, weight, 1f, true, age, current);
            // Only the release is being priced here; the raise has its own, shorter window.
            if (tick > s.holdTicks()) {
                for (int slot = 0; slot < N; slot++) worst = Math.max(worst, Math.abs(current[slot] - previous[slot]));
            }
            System.arraycopy(current, 0, previous, 0, N);
        }
        return (float) Math.toDegrees(worst);
    }

    // ---------------------------------------------------------------- wiring and defaults

    @Test void theStanceNeverTouchesTheHeadOrWritesAnOffset() throws IOException {
        String model = Files.readString(MODEL);
        int start = model.indexOf("private void applyStance(");
        assertTrue(start > 0, "applyStance must exist");
        String body = model.substring(start, model.indexOf("\n    /**", start + 40));
        assertFalse(body.contains("head."), "the world look stays Mojang's mouse, solved after this");
        assertFalse(body.contains(".x =") || body.contains(".y =") || body.contains(".z ="),
                "rotation only: no part offset may be written");
        assertFalse(body.contains("Scale"), "no scale may be written");
        assertTrue(model.indexOf("applyStance(state.ageInTicks)") < model.indexOf("applyHeadLook(lookPitch, lookYaw)"),
                "the head look must still be solved from the final body rotation");
    }

    @Test void theBlockShipsOffAndEveryShippedValueIsInsideItsRange() throws IOException {
        String yaml = Files.readString(SETTINGS);
        int start = yaml.indexOf("  barehand-stance:");
        assertTrue(start > 0, "the block must ship");
        String block = yaml.substring(start, yaml.indexOf("\n  sword:", start));
        assertTrue(block.contains("\n    enabled: false"), "the block must ship off");
        assertFalse(block.contains("enabled: true"));
        assertTrue(block.contains("restart"), "it must say it is read once at client start");
        assertTrue(block.contains("MINING A BLOCK WITH AN EMPTY HAND"),
                "the block must warn that mining bare-handed raises the guard");
        record Range(String key, double minimum, double maximum, double shipped) { }
        var d = BarehandCombatStance.DISABLED;
        var ranges = new Range[] {
            new Range("hold-ticks", 0, 400, d.holdTicks()),
            new Range("blend-in-ticks", 0, 40, d.blendInTicks()),
            new Range("blend-out-ticks", 0, 40, d.blendOutTicks()),
            new Range("walk-weight", 0, 1, d.walkWeight()),
            new Range("sprint-weight", 0, 1, d.sprintWeight()),
            new Range("airborne-weight", 0, 1, d.airborneWeight()),
            new Range("body-pitch-degrees", 0, BarehandCombatStance.MAXIMUM_BODY_PITCH_DEGREES, d.bodyPitchDegrees()),
            new Range("body-yaw-degrees", 0, BarehandCombatStance.MAXIMUM_BODY_YAW_DEGREES, d.bodyYawDegrees()),
            new Range("front-upper-arm-degrees", 0, BarehandCombatStance.MAXIMUM_UPPER_ARM_DEGREES, d.frontUpperArmDegrees()),
            new Range("front-elbow-degrees", 0, BarehandCombatStance.MAXIMUM_ELBOW_DEGREES, d.frontElbowDegrees()),
            new Range("rear-upper-arm-degrees", 0, BarehandCombatStance.MAXIMUM_UPPER_ARM_DEGREES, d.rearUpperArmDegrees()),
            new Range("rear-elbow-degrees", 0, BarehandCombatStance.MAXIMUM_ELBOW_DEGREES, d.rearElbowDegrees()),
            new Range("arm-tuck-degrees", 0, BarehandCombatStance.MAXIMUM_ARM_ROLL_DEGREES, d.armTuckDegrees()),
            new Range("leg-ready-degrees", 0, BarehandCombatStance.MAXIMUM_LEG_DEGREES, d.legReadyDegrees()),
            new Range("knee-ready-degrees", 0, BarehandCombatStance.MAXIMUM_KNEE_DEGREES, d.kneeReadyDegrees()),
            new Range("breath-degrees", 0, BarehandCombatStance.MAXIMUM_BREATH_DEGREES, d.breathDegrees()),
            new Range("breath-seconds", 2, 12, d.breathSeconds()),
        };
        for (Range range : ranges) {
            int at = block.indexOf("\n    " + range.key() + ":");
            assertTrue(at > 0, () -> "the block must ship " + range.key());
            int end = block.indexOf('\n', at + 1);
            String line = block.substring(at + 1, end < 0 ? block.length() : end);
            double shipped = Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
            assertTrue(shipped >= range.minimum() && shipped <= range.maximum(),
                    () -> range.key() + " ships " + shipped + ", outside its validated range");
            assertEquals(range.shipped(), shipped, 1e-6d,
                    range.key() + " in the file disagrees with the code fallback");
        }
        assertEquals(60f, d.holdTicks(), 0f, "three seconds at twenty ticks");
        assertFalse(d.enabled(), "the code fallback must also be off");
        assertEquals(0f, d.breathDegrees(), 0f,
                "breathing ships off so it cannot double motion-style.idle's own breath");
    }
}
