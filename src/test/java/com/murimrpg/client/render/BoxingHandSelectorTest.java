package com.murimrpg.client.render;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoxingHandSelectorTest {
    @Test void swingFlagThenClockSelectsOnlyOnce() {
        var selector = new BoxingHandSelector();
        var calls = new AtomicInteger();
        java.util.function.BooleanSupplier rng = () -> calls.incrementAndGet() % 2 == 0;
        assertFalse(selector.advance(0, true, true, true, rng));
        for (int i = 0; i < 100; i++)
            assertFalse(selector.advance(i / 100f, true, true, true, rng));
        assertEquals(1, calls.get());
        selector.advance(0, false, true, true, rng);
        assertTrue(selector.advance(0, true, true, true, rng));
        assertEquals(2, calls.get());
    }

    @Test void restartedSwingChoosesAgainButDuplicateRenderDoesNot() {
        var selector = new BoxingHandSelector();
        var calls = new AtomicInteger();
        java.util.function.BooleanSupplier rng = () -> calls.incrementAndGet() % 2 == 0;
        selector.advance(.7f, true, true, true, rng);
        assertTrue(selector.advance(.1f, true, true, true, rng));
        assertTrue(selector.advance(.1f, true, true, true, rng));
        assertEquals(2, calls.get());
    }

    @Test void armedOrDisabledPreservesActualHandWithoutRandomCalls() {
        var selector = new BoxingHandSelector();
        assertFalse(selector.advance(.4f, true, false, false, () -> { fail(); return true; }));
        assertTrue(selector.advance(.5f, true, false, true, () -> { fail(); return false; }));
    }

    @Test void restartAtZeroIsLatchedBeforeTheClockAdvances() {
        var selector = new BoxingHandSelector();
        var calls = new AtomicInteger();
        java.util.function.BooleanSupplier rng = () -> calls.incrementAndGet() % 2 == 0;
        selector.advance(.6f, true, true, true, rng);
        assertTrue(selector.advance(0f, true, true, true, rng));
        assertTrue(selector.advance(.1f, true, true, true, rng));
        assertEquals(2, calls.get());
    }
}
