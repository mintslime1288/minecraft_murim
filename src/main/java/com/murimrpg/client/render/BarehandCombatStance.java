package com.murimrpg.client.render;

import com.murimrpg.infrastructure.config.YamlDocument;

/**
 * A fighting guard the character drops into after a bare-handed attack and holds for a few seconds.
 *
 * <h2>What this is NOT</h2>
 * It does not slow the punch down. {@code AvatarRenderState.attackTime} is
 * {@code swingTime / currentSwingDuration} straight off the entity, so stretching it would put the
 * animation out of step with the hit that already happened on the server. The strike stays exactly
 * as fast as the game says it is, and it can be turned off on its own with
 * {@code combat-motion.barehand.enabled: false}; what this block adds is what happens AFTER the
 * swing, which is where the missing weight actually was.
 *
 * <h2>How a swing is detected, and what else swings</h2>
 * {@code attackTime} is exactly 0 at rest and becomes positive on the first frame of a swing, so
 * the 0 -> positive edge is the start. A second attack pressed before the first ends resets
 * {@code swingTime} to 0, which shows up as the clock going BACKWARDS; that is the other edge.
 * Neither can fire twice inside one swing, so the many render frames of a single swing cannot keep
 * pushing the timer out.
 *
 * <p>Reported honestly rather than discovered later: {@code LivingEntity.swing} is what sets
 * {@code swinging}, and mining a block calls it too. So breaking a block WITH AN EMPTY HAND raises
 * this guard exactly as a punch does - the render state cannot tell them apart, because both are
 * the same swing. Mining with any tool does not, because the hand is not empty. Whether that is
 * wanted is an F5 judgement, and turning the block off is one flag.
 *
 * <h2>Why the timer is in ticks and not in frames</h2>
 * The hold counts down by the difference between two {@code ageInTicks} readings, so the sum of the
 * steps across any number of frames is exactly the elapsed tick count. Two clients at different
 * frame rates reach the same weight at the same tick. Nothing integrates a rate, so nothing drifts.
 *
 * <p>Nothing here comes from any other mod: no code, no constants, no keyframes, no rig.
 */
final class BarehandCombatStance {
    private BarehandCombatStance() { }

    /** Slots of the additive delta, in radians. Front is the lead side, rear the attacking side. */
    static final int BODY_X = 0, BODY_Y = 1;
    static final int FRONT_UPPER_X = 2, FRONT_UPPER_Z = 3, FRONT_LOWER_X = 4;
    static final int REAR_UPPER_X = 5, REAR_UPPER_Z = 6, REAR_LOWER_X = 7;
    static final int FRONT_LEG_X = 8, REAR_LEG_X = 9, FRONT_KNEE_X = 10, REAR_KNEE_X = 11;
    static final int VALUE_COUNT = 12;

    /** Hard ceilings regardless of YAML. */
    static final float MAXIMUM_BODY_PITCH_DEGREES = 8f;
    static final float MAXIMUM_BODY_YAW_DEGREES = 12f;
    static final float MAXIMUM_UPPER_ARM_DEGREES = 45f;
    static final float MAXIMUM_ELBOW_DEGREES = 135f;
    static final float MAXIMUM_ARM_ROLL_DEGREES = 12f;
    static final float MAXIMUM_LEG_DEGREES = 14f;
    static final float MAXIMUM_KNEE_DEGREES = 20f;
    static final float MAXIMUM_BREATH_DEGREES = 0.6f;
    /** A tick step larger than this is a teleport, a pause or a world change: hold still. */
    static final float MAXIMUM_STEP_TICKS = 8f;

    /**
     * How much of the guard the strike is allowed to push aside, per body part, while it owns the
     * upper body. The attacking arm gives way completely, because a fist folded to the guard's 130
     * degree elbow is not a punch. The torso gives way half: the guard's bladed yaw and the
     * strike's weight transfer are the same axis and want the same direction, so the strike only
     * has to take the part it disagrees with. The off arm gives way almost not at all, which is
     * what keeps the other hand covering while the punch goes out.
     */
    static final float STRIKE_BODY_SHARE = 0.5f;
    static final float STRIKE_FRONT_SHARE = 0.15f;

    static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    static float unit(float value) { return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f; }

    static float limited(float value, float ceilingDegrees, float hardCeilingDegrees) {
        if (!Float.isFinite(value)) return 0f;
        float ceiling = radians(Math.min(Math.abs(ceilingDegrees), Math.abs(hardCeilingDegrees)));
        return Math.clamp(value, -ceiling, ceiling);
    }

    /** Bounded step toward a target; the result is always between the two, so it cannot run away. */
    static float approach(float current, float target, float maximumStep) {
        if (!Float.isFinite(current)) return Float.isFinite(target) ? target : 0f;
        if (!Float.isFinite(target)) return current;
        if (!Float.isFinite(maximumStep) || maximumStep <= 0f) return target;
        float delta = target - current;
        if (delta > maximumStep) return current + maximumStep;
        if (delta < -maximumStep) return current - maximumStep;
        return target;
    }

    /** Smooth at both ends, so entering and leaving the guard has no corner. */
    static float easeInOut(float amount) {
        float value = unit(amount);
        return value * value * (3f - 2f * value);
    }

    // ------------------------------------------------------------------ the timer

    /**
     * The hold clock and the fade. One instance belongs to one model, which belongs to the local
     * player, so no state is shared with any other entity.
     */
    static final class Tracker {
        private float previousAttackTime;
        private float holdTicksLeft;
        private float rawWeight;
        private float lastAge = Float.NaN;
        private boolean rearIsRight = true;
        private boolean latched;
        private boolean previousSwingActive;

        void reset() {
            previousAttackTime = 0f;
            holdTicksLeft = 0f;
            rawWeight = 0f;
            lastAge = Float.NaN;
            latched = false;
            previousSwingActive = false;
        }

        float weight() { return rawWeight; }
        /**
         * The same weight the last {@link #advance} returned. The strike layer runs before the
         * guard layer in one frame, so this is how it sees how much guard is up: one render frame
         * old, which at any frame rate is less than a tick and is bounded by the blend windows.
         */
        float easedWeight() { return easeInOut(rawWeight); }
        float holdTicksLeft() { return holdTicksLeft; }
        /** Which side is the power hand; latched when the guard is raised so it cannot flicker. */
        boolean rearIsRight() { return rearIsRight; }

        /**
         * @param attackTime      the render state's value, 0 at rest and 0..1 while swinging
         * @param barehandAttack  the attacking hand is empty; an armed swing must not raise this
         * @param supported       a state this guard is allowed in at all
         * @param targetWeight    how much guard this locomotion state wants: 1 standing, less
         *                        walking, 0 sprinting or airborne
         * @return the eased weight, 0..1
         */
        float advance(float ageInTicks, float attackTime, boolean barehandAttack, boolean supported,
                      float holdTicks, float blendInTicks, float blendOutTicks, float targetWeight) {
            return advance(ageInTicks, attackTime, attackTime > 0f, barehandAttack, supported,
                    holdTicks, blendInTicks, blendOutTicks, targetWeight);
        }

        /**
         * Runtime form of {@link #advance(float, float, boolean, boolean, float, float, float, float)}.
         * The entity swing flag is carried separately because the interpolated render clock can be
         * zero on the first visible swing frame. Either edge may start the guard; both are latched,
         * so observing both in adjacent frames still produces one hold rather than extending it.
         */
        float advance(float ageInTicks, float attackTime, boolean swingActive,
                      boolean barehandAttack, boolean supported,
                      float holdTicks, float blendInTicks, float blendOutTicks, float targetWeight) {
            float now = Float.isFinite(attackTime) ? attackTime : 0f;
            float delta = Float.isNaN(lastAge) || !Float.isFinite(ageInTicks) ? 0f : ageInTicks - lastAge;
            if (Float.isFinite(ageInTicks)) lastAge = ageInTicks;
            if (!(delta >= 0f) || delta > MAXIMUM_STEP_TICKS) delta = 0f;

            // If the entity edge was already seen on the preceding zero-clock frame, this is the
            // same swing becoming visible through interpolation, not a second trigger.
            boolean clockStarted = previousAttackTime <= 0f && now > 0f && !previousSwingActive;
            boolean entityStarted = !previousSwingActive && swingActive;
            // A swing ENDING also drops attackTime to 0, and that is not a new attack. A restart is
            // the clock going back to a value that is still inside a swing, which is what a second
            // press looks like on a frame that missed the single zero tick in between.
            boolean restarted = previousAttackTime > 0f && now > 0f && now < previousAttackTime - 0.01f;
            previousAttackTime = now;
            previousSwingActive = swingActive;

            if ((clockStarted || entityStarted || restarted) && barehandAttack && supported) {
                holdTicksLeft = Float.isFinite(holdTicks) ? Math.max(0f, holdTicks) : 0f;
                if (!latched || rawWeight <= 0f) latched = true;
            } else {
                holdTicksLeft = Math.max(0f, holdTicksLeft - delta);
            }
            if (!supported) holdTicksLeft = 0f;

            float target = supported && holdTicksLeft > 0f ? unit(targetWeight) : 0f;
            float window = target > rawWeight ? blendInTicks : blendOutTicks;
            boolean instant = !Float.isFinite(window) || window <= 0f;
            // No time passed, or a step so large it can only be a teleport or a pause: hold still.
            // Moving here would let one impossible frame jump the whole blend.
            if (instant) rawWeight = unit(target);
            else if (delta > 0f) rawWeight = unit(approach(rawWeight, target, delta / window));
            if (rawWeight <= 0f) latched = false;
            return easeInOut(rawWeight);
        }

        /** Latches the power hand the first frame the guard goes up, so the hands cannot swap. */
        void latchSide(boolean attackerIsRight) {
            if (!latched || rawWeight <= 0f) rearIsRight = attackerIsRight;
        }
    }

    // ------------------------------------------------------------------ the pose

    /**
     * Fills {@code out} with the rotations to ADD to whatever the locomotion and strike layers left.
     *
     * <p>Signs, from the convention read out of the 26.2 jar ({@code rotationZYX}) and this model's
     * axes: a hanging limb's lower end goes to +z for a positive xRot and +z is behind, so a NEGATIVE
     * upper-arm pitch brings the hand forward and a NEGATIVE elbow folds it the only way an elbow
     * folds. The right arm's pivot is x = -5, so a POSITIVE zRot swings that hand outward and a
     * negative one tucks it in; the left arm is the mirror. A positive body yaw carries the right
     * shoulder back, so a right-handed fighter standing bladed - lead shoulder toward the target -
     * is a POSITIVE yaw.
     *
     * <p>Measured with the shipped angles, from the shoulder pivot at root y = 2:
     * the lead hand sits 5.92 px forward and at root y 3.42, the rear hand 4.21 px forward at root
     * y 3.21. The head box spans root y -8..0, so both hands are at chest height and BELOW the
     * face, which is what "guards the chest and chin without covering the face" has to mean on a
     * model whose head is a solid cube.
     *
     * <p>The legs get a small ready stance only while standing. Bending a knee with no root
     * translation lifts the foot, so the numbers are chosen to keep that invisible: at the shipped
     * 10 degree knee and 6 degree hip the foot rises 0.047 px, and the hard ceilings cap it at
     * 0.495 px. Nothing here writes an offset or a scale.
     *
     * @param weight        0..1 from the tracker, already eased
     * @param groundedIdle  how much of the leg readiness applies: 1 standing still, 0 once moving
     * @param rearIsRight   the latched power side
     * @return false when nothing should be added
     */
    static boolean pose(Settings settings, float weight, float groundedIdle, boolean rearIsRight,
                        float ageInTicks, float[] out) {
        if (settings == null || out == null || out.length < VALUE_COUNT) return false;
        float amount = unit(weight);
        if (amount <= 0f) return false;
        float legs = unit(groundedIdle) * amount;
        float side = rearIsRight ? 1f : -1f;

        float breath = 0f;
        if (settings.breathDegrees() > 0f && Float.isFinite(ageInTicks) && settings.breathSeconds() > 0f) {
            double cycles = ageInTicks / (20d * settings.breathSeconds());
            breath = (float) (Math.sin((cycles - Math.floor(cycles)) * Math.PI * 2d)
                    * radians(Math.min(settings.breathDegrees(), MAXIMUM_BREATH_DEGREES)));
        }

        out[BODY_X] = limited(radians(settings.bodyPitchDegrees()) + breath,
                settings.bodyPitchDegrees() + MAXIMUM_BREATH_DEGREES, MAXIMUM_BODY_PITCH_DEGREES) * amount;
        out[BODY_Y] = limited(radians(settings.bodyYawDegrees()) * side,
                settings.bodyYawDegrees(), MAXIMUM_BODY_YAW_DEGREES) * amount;

        out[FRONT_UPPER_X] = limited(-radians(settings.frontUpperArmDegrees()),
                settings.frontUpperArmDegrees(), MAXIMUM_UPPER_ARM_DEGREES) * amount;
        out[FRONT_LOWER_X] = limited(-radians(settings.frontElbowDegrees()),
                settings.frontElbowDegrees(), MAXIMUM_ELBOW_DEGREES) * amount;
        out[REAR_UPPER_X] = limited(-radians(settings.rearUpperArmDegrees()),
                settings.rearUpperArmDegrees(), MAXIMUM_UPPER_ARM_DEGREES) * amount;
        out[REAR_LOWER_X] = limited(-radians(settings.rearElbowDegrees()),
                settings.rearElbowDegrees(), MAXIMUM_ELBOW_DEGREES) * amount;
        // Tucked in, not flared: the right arm tucks with a negative roll and the left with a
        // positive one, so the sign follows which physical arm each role is on this frame.
        float frontRoll = limited(radians(settings.armTuckDegrees()), settings.armTuckDegrees(),
                MAXIMUM_ARM_ROLL_DEGREES) * amount;
        out[FRONT_UPPER_Z] = rearIsRight ? frontRoll : -frontRoll;
        out[REAR_UPPER_Z] = rearIsRight ? -frontRoll : frontRoll;

        out[FRONT_LEG_X] = limited(-radians(settings.legReadyDegrees()),
                settings.legReadyDegrees(), MAXIMUM_LEG_DEGREES) * legs;
        out[REAR_LEG_X] = limited(radians(settings.legReadyDegrees()) * 0.7f,
                settings.legReadyDegrees(), MAXIMUM_LEG_DEGREES) * legs;
        out[FRONT_KNEE_X] = limited(radians(settings.kneeReadyDegrees()),
                settings.kneeReadyDegrees(), MAXIMUM_KNEE_DEGREES) * legs;
        out[REAR_KNEE_X] = limited(radians(settings.kneeReadyDegrees()),
                settings.kneeReadyDegrees(), MAXIMUM_KNEE_DEGREES) * legs;

        for (int slot = 0; slot < VALUE_COUNT; slot++) if (!Float.isFinite(out[slot])) return false;
        return true;
    }

    /**
     * Takes the guard back out of the joints the strike currently owns.
     *
     * <p>This is the whole strike-to-guard handover. The strike's ownership is 0 before a swing,
     * 1 through its contact and 0 again by the time the swing clock reaches 1, so the guard is
     * whole before the punch, out of the punching arm's way during it, and whole again at the end
     * WITHOUT the arm ever passing through the neutral pose in between. Both layers are smooth and
     * the weights sum to a convex combination at every instant, so nothing here can exceed what
     * either layer already asked for.
     *
     * <p>The legs are never scaled: the strike has no leg slot at all, so there is nothing there to
     * hand over, and the ready stance must not twitch when a punch is thrown while standing.
     *
     * @param ownership  0..1 from {@code ArticulatedCombatPose.ownership}
     * @param rearShare  how much of the rear arm's guard the strike takes; 1 when the rear arm is
     *                   the one attacking, which is the ordinary case
     * @param frontShare the same for the front arm
     */
    static void yieldToStrike(float[] delta, float ownership, float bodyShare,
                              float frontShare, float rearShare) {
        if (delta == null || delta.length < VALUE_COUNT) return;
        float own = unit(ownership);
        if (own <= 0f) return;
        float body = 1f - own * unit(bodyShare);
        float front = 1f - own * unit(frontShare);
        float rear = 1f - own * unit(rearShare);
        delta[BODY_X] *= body;
        delta[BODY_Y] *= body;
        delta[FRONT_UPPER_X] *= front;
        delta[FRONT_UPPER_Z] *= front;
        delta[FRONT_LOWER_X] *= front;
        delta[REAR_UPPER_X] *= rear;
        delta[REAR_UPPER_Z] *= rear;
        delta[REAR_LOWER_X] *= rear;
    }

    /** How far a foot leaves the ground at these angles, in pixels: the ready stance's only cost. */
    static float footRise(float legDegrees, float kneeDegrees) {
        double thigh = Math.toRadians(legDegrees), shin = Math.toRadians(kneeDegrees);
        return (float) (12d - (6d * Math.cos(thigh) + 6d * Math.cos(thigh + shin)));
    }

    // ------------------------------------------------------------------ settings

    /**
     * The {@code combat-motion.barehand-stance} block. Off by default, every value validated, and a
     * bad one disables only this block for the run - see {@link #loadOrDisabled}.
     */
    record Settings(boolean enabled, float holdTicks, float blendInTicks, float blendOutTicks,
                    float walkWeight, float sprintWeight, float airborneWeight,
                    float bodyPitchDegrees, float bodyYawDegrees,
                    float frontUpperArmDegrees, float frontElbowDegrees,
                    float rearUpperArmDegrees, float rearElbowDegrees, float armTuckDegrees,
                    float legReadyDegrees, float kneeReadyDegrees,
                    float breathDegrees, float breathSeconds) { }

    static final Settings DISABLED = new Settings(false, 60f, 4f, 10f, .65f, 0f, 0f,
            4f, 6f, 40f, 120f, 35f, 125f, 8f, 6f, 10f, 0f, 5f);

    static Settings loadOrDisabled(YamlDocument yaml, String[] failure) {
        try {
            return load(yaml);
        } catch (RuntimeException error) {
            if (failure != null && failure.length > 0) failure[0] = String.valueOf(error.getMessage());
            return DISABLED;
        }
    }

    private static Settings load(YamlDocument yaml) {
        return new Settings(
                Boolean.TRUE.equals(yaml.get("combat-motion.barehand-stance.enabled")),
                value(yaml, "hold-ticks", DISABLED.holdTicks(), 0f, 400f),
                value(yaml, "blend-in-ticks", DISABLED.blendInTicks(), 0f, 40f),
                value(yaml, "blend-out-ticks", DISABLED.blendOutTicks(), 0f, 40f),
                value(yaml, "walk-weight", DISABLED.walkWeight(), 0f, 1f),
                value(yaml, "sprint-weight", DISABLED.sprintWeight(), 0f, 1f),
                value(yaml, "airborne-weight", DISABLED.airborneWeight(), 0f, 1f),
                value(yaml, "body-pitch-degrees", DISABLED.bodyPitchDegrees(), 0f, MAXIMUM_BODY_PITCH_DEGREES),
                value(yaml, "body-yaw-degrees", DISABLED.bodyYawDegrees(), 0f, MAXIMUM_BODY_YAW_DEGREES),
                value(yaml, "front-upper-arm-degrees", DISABLED.frontUpperArmDegrees(), 0f, MAXIMUM_UPPER_ARM_DEGREES),
                value(yaml, "front-elbow-degrees", DISABLED.frontElbowDegrees(), 0f, MAXIMUM_ELBOW_DEGREES),
                value(yaml, "rear-upper-arm-degrees", DISABLED.rearUpperArmDegrees(), 0f, MAXIMUM_UPPER_ARM_DEGREES),
                value(yaml, "rear-elbow-degrees", DISABLED.rearElbowDegrees(), 0f, MAXIMUM_ELBOW_DEGREES),
                value(yaml, "arm-tuck-degrees", DISABLED.armTuckDegrees(), 0f, MAXIMUM_ARM_ROLL_DEGREES),
                value(yaml, "leg-ready-degrees", DISABLED.legReadyDegrees(), 0f, MAXIMUM_LEG_DEGREES),
                value(yaml, "knee-ready-degrees", DISABLED.kneeReadyDegrees(), 0f, MAXIMUM_KNEE_DEGREES),
                value(yaml, "breath-degrees", DISABLED.breathDegrees(), 0f, MAXIMUM_BREATH_DEGREES),
                value(yaml, "breath-seconds", DISABLED.breathSeconds(), 2f, 12f));
    }

    private static float value(YamlDocument yaml, String key, float fallback, float minimum, float maximum) {
        Object raw = yaml.get("combat-motion.barehand-stance." + key);
        float result = raw instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < minimum || result > maximum) {
            throw new IllegalArgumentException("articulated-player.yml.combat-motion.barehand-stance."
                    + key + " must be within " + minimum + ".." + maximum);
        }
        return result;
    }
}
