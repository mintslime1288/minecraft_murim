package com.murimrpg.client.render;

/**
 * A short weight-acceptance accent on the torso roll, once per foot, while running on the ground.
 *
 * <h2>Where the contacts are, measured rather than assumed</h2>
 * murim_run_v3_torso.json is a 17-sample, 16-frame loop. The two legs are exactly half a cycle
 * apart: {@code thigh.R[f]} and {@code thigh.L[f + 8]} differ by 0.000006 degrees across the whole
 * loop, and the same holds for the shins. So there is one contact per half cycle and their spacing
 * is not a design choice, it is a property of the clip.
 *
 * <p>Which extreme of the thigh sweep is the foot strike follows from the rig's own hinge
 * directions, and nothing else is needed:
 *
 * <ul>
 *   <li>{@code shin} is positive in every frame of every clip, 3.000 to 26.000 in the run and
 *       3.000 to 36.000 in the walk, and {@code AuthoredLocomotionClipTest} pins that. A knee only
 *       folds one way, so a positive rotation on a downward-hanging limb carries its lower end
 *       BEHIND the joint.</li>
 *   <li>{@code lower_arm} is negative in every frame of every clip, -12.000 to -3.000 in the run
 *       and -26.000 to -14.000 in the walk. An elbow folds the other way from a knee, so a negative
 *       rotation carries the lower end IN FRONT. The two agree.</li>
 * </ul>
 *
 * Therefore the thigh's most NEGATIVE value is the leg reaching furthest forward, and the instant
 * it stops reaching and begins sweeping back is the foot strike. Measured on the run clip after the
 * live gains and ceilings ({@code upper-leg-gain 1.20}, {@code run.stride-degrees 42}):
 *
 * <pre>
 *   thigh.R minimum  -31.200 deg at frame 0    -> RIGHT contact, phase 0
 *   thigh.L minimum  -31.200 deg at frame 8    -> LEFT  contact, phase PI
 * </pre>
 *
 * <p>Cross-check on foot height, {@code 6 cos(thigh) + 6 cos(thigh + shin)} below the hip: the
 * right foot is the lower of the two across frames 13 through 4, the left across 5 through 12, and
 * each thigh minimum falls inside its own leg's window. At frame 0 the right foot is 10.848 px down
 * and the left 9.290 px, so the striking foot is the lower one. The other extreme would put the
 * raised foot on the ground.
 *
 * <p>One honest caveat, since it decides how this class is written: a rotation-only rig with a
 * fixed hip height has no ground plane. Both feet reach exactly the same greatest depth, 11.984 px,
 * and neither holds still while the other swings. So the thigh reversal is a measured SURROGATE for
 * touchdown, not an observation of a foot meeting a floor, and there is no point pretending to a
 * precision finer than the clip's own 1/16 of a cycle.
 *
 * <p>Note for whoever reads {@code AuthoredLocomotionClipTest} next: its
 * {@code theDeepestKneeFoldBelongsToTheSwingHalf} names the OPPOSITE extreme {@code contact}. By
 * the hinge argument above that naming is backwards. It is left exactly as it is by this change -
 * it is a guard on the clips, it still passes, and quietly editing an existing guard to agree with
 * a new claim is not evidence.
 *
 * <h2>The shape</h2>
 * A sine on this axis is what the clip already authors: {@code body.zRot} runs -3.000 at frame 0 up
 * to +3.000 at frame 8 and back, a smooth round sway with its extremes exactly at the two contacts.
 * Adding more sine would only be a larger sway. What a landing looks like instead is a fast rise
 * and a decay that is over well before the next step, so the envelope here is zero at the contact
 * frame itself, reaches one an eighth of a half cycle later, and is back to exactly zero at the
 * halfway point of the half cycle - one frame and four frames of this clip's eight. Both segments
 * are smoothstepped, so value and slope are continuous everywhere, including across the loop seam
 * where the envelope is zero on both sides.
 *
 * <p>Nothing here is derived from any third-party mod, model, rig or animation. The class is a pure
 * function of the phase: it holds no state, so a teleport, a pause, a phase wrap or a reversal has
 * nothing to corrupt and nothing to reset.
 */
final class RunContactAccent {
    private RunContactAccent() { }

    /** Gait phase of the right and left foot strikes, measured on murim_run_v3_torso.json. */
    static final float RIGHT_CONTACT_PHASE = 0f;
    static final float LEFT_CONTACT_PHASE = (float) Math.PI;

    /** Fraction of a half cycle from the contact to the peak: one frame of the clip's eight. */
    static final float RISE_FRACTION = 0.125f;
    /** Fraction of a half cycle at which the accent is back to exactly zero: four frames of eight. */
    static final float DECAY_FRACTION = 0.5f;

    /**
     * Hard ceiling on the accent regardless of YAML. Past this it stops being an accent on the
     * authored 3.000 degree roll and becomes a pose of its own.
     */
    static final float MAXIMUM_DEGREES = 2f;

    /**
     * Signed weight of the accent at this phase, exactly -1 to 1.
     *
     * <p>Negative over the right foot's window and positive over the left's, which is the same sign
     * the clip's own roll already has at each contact: at frame 0 the authored roll is -3.000 and
     * the torso is tipped toward the right, the side that is landing. The accent presses a little
     * further that way while the weight arrives, instead of letting the sway turn back immediately.
     *
     * @return 0 for a non-finite phase, and 0 over the second half of every half cycle
     */
    static float envelope(float phase) {
        if (!Float.isFinite(phase)) return 0f;
        float halfCycles = phase / LEFT_CONTACT_PHASE;
        float completed = (float) Math.floor(halfCycles);
        if (!Float.isFinite(completed)) return 0f;
        float sinceContact = halfCycles - completed;
        float weight = shape(sinceContact);
        if (weight == 0f) return 0f;
        // An even number of half cycles since phase zero means the right foot landed last.
        boolean rightFoot = (((long) completed) & 1L) == 0L;
        return rightFoot ? -weight : weight;
    }

    /**
     * The accent to add to {@code body.zRot}, in radians.
     *
     * @param amplitudeRadians already scaled by runWeight and by the gait speed; a value of zero,
     *                         a negative value or a non-finite one turns the layer off entirely
     * @return 0 whenever the layer is off or outside a contact window; never larger in magnitude
     *         than {@code amplitudeRadians}
     */
    static float rollRadians(float phase, float amplitudeRadians) {
        if (!Float.isFinite(amplitudeRadians) || amplitudeRadians <= 0f) return 0f;
        float accent = amplitudeRadians * envelope(phase);
        return Float.isFinite(accent) ? accent : 0f;
    }

    /** Zero at the contact, one at RISE_FRACTION, zero again from DECAY_FRACTION onwards. */
    private static float shape(float sinceContact) {
        if (!(sinceContact > 0f) || sinceContact >= DECAY_FRACTION) return 0f;
        if (sinceContact < RISE_FRACTION) return smoothstep(sinceContact / RISE_FRACTION);
        return 1f - smoothstep((sinceContact - RISE_FRACTION) / (DECAY_FRACTION - RISE_FRACTION));
    }

    private static float smoothstep(float amount) {
        float value = Math.clamp(amount, 0f, 1f);
        return value * value * (3f - 2f * value);
    }
}
