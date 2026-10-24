package com.murimrpg.client;

import com.murimrpg.infrastructure.config.YamlDocument;

/**
 * The part of a walk that is not in the arms and legs: where the chest points, when it turns
 * relative to the feet, and what the body does at the instant a foot takes the load.
 *
 * <h2>What problem this solves</h2>
 * The nine authored clips already carry a correct gait - the loader proves the left/right mirror
 * to 1e-16 and the contralateral arm/leg phase at every sample. What they do not carry is a body
 * that passes force along itself. Every channel in a clip is read at ONE phase, so the chest turns
 * on exactly the same beat as the feet and the shoulders on exactly the same beat as the chest.
 * Nothing leads and nothing trails, which is what reads as "limbs on a sine wave" however good the
 * individual curves are.
 *
 * <h2>What this layer can and cannot do on this rig</h2>
 * Honest limits first, because two of the things usually asked for here are not representable.
 * <ul>
 *   <li>There is NO pelvis bone. The rig PAL exposes is {@code torso, right_arm, left_arm,
 *       right_leg, left_leg}, and the legs are SIBLINGS of the torso, not its children - exactly
 *       Mojang's own hierarchy. A true pelvis-against-ribcage counter-rotation therefore cannot be
 *       expressed: there is no joint between them to rotate. What can be expressed is the torso
 *       turning against the LEGS, which is the same visible relationship seen from outside, minus
 *       the waist twist in the middle.</li>
 *   <li>There is no IK and no foot lock, and root translation is out of scope, so a stance foot is
 *       held by matching its swing to the ground it covers (the existing stride match), never by
 *       pinning it.</li>
 * </ul>
 *
 * <h2>The three things it does do</h2>
 * <ol>
 *   <li><b>A standing orientation.</b> A constant yaw offset on the torso, in the player's own
 *       local frame, so the chest is not square to the camera. PAL disables the head's rotation
 *       channels outright ({@code setPostAnimationSetupConsumer}), so the head keeps Mojang's mouse
 *       look whatever the torso does and the aim cannot be dragged.</li>
 *   <li><b>A chain delay.</b> Instead of inventing a second oscillator, the SAME clip is read at
 *       three phases: the legs at the gait phase, the torso a little behind them, the arms a
 *       little behind the torso. Each group keeps its own authored waveform, so the left/right
 *       mirror inside a group and the hinge signs are untouched; only the timing between groups
 *       changes. That is what makes the body look like it is transmitting a step rather than
 *       playing four loops at once.</li>
 *   <li><b>Weight acceptance.</b> A short, smooth pulse at each foot contact - a small pitch dip
 *       on both contacts and a small roll that alternates with the support side. The contact
 *       instant is measured off the clip ({@link PalSmoothClip#contactPhase}), not configured, so
 *       it follows the curve if the curve is re-authored.</li>
 * </ol>
 *
 * <p>The pitch pulse is made zero-mean on purpose: a pulse that is always positive would quietly
 * add forward lean on top of the run lean and nobody would see where it came from.
 *
 * <p>Nothing here is taken from any other mod: no code, no constants, no keyframes, no rig.
 */
final class PalBodyChain {
    private PalBodyChain() { }

    /** The chain delays together may never approach the half cycle that separates the two legs. */
    static final double MAXIMUM_TOTAL_LAG = 0.20;

    /**
     * A smooth periodic pulse: 1 at the contact, 0 beyond {@code width} either side, with zero
     * slope at both edges so it joins the rest of the cycle without a corner.
     *
     * @param offset phase minus the contact phase; wrapped, so it works across the loop seam
     */
    static double pulse(double offset, double width) {
        if (!Double.isFinite(offset) || !Double.isFinite(width) || width <= 0) return 0;
        double wrapped = offset - Math.floor(offset + 0.5);
        if (Math.abs(wrapped) >= width) return 0;
        double cosine = Math.cos(Math.PI * wrapped / (2 * width));
        return cosine * cosine;
    }

    /** The cycle average of one {@link #pulse}, so a pulse can be made to add no standing bias. */
    static double pulseMean(double width) {
        return Double.isFinite(width) && width > 0 ? Math.min(width, 0.5) : 0;
    }

    /**
     * Adds the weight-acceptance accent for both feet to a sampled gait pose.
     *
     * @param out          the pose being built; only torso pitch and torso roll are touched
     * @param phase        the gait phase, 0..1
     * @param contactPhase the clip's own right-foot contact, 0..1
     */
    static void applyContact(double[] out, double phase, double contactPhase, Settings settings) {
        if (out == null || out.length < 4 || settings == null || !settings.enabled()) return;
        double width = settings.contactWidth();
        double right = pulse(phase - contactPhase, width);
        double left = pulse(phase - contactPhase - 0.5, width);
        // Both feet dip the chest; only the roll knows which foot it was.
        out[0] += settings.contactPitch() * (right + left - 2 * pulseMean(width));
        out[2] += settings.contactRoll() * (right - left);
    }

    /** The phase each body group reads, given the gait phase. Legs lead, arms trail. */
    static double chestPhase(double phase, Settings settings) {
        if (settings == null || !settings.enabled()) return phase;
        return wrap(phase - settings.chestLag());
    }
    static double armPhase(double phase, Settings settings) {
        if (settings == null || !settings.enabled()) return phase;
        return wrap(phase - settings.chestLag() - settings.shoulderLag());
    }
    static double wrap(double phase) {
        if (!Double.isFinite(phase)) return 0;
        return phase - Math.floor(phase);
    }

    /**
     * The standing orientation, applied to the finished torso yaw.
     *
     * <p>Sign, from this rig's axes: the right arm's pivot is at x = -5, and under a rotation about
     * Y a point at negative x moves toward +z, which is behind. A POSITIVE torso yaw therefore
     * carries the right shoulder back and the left shoulder forward, which turns the chest to the
     * player's right - clockwise seen from above. That is the asked-for offset, so it is positive.
     *
     * @return the torso yaw with the offset added, clamped to the hard ceiling
     */
    static double standingYaw(double torsoYaw, Settings settings) {
        if (settings == null || !settings.enabled()) return torsoYaw;
        double total = torsoYaw + settings.torsoYawOffset();
        if (!Double.isFinite(total)) return torsoYaw;
        double limit = settings.maximumTorsoYaw();
        return Math.clamp(total, -limit, limit);
    }

    // ------------------------------------------------------------------ settings

    /** Angles are radians once loaded; lags and widths are fractions of one gait cycle. */
    record Settings(boolean enabled, double torsoYawOffset, double chestLag, double shoulderLag,
                    double contactPitch, double contactRoll, double contactWidth,
                    double maximumTorsoYaw) { }

    static final Settings DISABLED = new Settings(false, 0, 0, 0, 0, 0, 0.12, Math.toRadians(15));

    static Settings load(YamlDocument yaml) {
        if (!Boolean.TRUE.equals(yaml.get("body-chain.enabled"))) return DISABLED;
        double chest = range(yaml, "chest-lag-fraction", .04, 0, .15);
        double shoulder = range(yaml, "shoulder-lag-fraction", .05, 0, .15);
        if (chest + shoulder > MAXIMUM_TOTAL_LAG) {
            throw new IllegalArgumentException("Invalid pal-natural-motion.yml: body-chain"
                    + " chest-lag-fraction + shoulder-lag-fraction must not exceed " + MAXIMUM_TOTAL_LAG);
        }
        return new Settings(true,
                Math.toRadians(range(yaml, "torso-yaw-offset-degrees", 4, 0, 12)),
                chest, shoulder,
                Math.toRadians(range(yaml, "contact-pitch-degrees", 1.2, 0, 4)),
                Math.toRadians(range(yaml, "contact-roll-degrees", .8, 0, 3)),
                range(yaml, "contact-width-fraction", .12, .04, .25),
                Math.toRadians(range(yaml, "maximum-torso-yaw-degrees", 15, 5, 25)));
    }

    private static double range(YamlDocument yaml, String key, double fallback, double min, double max) {
        double value = yaml.decimal("body-chain." + key, fallback);
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Invalid pal-natural-motion.yml: body-chain." + key);
        }
        return value;
    }
}
