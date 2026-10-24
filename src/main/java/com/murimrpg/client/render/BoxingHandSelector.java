package com.murimrpg.client.render;

import java.util.function.BooleanSupplier;

/** Visual hand choice, latched once per swing; never changes the gameplay hand. */
final class BoxingHandSelector {
    private boolean active;
    private float previous;
    private boolean right = true;

    boolean advance(float phase, boolean swinging, boolean enabled, boolean actualRight,
                    BooleanSupplier random) {
        float time = Float.isFinite(phase) ? Math.clamp(phase, 0f, 1f) : 0f;
        boolean now = swinging || time > 0f;
        // A swinging edge can precede attackTime. Do not choose again when the clock starts.
        if (!enabled) right = actualRight;
        else if (now && (!active || (previous > 0f && (time > 0f || swinging)
                && ArticulatedCombatPose.swingRestarted(previous, time)))) right = random.getAsBoolean();
        active = now;
        previous = time;
        return right;
    }

    void reset() { active = false; previous = 0f; }
}
