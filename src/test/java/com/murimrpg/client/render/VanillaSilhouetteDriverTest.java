package com.murimrpg.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The state the vanilla-silhouette layer keeps: the fade, the flight clock and the take-off stride. */
class VanillaSilhouetteDriverTest {
    private static final Path SETTINGS = Path.of("src/main/resources/articulated-player.yml");

    @Test void theFadeReachesOneAndComesBackToZeroWithoutOvershooting() {
        var driver = new VanillaSilhouetteDriver();
        float age = 0f, previous = 0f;
        for (int tick = 0; tick < 12; tick++) {
            age += 1f;
            float weight = driver.advanceSupport(age, true, 4f);
            assertTrue(weight >= previous - 1e-6f, "the fade in must not go backwards");
            assertTrue(weight <= 1f + 1e-6f, "the fade must not overshoot");
            previous = weight;
        }
        assertEquals(1f, driver.supportWeight(), 1e-6f, "the fade must arrive");
        for (int tick = 0; tick < 12; tick++) {
            age += 1f;
            float weight = driver.advanceSupport(age, false, 4f);
            assertTrue(weight >= -1e-6f, "the fade out must not undershoot");
        }
        assertEquals(0f, driver.supportWeight(), 1e-6f, "leaving a posed state must reach exactly vanilla");
    }

    @Test void aPauseOrATeleportCannotAdvanceTheFadeOrTheFlightClock() {
        var driver = new VanillaSilhouetteDriver();
        driver.advanceSupport(0f, true, 4f);
        float afterHugeGap = driver.advanceSupport(9_000f, true, 4f);
        assertEquals(0f, afterHugeGap, 1e-6f, "an impossible tick step must not move the fade");
        driver.noteGround(true, 0.5f, -0.5f);
        assertEquals(0f, driver.airborneProgress(10f), 1e-6f, "nor the flight clock");
        driver.advanceSupport(Float.NaN, true, 4f);
        assertEquals(1f, driver.supportWeight(), 0f, "a broken clock lands on the target instead of drifting");
        // And an ordinary tick still moves it, so holding still is not the same as being stuck.
        var moving = new VanillaSilhouetteDriver();
        moving.advanceSupport(100f, true, 4f);
        assertTrue(moving.advanceSupport(101f, true, 4f) > 0f, "a normal tick must still fade");
    }

    @Test void theTakeOffStrideIsCapturedOnceAndTheFlightClockRuns() {
        var driver = new VanillaSilhouetteDriver();
        float age = 0f;
        driver.advanceSupport(age, true, 1f);
        driver.noteGround(false, 0.1f, -0.1f);
        age += 1f; driver.advanceSupport(age, true, 1f);
        driver.noteGround(true, 0.42f, -0.31f);
        assertEquals(0.42f, driver.takeOffRightLeg(), 0f, "the take-off stride must be the frame the feet left");
        assertEquals(-0.31f, driver.takeOffLeftLeg(), 0f);
        assertTrue(driver.holdingTakeOff());
        for (int tick = 0; tick < 5; tick++) {
            age += 1f;
            driver.advanceSupport(age, true, 1f);
            driver.noteGround(true, 9f, -9f);
        }
        assertEquals(0.42f, driver.takeOffRightLeg(), 0f, "it must not be recaptured mid flight");
        assertTrue(driver.airborneProgress(10f) > 0f, "the flight clock must run");
        assertTrue(driver.airborneProgress(10f) < 1f, "and not finish early");
        age += 1f; driver.advanceSupport(age, true, 1f); driver.noteGround(false, 0f, 0f);
        assertEquals(0f, driver.airborneProgress(10f), 0f, "landing resets the flight clock");
    }

    @Test void thePhaseWrapsAndTheRunWeightEasesInsteadOfSwapping() {
        var driver = new VanillaSilhouetteDriver();
        float previous = driver.advancePhase(0f, 1f, false);
        for (int tick = 1; tick <= 400; tick++) {
            float phase = driver.advancePhase(tick * 0.9f, 1f, false);
            assertTrue(Float.isFinite(phase) && phase >= 0f && phase < (float) (Math.PI * 2d) + 1e-4f,
                    "the phase must stay inside one turn");
            previous = phase;
        }
        assertTrue(Float.isFinite(previous));
        float age = 0f, weight = 0f;
        for (int tick = 0; tick < 3; tick++) {
            age += 1f;
            float next = driver.advanceRunWeight(age, true, 3);
            assertTrue(next >= weight - 1e-6f, "the walk to run blend must not go backwards");
            weight = next;
        }
        assertTrue(weight > 0f && weight <= 1f, "the blend must move but stay bounded");
    }

    @Test void everyShippedValueIsInsideTheRangeItsLoaderAccepts() throws IOException {
        String yaml = Files.readString(SETTINGS);
        int start = yaml.indexOf("vanilla-silhouette:");
        String block = yaml.substring(start, yaml.indexOf("\ndiagnostics:", start));
        record Range(String key, double minimum, double maximum) { }
        var ranges = new Range[] {
            new Range("fade-ticks", 0d, 20d), new Range("stride-scale", .5d, 1.6d),
            new Range("arm-stride-scale", .5d, 1.6d),
            new Range("limb-ceiling-degrees", 0d, VanillaSilhouettePose.MAXIMUM_LIMB_DEGREES),
            new Range("walk-lean-degrees", 0d, VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES),
            new Range("run-lean-degrees", 0d, VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES),
            new Range("shoulder-yaw-degrees", 0d, VanillaSilhouettePose.MAXIMUM_YAW_DEGREES),
            new Range("shoulder-yaw-gain", 0d, 1d), new Range("cadence-scale", .5d, 1.5d),
            new Range("fade-speed", .02d, .5d), new Range("lean-degrees", 0d, VanillaSilhouettePose.MAXIMUM_LEAN_DEGREES),
            new Range("arm-spread-degrees", 0d, 6d), new Range("arm-pitch-degrees", 0d, 10d),
            new Range("breath-degrees", 0d, 1.5d), new Range("breath-seconds", 2d, 8d),
            new Range("hold-fraction", 0d, 1d), new Range("lean-degrees-per-block", 0d, 60d),
            new Range("roll-degrees-per-block", 0d, 60d),
            new Range("roll-degrees", 0d, VanillaSilhouettePose.MAXIMUM_ROLL_DEGREES),
        };
        for (Range range : ranges) {
            int at = block.indexOf("\n    " + range.key() + ":");
            if (at < 0) at = block.indexOf("\n  " + range.key() + ":");
            assertTrue(at >= 0, () -> "the block must ship " + range.key());
            int end = block.indexOf('\n', at + 1);
            String line = block.substring(at + 1, end < 0 ? block.length() : end);
            double value = Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
            assertTrue(value >= range.minimum() && value <= range.maximum(),
                    () -> range.key() + " ships " + value + ", outside " + range.minimum() + ".." + range.maximum());
        }
    }
}
