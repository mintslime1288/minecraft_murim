package com.murimrpg.client.render.wuxia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Clip selection, distance-driven cadence, cross-fades and the unsupported-state fallback. */
class WuxiaLocomotionTest {

    private static final float TICK = 1f / 20f;

    private static WuxiaPlayerSettings enabled() {
        WuxiaPlayerSettings d = WuxiaPlayerSettings.disabled();
        return new WuxiaPlayerSettings(true, d.modelPath(), d.targetHeightBlocks(), d.sourceHeightMetres(),
                d.groundOffsetBlocks(), d.yawDegrees(), d.flipVertical(), d.transitionSeconds(),
                d.movementThreshold(), d.runSpeedThreshold(), d.maximumPlaybackRate(), d.jumpEnabled(),
                d.jumpSeconds(), d.jumpClipStart(), d.jumpClipEnd(), d.idleClip(), d.walkClip(),
                d.runClip(), d.crouchClip(), d.jumpClip(), d.idleSeconds(), d.walkSeconds(),
                d.runSeconds(), d.crouchSeconds(), d.walkStrideMetres(), d.runStrideMetres(),
                d.crouchStrideMetres(), d.allowHeldItems(), d.allowArmour(), d.minimumSkinnedHeight(),
                d.maximumSkinnedHeight(), d.maximumSkinnedWidth(), d.maximumSkinnedDepth(),
                d.validationSamples());
    }

    private static WuxiaLocomotion.Input walking(float speed) {
        return new WuxiaLocomotion.Input(speed, TICK, false, false, false, true);
    }

    @Test
    @DisplayName("an unsupported state renders nothing instead of borrowing a clip")
    void unsupportedStateRendersNothing() {
        var engine = new WuxiaLocomotion(enabled());
        var state = engine.update(new WuxiaLocomotion.Input(4.3f, TICK, false, false, false, false));
        assertEquals(WuxiaLocomotion.Clip.NONE, state.primary());
        assertFalse(state.renders());
    }

    @Test
    @DisplayName("standing, walking, sprinting, crouching and airborne each pick their own clip")
    void clipSelectionCoversEveryAuthoredState() {
        var s = enabled();
        assertEquals(WuxiaLocomotion.Clip.IDLE, new WuxiaLocomotion(s).update(walking(0f)).primary());
        assertEquals(WuxiaLocomotion.Clip.WALK, new WuxiaLocomotion(s).update(walking(4.3f)).primary());
        assertEquals(WuxiaLocomotion.Clip.RUN, new WuxiaLocomotion(s).update(walking(5.6f)).primary());
        assertEquals(WuxiaLocomotion.Clip.RUN, new WuxiaLocomotion(s).update(
                new WuxiaLocomotion.Input(4.3f, TICK, true, false, false, true)).primary());
        assertEquals(WuxiaLocomotion.Clip.CROUCH_WALK, new WuxiaLocomotion(s).update(
                new WuxiaLocomotion.Input(1.3f, TICK, false, true, false, true)).primary());
        assertEquals(WuxiaLocomotion.Clip.JUMP, new WuxiaLocomotion(s).update(
                new WuxiaLocomotion.Input(4.3f, TICK, false, false, true, true)).primary());
    }

    @Test
    @DisplayName("phase advances with distance, so one stride of travel is exactly one cycle")
    void phaseIsDrivenByDistanceNotByTime() {
        var s = enabled();
        // Walk slowly enough that the playback-rate cap never engages, then travel one stride.
        float speed = 1.4f;
        var engine = new WuxiaLocomotion(s);
        engine.update(walking(speed));
        float travelled = speed * TICK;
        int steps = 1;
        while (travelled < s.walkStrideMetres() - speed * TICK) {
            engine.update(walking(speed));
            travelled += speed * TICK;
            steps++;
        }
        float expected = travelled / s.walkStrideMetres();
        assertEquals(expected, engine.gaitPhase(), 1.0e-5,
                "after " + steps + " ticks the phase must equal distance / stride");
        assertTrue(expected > .9f && expected < 1f, "the sweep should stop just short of one cycle");
    }

    @Test
    @DisplayName("doubling the speed doubles the phase travelled, up to the configured rate cap")
    void phaseScalesWithSpeed() {
        var s = enabled();
        var slow = new WuxiaLocomotion(s);
        var fast = new WuxiaLocomotion(s);
        for (int i = 0; i < 10; i++) { slow.update(walking(1.0f)); fast.update(walking(2.0f)); }
        assertEquals(2f * slow.gaitPhase(), fast.gaitPhase(), 1.0e-5);
    }

    @Test
    @DisplayName("the rate cap reports the foot slip it causes instead of hiding it")
    void rateCapReportsSlip() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        engine.update(walking(4.317f));
        var state = engine.update(walking(4.317f));
        // Minecraft walks 4.317 blocks/s. A 1.5408 m stride in a 1.0833 s clip plays 1.4223 m/s
        // at 1.0x, so matching the game needs 3.035x, which is inside the 3.2 cap.
        assertEquals(0f, state.slipPerSecond(), 1.0e-6, "4.317 blocks/s must fit under the cap");

        // 12 blocks/s is above the run threshold, so the run clip's own stride and length set
        // the ceiling: 3.2 / 0.7083 s per cycle x 1.4854 m per cycle = 6.711 blocks/s.
        var capped = new WuxiaLocomotion(s);
        capped.update(walking(12f));
        var fastState = capped.update(walking(12f));
        assertEquals(WuxiaLocomotion.Clip.RUN, fastState.primary());
        assertTrue(fastState.slipPerSecond() > 0f, "a speed above the cap must report slip");
        float playable = s.maximumPlaybackRate() * s.runStrideMetres() / s.runSeconds();
        assertEquals(6.711f, playable, .005f);
        assertEquals(12f - playable, fastState.slipPerSecond(), .01f,
                "reported slip must equal the un-played ground speed");
    }

    @Test
    @DisplayName("sprinting at 5.612 blocks per second stays inside the cap with zero slip")
    void vanillaSprintFitsUnderTheCap() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        var input = new WuxiaLocomotion.Input(5.612f, TICK, true, false, false, true);
        engine.update(input);
        assertEquals(0f, engine.update(input).slipPerSecond(), 1.0e-6);
    }

    @Test
    @DisplayName("a gait change cross-fades instead of popping, and both clips share one phase")
    void gaitChangeCrossFades() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        for (int i = 0; i < 20; i++) engine.update(walking(4.0f));
        var first = engine.update(new WuxiaLocomotion.Input(5.6f, TICK, true, false, false, true));
        assertEquals(WuxiaLocomotion.Clip.RUN, first.primary());
        assertEquals(WuxiaLocomotion.Clip.WALK, first.secondary());
        assertTrue(first.secondaryWeight() > 0f && first.secondaryWeight() < 1f);
        assertEquals(first.primaryPhase(), first.secondaryPhase(), 1.0e-6,
                "both gait clips must be sampled at the same phase so no leg rewinds");
    }

    @Test
    @DisplayName("the cross-fade weight falls monotonically to zero within the configured time")
    void crossFadeCompletesAndIsMonotone() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        for (int i = 0; i < 20; i++) engine.update(walking(4.0f));
        float previous = 1f;
        int ticks = 0;
        var sprint = new WuxiaLocomotion.Input(5.6f, TICK, true, false, false, true);
        WuxiaLocomotion.State state = engine.update(sprint);
        while (state.secondaryWeight() > 0f && ticks < 200) {
            assertTrue(state.secondaryWeight() <= previous + 1.0e-6, "weight must not rise");
            previous = state.secondaryWeight();
            state = engine.update(sprint);
            ticks++;
        }
        assertTrue(ticks <= Math.ceil(s.transitionSeconds() / TICK) + 1,
                "the fade must finish within the configured time, took " + ticks + " ticks");
        assertEquals(WuxiaLocomotion.Clip.NONE, state.secondary());
    }

    @Test
    @DisplayName("121 uniform ticks of walking never produce a backwards or discontinuous phase")
    void phaseIsContinuousAcrossTheSweep() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        float previous = 0f;
        float largest = 0f;
        for (int i = 0; i < 121; i++) {
            engine.update(walking(4.317f));
            float now = engine.gaitPhase();
            float step = now - previous;
            if (step < 0f) step += 1f;
            largest = Math.max(largest, step);
            assertTrue(step >= 0f, "phase must never run backwards");
            previous = now;
        }
        float expected = 4.317f * TICK / s.walkStrideMetres();
        assertEquals(expected, largest, 1.0e-5, "every step must be exactly one tick of travel");
    }

    @Test
    @DisplayName("the jump phase runs once from 0 to 1 and resets on landing")
    void jumpPhaseRunsOnceAndResets() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        var airborne = new WuxiaLocomotion.Input(4f, TICK, false, false, true, true);
        float previous = -1f;
        for (int i = 0; i < 40; i++) {
            var state = engine.update(airborne);
            assertEquals(WuxiaLocomotion.Clip.JUMP, state.primary());
            assertTrue(state.primaryPhase() >= previous, "jump phase must not run backwards");
            assertTrue(state.primaryPhase() <= 1f, "jump phase must never exceed 1");
            previous = state.primaryPhase();
        }
        assertEquals(1f, previous, 1.0e-6, "a long fall holds the clip at its end, it does not loop");
        var landed = engine.update(walking(4f));
        assertNotEquals(WuxiaLocomotion.Clip.JUMP, landed.primary());
    }

    @Test
    @DisplayName("an idle player animates from its own clock, not from distance")
    void idleUsesItsOwnClock() {
        var s = enabled();
        var engine = new WuxiaLocomotion(s);
        engine.update(walking(0f));
        for (int i = 0; i < 10; i++) engine.update(walking(0f));
        var state = engine.update(walking(0f));
        assertEquals(WuxiaLocomotion.Clip.IDLE, state.primary());
        assertTrue(state.primaryPhase() > 0f, "idle must advance even with no movement");
        assertEquals(0f, engine.gaitPhase(), 1.0e-6, "the gait phase must not drift while standing");
    }
}
