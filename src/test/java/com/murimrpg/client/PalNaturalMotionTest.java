package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.*;
import static com.murimrpg.client.PalAnimationStateSelector.Locomotion.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PalNaturalMotionTest {
    @TempDir Path directory;
    private Map<PalAnimationStateSelector.Locomotion,PalSmoothClip> clips() throws Exception {
        var result=new EnumMap<PalAnimationStateSelector.Locomotion,PalSmoothClip>(PalAnimationStateSelector.Locomotion.class);
        String[] names={"pal_idle","pal_walk","pal_run","pal_fast_run","pal_jump_takeoff","pal_jump_rise",
                "pal_jump_apex","pal_jump_fall","pal_jump_land"};
        var states=new PalAnimationStateSelector.Locomotion[]{IDLE,WALK,RUN,FAST_RUN,JUMP_TAKEOFF,JUMP_RISE,JUMP_APEX,JUMP_FALL,JUMP_LAND};
        for(int i=0;i<names.length;i++) try(var reader=Files.newBufferedReader(Path.of(
                "src/main/resources/assets/murimrpg/player_animations",names[i]+".json"))) {
            result.put(states[i],new PalSmoothClip(reader));
        }
        return result;
    }
    private PalNaturalMotion engine() throws Exception { return new PalNaturalMotion(PalNaturalSettings.load(directory),clips()); }

    @Test void allNineActualResourcesLoadAndStayFiniteWithCorrectHinges() throws Exception {
        double[] out=new double[20];
        for(var clip:clips().values()) for(double t=0;t<clip.length;t+=.03125) {
            clip.sample(t,out);
            for(double v:out) assertTrue(Double.isFinite(v)&&Math.abs(v)<Math.PI);
            assertTrue(out[7]<=1e-9&&out[11]<=1e-9&&out[15]>=-1e-9&&out[19]>=-1e-9);
        }
    }
    @Test void everyLoopAndInternalKeyHasContinuousAngularVelocity() throws Exception {
        double[] a=new double[20],b=new double[20],c=new double[20];
        double e=1e-4;
        for(var clip:clips().values()) for(double t:clip.ticks) {
            if(!clip.loop && (t==0||t==clip.length)) continue;
            clip.sample(t-e,a);clip.sample(t,b);clip.sample(t+e,c);
            for(int k=0;k<20;k++) assertEquals((b[k]-a[k])/e,(c[k]-b[k])/e,0.0002,
                    "Velocity seam at tick "+t+" channel "+k);
        }
    }
    @Test void walkAndRunKeepHalfCycleKneeSymmetry() throws Exception {
        double[] a=new double[20],b=new double[20];
        for(var state:new PalAnimationStateSelector.Locomotion[]{WALK,RUN,FAST_RUN}) {
            var clip=clips().get(state);
            for(double t=0;t<clip.length;t+=.125) {
                clip.sample(t,a);clip.sample(t+clip.length/2,b);
                assertEquals(a[15],b[19],1e-6); assertEquals(a[19],b[15],1e-6);
            }
        }
    }
    @Test void constantMotionIsIdenticalAt30_60_144Fps() throws Exception {
        double[] reference=null;
        for(int fps:new int[]{30,60,144}) {
            var engine=engine();
            for(int frame=0;frame<=fps*6;frame++) assertTrue(engine.update(frame*20.0/fps,WALK,0,.08,0));
            if(reference==null) reference=engine.pose.clone(); else assertArrayEquals(reference,engine.pose,1e-8);
        }
    }
    @Test void periodTransitionIsFrameIndependentAndDoesNotResetPhase() throws Exception {
        double reference=-1;
        for(int fps:new int[]{30,60,144}) {
            var engine=engine();
            engine.update(0,WALK,0,.08,0);engine.update(20,WALK,0,.08,0);
            double before=engine.phase();engine.update(20,RUN,0,.13,0);
            assertEquals(before,engine.phase());
            for(int i=1;i<=fps*2;i++) engine.update(20+i*20.0/fps,RUN,0,.13,0);
            if(reference<0) reference=engine.phase(); else assertEquals(reference,engine.phase(),1e-9);
        }
    }
    @Test void duplicateRenderPassesDoNotAdvanceOrChangePose() throws Exception {
        var engine=engine();engine.update(0,WALK,0,.08,0);engine.update(5.5,WALK,0,.08,0);
        double phase=engine.phase();double[] pose=engine.pose.clone();
        for(int i=0;i<50;i++) engine.update(5.5,WALK,0,.08,0);
        assertEquals(phase,engine.phase());assertArrayEquals(pose,engine.pose);
    }
    @Test void gaitTransitionBeginsAtPreviousPoseRatherThanSymmetricReset() throws Exception {
        var engine=engine();engine.update(0,WALK,0,.08,0);engine.update(6,WALK,0,.08,0);
        double[] before=engine.pose.clone();engine.update(6,RUN,0,.13,0);
        assertArrayEquals(before,engine.pose,1e-12);
        engine.update(6.05,RUN,0,.13,0);
        for(int i=0;i<20;i++) assertTrue(Math.abs(before[i]-engine.pose[i])<.01);
    }
    @Test void movingTakeoffRetainsSupportLegDifference() throws Exception {
        var engine=engine();engine.update(0,RUN,0,.13,0);engine.update(3,RUN,0,.13,0);
        double[] before=engine.pose.clone();engine.update(3,JUMP_TAKEOFF,0,.13,0);
        assertArrayEquals(before,engine.pose,1e-12);
        engine.update(4,JUMP_TAKEOFF,0,.13,0);
        assertTrue(Math.abs(engine.pose[12]-engine.pose[16])>.05);
    }
    @Test void backwardTravelReversesForeAftAndSideTravelAddsLateralArc() throws Exception {
        var front=engine();var back=engine();var side=engine();
        for(int i=0;i<=12;i++) { front.update(i,WALK,0,.08,0);back.update(i,WALK,0,-.08,0);side.update(i,WALK,-.08,0,0); }
        assertEquals(-front.pose[12],back.pose[12],1e-9);
        assertEquals(front.pose[15],back.pose[15],1e-9);
        assertEquals(0,side.pose[12],1e-9);
        assertNotEquals(front.pose[14],side.pose[14]);
    }
    @Test void idleBreathingDoesNotMoveSupportLegs() throws Exception {
        var engine=engine();engine.update(0,IDLE,0,0,0);double[] initial=engine.pose.clone();
        engine.update(12,IDLE,0,0,0);
        for(int i=12;i<20;i++) assertEquals(initial[i],engine.pose[i]);
        assertNotEquals(initial[0],engine.pose[0]);
    }
    @Test void invalidInputsResetWithoutPoisoningNextPlayerPose() throws Exception {
        var engine=engine();engine.update(0,WALK,0,.08,0);
        assertFalse(engine.update(1,WALK,Double.NaN,0,0));
        assertTrue(engine.update(2,IDLE,0,0,0));
        assertArrayEquals(engine().pose,new double[20]);
        for(double v:engine.pose) assertTrue(Double.isFinite(v));
    }
    @Test void badResourceOrConfigurationRejectsUnsafeValues() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new PalSmoothClip(new StringReader("{}")));
        Files.writeString(directory.resolve("pal-natural-motion.yml"),"walk-cycle-ticks: .nan\n");
        assertThrows(IllegalArgumentException.class,()->PalNaturalSettings.load(directory));
    }
    // ---------------------------------------------------------------- distance-driven cadence

    /** Vanilla walk and sprint ground speed, blocks per tick, and vanilla's own limb-swing cycle. */
    private static final double WALK_SPEED = 0.21585, SPRINT_SPEED = 0.2806;
    private static double vanillaCycleTicks(double speed) {
        return (2 * Math.PI / 0.6662) / Math.min(4 * speed, 1);
    }
    /** Runs the engine at a constant speed and returns the phase advanced over the measured window. */
    private double advance(PalNaturalMotion engine, PalAnimationStateSelector.Locomotion state,
                           double speed, double settleTicks, double windowTicks, double step) {
        double time = 0;
        for (; time <= settleTicks; time += step) engine.update(time, state, 0, speed, 0);
        double before = engine.phase(), wraps = 0, previous = before;
        double end = settleTicks + windowTicks;
        for (; time <= end; time += step) {
            engine.update(time, state, 0, speed, 0);
            double now = engine.phase();
            if (now < previous) wraps++;
            previous = now;
        }
        return wraps + previous - before;
    }

    @Test void theCadenceFollowsGroundSpeedInsteadOfAFixedTickPeriod() throws Exception {
        double slow = advance(engine(), WALK, .10, 60, 200, .5);
        double fast = advance(engine(), WALK, .20, 60, 200, .5);
        // Twice the ground speed is twice the step rate, because a step is a distance.
        assertEquals(2, fast / slow, 1e-6, "the cadence did not follow the ground speed");
        // And it is not the old constant: 200 ticks at the legacy 32-tick period would be 6.25.
        assertNotEquals(6.25, Math.round(fast * 1e6) / 1e6);
    }

    @Test void oneGaitCycleCoversTheConfiguredGroundDistance() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        for (var pair : new Object[][] {{WALK, settings.walkCycleBlocks()}, {RUN, settings.runCycleBlocks()},
                {FAST_RUN, settings.fastCycleBlocks()}}) {
            var state = (PalAnimationStateSelector.Locomotion) pair[0];
            double blocks = (Double) pair[1];
            double speed = state == WALK ? WALK_SPEED : SPRINT_SPEED;
            double window = 300;
            double cycles = advance(engine(), state, speed, 60, window, .5);
            assertEquals(blocks, speed * window / cycles, 1e-6,
                    state + " covered the wrong ground distance per cycle");
        }
    }

    @Test void theDefaultCycleDistancesReproduceVanillasOwnCadence() throws Exception {
        // Not a taste value: Mojang's limb swing is cos(walkAnimationPos * 0.6662) and
        // WalkAnimationState advances walkAnimationPos by min(4 * distance, 1) each tick, so one
        // vanilla cycle is 2*pi/0.6662 units of that. These defaults put our cycle on the same
        // ground distance, which is the cadence the player's eye is already calibrated to.
        var settings = PalNaturalSettings.load(directory);
        assertTrue(settings.distanceCadence(), "the distance-driven clock must ship on");
        assertEquals(vanillaCycleTicks(WALK_SPEED), settings.walkCycleBlocks() / WALK_SPEED, .05,
                "walking must take as long per cycle as vanilla does");
        assertEquals(vanillaCycleTicks(SPRINT_SPEED), settings.runCycleBlocks() / SPRINT_SPEED, .05,
                "sprinting must take as long per cycle as vanilla does");
        // The legacy fixed periods are what this replaces, and they are far slower than that.
        assertTrue(settings.walkTicks() > 2.5 * vanillaCycleTicks(WALK_SPEED));
    }

    @Test void theDistanceDrivenCadenceIsIdenticalAt30_60_144Fps() throws Exception {
        Double reference = null;
        for (int fps : new int[] {30, 60, 144}) {
            var engine = engine();
            double step = 20.0 / fps, wraps = 0, previous = 0;
            for (int frame = 0; frame <= fps * 8; frame++) {
                double time = frame * step;
                // An accelerating, then decelerating, profile: a constant speed would not catch a
                // rate filter that secretly integrates per frame instead of per tick.
                double speed = .05 + .18 * Math.sin(Math.min(Math.PI, time / 40));
                engine.update(time, WALK, 0, speed, 0);
                double now = engine.phase();
                if (now < previous) wraps++;
                previous = now;
            }
            double total = wraps + previous;
            // Not exactly equal, and it must not be claimed to be: the rate filter is exact for a
            // speed held constant across a frame, so sampling a speed that is itself still changing
            // at 30 and at 144 frames leaves an O(dt^2) quadrature difference. Measured over eight
            // seconds of accelerating travel it is about 3e-5 of one cycle - a thousandth of a
            // degree of leg swing. What must not happen is the cadence SCALING with the frame rate,
            // which this bound is three orders of magnitude tighter than.
            if (reference == null) reference = total; else assertEquals(reference, total, 1e-3,
                    "the cadence changed with the frame rate");
        }
    }

    @Test void anImpossibleSpeedCannotSpinTheLegsFasterThanTheFloor() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        var engine = engine();
        for (double time = 0; time <= 40; time += .5) engine.update(time, RUN, 0, 5, 0);
        assertTrue(engine.cycleTicks() >= settings.minimumCycleTicks() - 1e-9,
                () -> "a launch spun the gait to " + engine.cycleTicks() + " ticks per cycle");
        for (double v : engine.pose) assertTrue(Double.isFinite(v) && Math.abs(v) <= Math.PI);
    }

    @Test void walkingIntoAWallPlantsTheFeetInsteadOfRunningOnTheSpot() throws Exception {
        var engine = engine();
        for (double time = 0; time <= 40; time += .5) engine.update(time, WALK, 0, WALK_SPEED, 0);
        double moving = engine.phase();
        for (double time = 40.5; time <= 120; time += .5) engine.update(time, WALK, 0, 0, 0);
        // The rate eases to zero rather than stopping dead, so the phase settles and then holds.
        double settled = engine.phase();
        for (double time = 120.5; time <= 200; time += .5) engine.update(time, WALK, 0, 0, 0);
        assertEquals(settled, engine.phase(), 1e-9, "the stride kept turning with no ground moving");
        assertNotEquals(moving, settled);
        assertTrue(Double.isInfinite(engine.cycleTicks()), "a stopped gait has no cycle time");
    }

    @Test void turningOffTheDistanceClockRestoresTheExactPreviousPeriods() throws Exception {
        Files.writeString(directory.resolve("pal-natural-motion.yml"),
                "distance-driven-cadence: false\nwalk-cycle-ticks: 32\nrun-cycle-ticks: 16\n");
        var legacy = new PalNaturalMotion(PalNaturalSettings.load(directory), clips());
        for (double time = 0; time <= 64; time += .5) legacy.update(time, WALK, 0, .08, 0);
        // Two whole 32-tick periods, with the rate filter fully settled, is exactly phase 0 again.
        assertEquals(0, Math.min(legacy.phase(), 1 - legacy.phase()), 2e-3,
                "the legacy fixed period is the documented rollback and must still be exact");
        // And the legacy clock ignores the ground speed, which is the fault being rolled back to.
        var slow = new PalNaturalMotion(PalNaturalSettings.load(directory), clips());
        var fast = new PalNaturalMotion(PalNaturalSettings.load(directory), clips());
        for (double time = 0; time <= 60; time += .5) {
            slow.update(time, WALK, 0, .05, 0); fast.update(time, WALK, 0, .25, 0);
        }
        assertEquals(slow.phase(), fast.phase(), 1e-12);
    }

    @Test void aSpeedChangeMovesTheCadenceWithoutSteppingThePhase() throws Exception {
        var engine = engine();
        double previous = 0, worst = 0;
        for (double time = 0; time <= 120; time += .5) {
            double speed = time < 60 ? WALK_SPEED : SPRINT_SPEED;
            engine.update(time, time < 60 ? WALK : RUN, 0, speed, 0);
            double now = engine.phase();
            double delta = now - previous;
            if (delta < 0) delta += 1;
            worst = Math.max(worst, delta);
            previous = now;
        }
        // Half a tick at the fastest shipped cadence is well under a tenth of a cycle; a jump would
        // be a visible teleport of the whole stride.
        final double measured = worst;
        assertTrue(measured < .1, () -> "the phase jumped by " + measured + " of a cycle in half a tick");
    }

    // ---------------------------------------------------------------- stride matching

    private PalNaturalMotion engineWith(String yaml) throws Exception {
        Path folder = Files.createTempDirectory("stride");
        // An absent file deliberately selects defaults; an empty YAML document is not a map.
        if (!yaml.isEmpty()) Files.writeString(folder.resolve("pal-natural-motion.yml"), yaml);
        return new PalNaturalMotion(PalNaturalSettings.load(folder), clips());
    }
    private static double groundPerStep(PalNaturalSettings s, PalAnimationStateSelector.Locomotion g) {
        return (g == WALK ? s.walkCycleBlocks() : g == RUN ? s.runCycleBlocks() : s.fastCycleBlocks()) / 2;
    }
    /** Runs one full gait cycle at a constant speed and hands every sampled pose to the caller. */
    private void sweep(PalNaturalMotion engine, PalAnimationStateSelector.Locomotion gait, double speed,
                       java.util.function.Consumer<double[]> visitor) {
        for (double time = 0; time <= 400; time += .25) {
            assertTrue(engine.update(time, gait, 0, speed, 0));
            if (time > 100) visitor.accept(engine.pose);
        }
    }

    @Test void theSolvedStrideGainMakesOneStepsFootTravelMeetTheGround() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        var engine = engine();
        var clips = clips();
        for (var gait : new PalAnimationStateSelector.Locomotion[] {WALK, RUN, FAST_RUN}) {
            double gain = engine.strideGain(gait);
            double want = groundPerStep(settings, gait);
            var clip = clips.get(gait);
            // The shipped clips are all short of the ground they cover, so every gain is a widening.
            assertTrue(clip.footTravelBlocks(1, settings.maximumLimbSwing()) < want,
                    () -> gait + " no longer needs widening; re-derive the defaults");
            assertTrue(gain > 1 && gain <= settings.maximumStrideGain(), () -> gait + " gain " + gain);
            assertEquals(want, clip.footTravelBlocks(gain, settings.maximumLimbSwing()), 1e-3,
                    () -> gait + " still slips after widening");
        }
    }

    @Test void widenedSwingsStayInsideTheHumanEnvelopeAndNeverInvertAHinge() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        double limit = settings.maximumLimbSwing();
        for (var pair : new Object[][] {{WALK, .21585}, {RUN, .2806}, {FAST_RUN, .2806}}) {
            var gait = (PalAnimationStateSelector.Locomotion) pair[0];
            double speed = (Double) pair[1];
            // Vanilla's own limb swing at this very speed: 1.4 * min(4 * blocks-per-tick, 1).
            double vanilla = 1.4 * Math.min(4 * speed, 1);
            double[] worst = {0};
            sweep(engineWith(""), gait, speed, pose -> {
                for (double v : pose) assertTrue(Double.isFinite(v) && Math.abs(v) <= Math.PI);
                for (int channel : new int[] {4, 8, 12, 16}) {
                    assertTrue(Math.abs(pose[channel]) <= limit + 1e-9, "a limb passed the hard ceiling");
                    worst[0] = Math.max(worst[0], Math.abs(pose[channel]));
                }
                assertTrue(pose[7] <= 1e-9 && pose[11] <= 1e-9, "an elbow inverted");
                assertTrue(pose[15] >= -1e-9 && pose[19] >= -1e-9, "a knee inverted");
            });
            assertTrue(worst[0] < vanilla, () -> gait + " swings wider than Mojang's own model does");
        }
    }

    @Test void aGainOfOneIsABitForBitNoOpSoTheRollbackIsExact() throws Exception {
        // Two ways of asking for no widening must produce the same numbers as each other, to the
        // bit: the flag, and a ceiling that leaves no room to widen.
        var off = engineWith("stride-match: false\n");
        var capped = engineWith("maximum-stride-gain: 1.0\n");
        assertEquals(1d, off.strideGain(WALK));
        assertEquals(1d, capped.strideGain(WALK));
        for (double time = 0; time <= 200; time += .5) {
            assertTrue(off.update(time, WALK, 0, .21585, 0));
            assertTrue(capped.update(time, WALK, 0, .21585, 0));
        }
        assertArrayEquals(off.pose, capped.pose, 0d);
        // And with widening on the walk actually reaches further, which is the whole point.
        var on = engineWith("");
        for (double time = 0; time <= 200; time += .5) assertTrue(on.update(time, WALK, 0, .21585, 0));
        assertTrue(Math.abs(on.pose[12] - on.pose[16]) > Math.abs(off.pose[12] - off.pose[16]));
    }

    @Test void wideningTouchesOnlyTheLimbPitchAndNotTheTorsoOrTheHinges() throws Exception {
        // Both engines keep the body-chain layer on, so the only difference under test is the
        // stride widening itself.
        var off = engineWith("stride-match: false\nbody-chain:\n  enabled: true\n");
        var on = engineWith("body-chain:\n  enabled: true\n");
        for (double time = 0; time <= 200; time += .5) {
            assertTrue(off.update(time, RUN, 0, .2806, 0));
            assertTrue(on.update(time, RUN, 0, .2806, 0));
        }
        // Torso pitch/yaw/roll/bend, both elbows, both knees and the static leg roll are untouched.
        for (int channel : new int[] {0, 1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15, 17, 18, 19}) {
            final int c = channel;
            assertEquals(off.pose[c], on.pose[c], 1e-12, () -> "channel " + c + " must not be widened");
        }
        for (int channel : new int[] {4, 8, 12, 16}) {
            final int c = channel;
            assertNotEquals(off.pose[c], on.pose[c]);
        }
    }

    @Test void theWidenedGaitIsIdenticalAt30_60_144Fps() throws Exception {
        Double reference = null;
        double[] referencePose = null;
        for (int fps : new int[] {30, 60, 144}) {
            var engine = engineWith("");
            double step = 20.0 / fps, wraps = 0, previous = 0;
            for (int frame = 0; frame <= fps * 8; frame++) {
                double time = frame * step;
                double speed = .05 + .18 * Math.sin(Math.min(Math.PI, time / 40));
                assertTrue(engine.update(time, WALK, 0, speed, 0));
                double now = engine.phase();
                if (now < previous) wraps++;
                previous = now;
            }
            double total = wraps + previous;
            if (reference == null) { reference = total; referencePose = engine.pose.clone(); }
            else {
                // Same O(dt^2) quadrature bound as the cadence test: what must not happen is the
                // widened stride scaling with the frame rate.
                assertEquals(reference, total, 1e-3, "the widened cadence changed with the frame rate");
                assertArrayEquals(referencePose, engine.pose, 2e-3, "the widened pose changed with the frame rate");
            }
        }
    }

    @Test void aClipAuthoredWithARealStrideNeedsNoWideningAtAll() throws Exception {
        // The handover property: once the Blender clips are re-authored to cover the ground, the
        // solver returns exactly 1 and this layer takes itself out of the picture. A stale tuning
        // constant could not do that.
        String wide = "{\"emote\":{\"degrees\":false,\"endTick\":16,\"isLoop\":true,\"moves\":["
                + "{\"tick\":0,\"torso\":{},\"rightArm\":{},\"leftArm\":{},\"rightLeg\":{\"pitch\":1.0},\"leftLeg\":{\"pitch\":-1.0}},"
                + "{\"tick\":8,\"torso\":{},\"rightArm\":{},\"leftArm\":{},\"rightLeg\":{\"pitch\":-1.0},\"leftLeg\":{\"pitch\":1.0}},"
                + "{\"tick\":16,\"torso\":{},\"rightArm\":{},\"leftArm\":{},\"rightLeg\":{\"pitch\":1.0},\"leftLeg\":{\"pitch\":-1.0}}]}}";
        var clip = new PalSmoothClip(new StringReader(wide));
        var settings = PalNaturalSettings.load(directory);
        assertTrue(clip.footTravelBlocks(1, settings.maximumLimbSwing()) > groundPerStep(settings, WALK));
        assertEquals(1d, clip.strideGain(groundPerStep(settings, WALK), settings.maximumStrideGain(),
                settings.maximumLimbSwing()));
    }

    @Test void abrokenStrideConfigurationIsRefusedAndABrokenFrameCannotPoisonThePose() throws Exception {
        for (String bad : new String[] {"stride-match-strength: 2.0\n", "maximum-stride-gain: 0.5\n",
                "maximum-limb-swing-degrees: 120\n", "maximum-limb-swing-degrees: .nan\n"}) {
            Path folder = Files.createTempDirectory("stride-bad");
            Files.writeString(folder.resolve("pal-natural-motion.yml"), bad);
            assertThrows(IllegalArgumentException.class, () -> PalNaturalSettings.load(folder));
        }
        // A non-finite request is refused by the solver rather than producing a non-finite gain.
        var clip = clips().get(WALK);
        assertEquals(1d, clip.strideGain(Double.NaN, 3, 1.4));
        assertEquals(1d, clip.strideGain(0, 3, 1.4));
        assertEquals(0d, PalSmoothClip.clampSwing(Double.NaN, 1.4));
        assertEquals(1.4d, PalSmoothClip.clampSwing(99, 1.4));
        // And a broken velocity still resets instead of leaving a widened pose behind.
        var engine = engineWith("");
        for (double time = 0; time <= 40; time += .5) assertTrue(engine.update(time, WALK, 0, .21585, 0));
        assertFalse(engine.update(41, WALK, Double.NaN, 0, 0));
        assertTrue(engine.update(42, IDLE, 0, 0, 0));
        for (double v : engine.pose) assertTrue(Double.isFinite(v) && Math.abs(v) <= Math.PI);
    }

    // ---------------------------------------------------------------- body chain

    private static final int PHASE_SAMPLES = 120;
    /** The channels that are hinges, and the sign each is allowed to take. */
    private static final int[] ELBOWS = {7, 11}, KNEES = {15, 19};
    private PalNaturalMotion chainEngine() throws Exception { return engineWith("body-chain:\n  enabled: true\n"); }
    private PalBodyChain.Settings chainSettings(String yaml) throws Exception {
        Path folder = Files.createTempDirectory("chain-settings");
        if (!yaml.isEmpty()) Files.writeString(folder.resolve("pal-natural-motion.yml"), yaml);
        return PalNaturalSettings.load(folder).bodyChain();
    }
    private PalNaturalMotion plainEngine() throws Exception { return engineWith("body-chain:\n  enabled: false\n"); }

    /**
     * Settles one engine at a constant speed, then hands the visitor exactly one gait cycle's
     * worth of poses at uniform phase. Sampling a whole cycle and no more matters: a window that
     * is 1.27 cycles long double-counts part of the loop and biases every fraction measured here.
     */
    private void cycle(PalNaturalMotion engine, PalAnimationStateSelector.Locomotion gait, double speed,
                       java.util.function.Consumer<double[]> visitor) throws Exception {
        var settings = PalNaturalSettings.load(directory);
        double blocks = gait == WALK ? settings.walkCycleBlocks()
                : gait == RUN ? settings.runCycleBlocks() : settings.fastCycleBlocks();
        double cycleTicks = blocks / speed;
        for (double time = 0; time <= 120; time += .25) engine.update(time, gait, 0, speed, 0);
        for (int i = 1; i <= PHASE_SAMPLES; i++) {
            assertTrue(engine.update(120 + i * cycleTicks / PHASE_SAMPLES, gait, 0, speed, 0));
            visitor.accept(engine.pose);
        }
    }

    @Test void everyJointStaysFiniteAndInsideItsCeilingAcrossAHundredAndTwentySamples() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        double swing = settings.maximumLimbSwing(), yaw = settings.bodyChain().maximumTorsoYaw();
        for (var pair : new Object[][] {{WALK, .21585}, {RUN, .2806}, {FAST_RUN, .2806}}) {
            var gait = (PalAnimationStateSelector.Locomotion) pair[0];
            cycle(chainEngine(), gait, (Double) pair[1], pose -> {
                for (double v : pose) assertTrue(Double.isFinite(v) && Math.abs(v) <= Math.PI);
                for (int channel : new int[] {4, 8, 12, 16}) {
                    assertTrue(Math.abs(pose[channel]) <= swing + 1e-9, "a limb passed the swing ceiling");
                }
                assertTrue(Math.abs(pose[1]) <= yaw + 1e-9, "the torso passed its yaw ceiling");
                for (int channel : ELBOWS) assertTrue(pose[channel] <= 1e-9, "an elbow inverted");
                for (int channel : KNEES) assertTrue(pose[channel] >= -1e-9, "a knee inverted");
            });
        }
    }

    @Test void theChainDelayNeverInvertsTheContralateralDiagonal() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        double totalLag = settings.bodyChain().chestLag() + settings.bodyChain().shoulderLag();
        assertTrue(totalLag > 0, "the shipped chain must actually delay something");
        for (var pair : new Object[][] {{WALK, .21585}, {RUN, .2806}, {FAST_RUN, .2806}}) {
            var gait = (PalAnimationStateSelector.Locomotion) pair[0];
            double[] sum = {0};
            int[] wrong = {0};
            cycle(chainEngine(), gait, (Double) pair[1], pose -> {
                double arms = pose[4] - pose[8], legs = pose[12] - pose[16];
                sum[0] += arms * legs;
                if (arms * legs > 0) wrong[0]++;
            });
            // Right arm and right leg must move OPPOSITE ways. A lag shifts the two zero crossings
            // apart, so a handful of samples around them legitimately read zero or the other sign;
            // what must hold is that the cycle correlation is firmly negative and that the window
            // where it is not is no wider than the delay that caused it.
            assertTrue(sum[0] / PHASE_SAMPLES < -0.01,
                    () -> gait + " lost the contralateral diagonal: mean " + sum[0] / PHASE_SAMPLES);
            // Each waveform crosses zero twice a cycle, so a phase lag of L misaligns the two
            // crossings in TWO windows of width L. Inside them both differences are near zero and
            // the limbs are near neutral, which is why the correlation above is the test that
            // matters; this bound only pins the window to the delay that causes it.
            assertTrue(wrong[0] <= Math.ceil(PHASE_SAMPLES * (2 * totalLag + 0.03)),
                    () -> gait + " broke the diagonal for " + wrong[0] + " of " + PHASE_SAMPLES + " samples");
        }
    }

    /**
     * The composition fault this layer is most able to cause: the standing offset is added to the
     * target, and the blend's `from` is a finished pose that already carries it, so adding it after
     * the blend instead would stack a second copy on every state change.
     */
    @Test void theStandingYawOffsetIsAppliedExactlyOnceThroughEveryTransition() throws Exception {
        var settings = PalNaturalSettings.load(directory);
        double offset = settings.bodyChain().torsoYawOffset();
        assertTrue(offset > 0, "the shipped offset must be non-zero for this test to mean anything");
        // The control keeps the chain - and therefore the chest's phase lag - and differs only in
        // the offset. Comparing against a chain-off engine would also measure the lag.
        var plain = engineWith("body-chain:\n  enabled: true\n  torso-yaw-offset-degrees: 0.0\n");
        var chain = chainEngine();
        var states = new PalAnimationStateSelector.Locomotion[] {
                IDLE, WALK, RUN, FAST_RUN, JUMP_TAKEOFF, JUMP_RISE, JUMP_APEX, JUMP_FALL, JUMP_LAND,
                WALK, IDLE, FAST_RUN, JUMP_TAKEOFF, JUMP_LAND, IDLE};
        double time = 0;
        for (var state : states) {
            for (int step = 0; step < 24; step++, time += .5) {
                double speed = state == IDLE ? 0 : .21585;
                assertTrue(plain.update(time, state, 0, speed, 0));
                assertTrue(chain.update(time, state, 0, speed, 0));
            }
            double measured = chain.pose[1] - plain.pose[1];
            assertEquals(offset, measured, 1e-6,
                    () -> "the offset stacked or vanished after " + state + ": " + measured);
        }
    }

    @Test void theBodyChainIsIdenticalAt30_60_144Fps() throws Exception {
        double[] reference = null;
        for (int fps : new int[] {30, 60, 144}) {
            var engine = chainEngine();
            double step = 20.0 / fps;
            for (int frame = 0; frame <= fps * 8; frame++) engine.update(frame * step, WALK, 0, .21585, 0);
            if (reference == null) reference = engine.pose.clone();
            else assertArrayEquals(reference, engine.pose, 1e-9, "the chain moved with the frame rate");
        }
    }

    @Test void everyLocomotionTransitionStaysContinuousWithTheChainOn() throws Exception {
        // stop<->walk, walk<->run, forward<->backward, forward<->strafe, each at the real cadence.
        // An absolute degrees-per-frame bound would be the wrong test: a sprint leg legitimately
        // sweeps about 23 degrees in half a tick, the same with the chain on and off. What a
        // transition must not do is step MORE than the gait it is leaving or entering already
        // does, so the bound is the measured steady-state step of those same gaits.
        double[][] legs = {
                {0, 0, 0}, {0, .21585, 0}, {0, .2806, 1}, {0, .21585, 0}, {0, 0, 0},
                {0, .21585, 0}, {0, -.21585, 0}, {0, .21585, 0}, {-.21585, 0, 0}, {0, .21585, 0}};
        var engine = chainEngine();
        double time = 0, steady = 0, transition = 0;
        int sinceChange = Integer.MAX_VALUE;
        double[] previous = null;
        for (double[] leg : legs) {
            var gait = leg[2] > 0 ? RUN : (leg[0] == 0 && leg[1] == 0) ? IDLE : WALK;
            sinceChange = 0;
            for (int step = 0; step < 60; step++, time += .5, sinceChange++) {
                assertTrue(engine.update(time, gait, leg[0], leg[1], 0));
                if (previous != null) {
                    for (int c = 0; c < engine.pose.length; c++) {
                        double moved = Math.abs(engine.pose[c] - previous[c]);
                        // The first twenty frames after a change are the transition; the tail of
                        // each segment is that gait running steadily.
                        if (sinceChange < 20) transition = Math.max(transition, moved);
                        else steady = Math.max(steady, moved);
                    }
                }
                previous = engine.pose.clone();
            }
        }
        final double t = transition, s = steady;
        assertTrue(s > 0, "the steady gait must actually move");
        assertTrue(t <= s * 1.25, () -> "a transition stepped " + Math.toDegrees(t)
                + " degrees against a steady " + Math.toDegrees(s));
    }

    @Test void turningOffTheBodyChainRestoresThePreviousPoseBitForBit() throws Exception {
        var off = plainEngine();
        var alsoOff = engineWith("stride-match: true\n");
        assertFalse(chainSettings("body-chain:\n  enabled: false\n").enabled());
        assertFalse(chainSettings("stride-match: true\n").enabled());
        for (double time = 0; time <= 200; time += .5) {
            assertTrue(off.update(time, WALK, 0, .21585, 0));
            assertTrue(alsoOff.update(time, WALK, 0, .21585, 0));
        }
        // Two ways of asking for no chain agree to the bit, and the chain really does change things.
        assertArrayEquals(off.pose, alsoOff.pose, 0d);
        var on = chainEngine();
        for (double time = 0; time <= 200; time += .5) assertTrue(on.update(time, WALK, 0, .21585, 0));
        assertNotEquals(off.pose[1], on.pose[1]);
    }

    @Test void theContactAccentIsZeroMeanSmoothAndTiedToTheClipsOwnContact() throws Exception {
        var clip = clips().get(WALK);
        // The measured contact is where the right foot reaches furthest forward, and the walk clip
        // puts a nearly straight leg there - a heel strike, not an arbitrary phase.
        double[] at = new double[20];
        clip.sample(clip.contactPhase * clip.length, at);
        assertTrue(at[12] < 0, "the contact foot must be forward of the hip");
        assertTrue(Math.abs(at[15]) < Math.toRadians(8), "the contact leg must be nearly straight");
        var settings = chainSettings("body-chain:\n  enabled: true\n");
        double pitchSum = 0, rollSum = 0, worstStep = 0, previousPitch = Double.NaN;
        for (int i = 0; i < 2000; i++) {
            double phase = i / 2000.0;
            double[] out = new double[20];
            PalBodyChain.applyContact(out, phase, clip.contactPhase, settings);
            pitchSum += out[0];
            rollSum += out[2];
            if (!Double.isNaN(previousPitch)) worstStep = Math.max(worstStep, Math.abs(out[0] - previousPitch));
            previousPitch = out[0];
        }
        assertEquals(0d, pitchSum / 2000, 1e-6, "the pitch accent must not add standing lean");
        assertEquals(0d, rollSum / 2000, 1e-9, "the roll accent must be symmetric between the feet");
        assertTrue(worstStep < Math.toRadians(0.2), "the accent must have no corner");
        // Switched off it writes nothing at all.
        double[] none = new double[20];
        PalBodyChain.applyContact(none, .3, clip.contactPhase, PalBodyChain.DISABLED);
        assertArrayEquals(new double[20], none, 0d);
    }

    @Test void aBrokenBodyChainConfigurationIsRefusedRatherThanClamped() throws Exception {
        for (String bad : new String[] {
                "body-chain:\n  enabled: true\n  torso-yaw-offset-degrees: 30\n",
                "body-chain:\n  enabled: true\n  chest-lag-fraction: 0.4\n",
                "body-chain:\n  enabled: true\n  contact-roll-degrees: 9\n",
                "body-chain:\n  enabled: true\n  contact-width-fraction: 0.01\n",
                "body-chain:\n  enabled: true\n  maximum-torso-yaw-degrees: .nan\n",
                // The two delays are each legal but together would approach the half cycle.
                "body-chain:\n  enabled: true\n  chest-lag-fraction: 0.15\n  shoulder-lag-fraction: 0.15\n"}) {
            Path folder = Files.createTempDirectory("chain-bad");
            Files.writeString(folder.resolve("pal-natural-motion.yml"), bad);
            assertThrows(IllegalArgumentException.class, () -> PalNaturalSettings.load(folder));
        }
        assertEquals(0d, PalBodyChain.pulse(Double.NaN, .1));
        assertEquals(0d, PalBodyChain.pulse(.3, 0));
        assertEquals(.25d, PalBodyChain.wrap(-2.75), 1e-12);
    }

    @Test void legacyPalTorsoPitchUsesTheSameSignConversionAsTheLibraryLoader() throws Exception {
        String legacy = "{\"emote\":{\"degrees\":false,\"endTick\":1,\"isLoop\":false,\"moves\":["
                + "{\"tick\":0,\"torso\":{\"pitch\":0.25},\"rightArm\":{},\"leftArm\":{},\"rightLeg\":{},\"leftLeg\":{}},"
                + "{\"tick\":1,\"torso\":{\"pitch\":0.25},\"rightArm\":{},\"leftArm\":{},\"rightLeg\":{},\"leftLeg\":{}}]}}";
        var clip = new PalSmoothClip(new StringReader(legacy));
        double[] pose = new double[20];
        clip.sample(0, pose);
        assertEquals(-0.25, pose[0], 1e-12);
    }
}
