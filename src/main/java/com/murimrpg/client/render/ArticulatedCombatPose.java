package com.murimrpg.client.render;

/**
 * A rotation-only upper-body attack, added on top of whatever the locomotion layers just produced.
 *
 * <h2>Why the joints used to disappear the moment you swung</h2>
 * {@code ModelPartArticulatedPlayerRenderer.shouldReplace} carried {@code state.attackTime <= .01f}
 * in its condition, so the very first frame of a swing dropped the split model and handed the frame
 * back to Mojang's {@code PlayerModel}. Nothing about the attack was broken; the model simply
 * stopped being submitted, which is why the elbows and knees vanish and the proportions jump. The
 * fix is to stop refusing that state and to have something to draw there instead - this class.
 *
 * <h2>What attackTime actually is, read out of the 26.2 jar</h2>
 * <ul>
 *   <li>{@code ArmedEntityRenderState.extractArmedEntityRenderState} ends with
 *       {@code attackTime = LivingEntity.getAttackAnim(partialTick)}, and that method is, in
 *       bytecode, {@code f = attackAnim - oAttackAnim; if (f < 0) f += 1; return oAttackAnim + f*p}.
 *       The wrap is the important part: when the swing clock rolls over from
 *       {@code (duration-1)/duration} back to 0, the interpolation still walks FORWARDS, up to
 *       exactly 1.0. So attackTime is a monotone 0 -> 1 ramp across one swing and is exactly 0 at
 *       rest; it never plays backwards at the end.</li>
 *   <li>{@code LivingEntity.updateSwingTime} is {@code swingTime++; if (swingTime >= duration)
 *       {swingTime = 0; swinging = false;} attackAnim = swingTime / duration}. Because
 *       {@code swing()} seeds {@code swingTime = -1}, the FIRST tick of a swing has
 *       {@code attackAnim == 0} while {@code swinging} is already true: there is one tick, 50 ms,
 *       in which the render clock is flat zero. That tick is why the guard needs the entity swing
 *       flag as a second start edge, and it is also why the anticipation phase here is kept short -
 *       nothing this class can do makes that tick move.</li>
 *   <li>{@code getCurrentSwingDuration} is the held item's {@code SwingAnimation.duration},
 *       adjusted by haste and mining fatigue; the default is 6 ticks. Everything below is written
 *       in fractions of the swing, so a haste-shortened swing simply plays the same shape faster
 *       and nothing here has to know the tick count.</li>
 *   <li>{@code ArmedEntityRenderState.attackArm} is already the swinging hand -
 *       {@code mainArm} when {@code swingingArm} is the main hand and its opposite otherwise - so
 *       left-handed players and off-hand swings need no work of our own.</li>
 *   <li>{@code swingAnimationType} is NOT usable to tell a sword from a fist:
 *       {@code ItemStack.getSwingAnimation} returns {@code SwingAnimation.DEFAULT} when the item
 *       carries no component, and that default is {@code (WHACK, 6)}. An empty hand reports WHACK
 *       too. The classification used instead is whether the attacking hand holds anything, which
 *       needs no item tag and therefore covers the 목검 whatever it is registered as.</li>
 * </ul>
 *
 * <h2>What Mojang does during an attack, and what is deliberately not copied</h2>
 * {@code HumanoidModel.setupAttackAnimation} sets {@code body.yRot = sin(sqrt(attackTime) * 2PI) *
 * 0.2}, negated for a left attack arm, and then moves the arm PIVOTS with
 * {@code rightArm.z = sin(body.yRot) * 5} and {@code rightArm.x = -cos(body.yRot) * 5}. Those two
 * are translations, and they exist only because Mojang's arms are children of root and have to be
 * carried around the torso by hand. With {@code locomotion.torso-hip-pivot} on, the arms are
 * already children of body and follow the torso for free, so this class writes no offsets at all -
 * which is also what the project's rotation-only rule requires.
 *
 * <h2>The curve, and the chain that hangs off it</h2>
 * One scalar drive, in [-windUp, 1], sampled from attackTime through four phases:
 * <pre>
 *   t = 0            drive  0        rest, so the first frame of a swing adds nothing
 *   t = anticipation drive -windUp   the attacking shoulder has gathered back
 *   t = acceleration drive  1        the strike is out
 *   t = contact      drive  1 - settle
 *   t = 1            drive  0        merged back into the walk swing
 * </pre>
 * Every segment is a smoothstep, whose derivative is zero at both ends, so value AND first
 * derivative are continuous at all four joins - there is no frame where the pose steps.
 *
 * <p>The torso reads that curve directly. The upper arm reads it through {@link #chainTime} with
 * {@code arm-lag}, and the forearm through the same warp with {@code arm-lag + elbow-lag}. That is
 * the kinetic chain: the weight turns first, the shoulder follows, the forearm arrives last, from
 * ONE curve, with no second oscillator and no state that could drift.
 *
 * <p>The warp is used instead of a plain {@code t - lag} shift for a reason that shows up on the
 * very last frame of a swing. A shifted tap is not zero at {@code t = 1}: it is still reading the
 * recovery segment, so the pose would step to zero the instant attackTime crossed 1. The warp
 * {@code t - 4L*t*(1-t)} is exactly 0 at 0 and exactly 1 at 1, lags by at most {@code L} in
 * between, and is monotone for {@code L <= 0.25}. Both ends therefore close on their own.
 *
 * <h2>How this hands over to the guard</h2>
 * {@link #ownership} is how much of the upper body the strike owns at this instant: it rises over
 * the anticipation, holds through the contact, and falls to zero by the end of the swing. The model
 * multiplies the fighting guard's delta by what is left, so the punching arm is not folded into a
 * guard while it is punching, and so the arm's recovery lands ON the guard rather than passing
 * through the neutral pose first and popping. Both ends are exactly zero, so with the guard block
 * off, or with no swing running, nothing anywhere changes.
 *
 * <p>Nothing here is taken from Epic Fight, Mathimations or any other mod: no code, no constants,
 * no keyframes, no rig. The reference material was used only to name what to look for - weight
 * shift, lead rotation, arc, recovery.
 */
final class ArticulatedCombatPose {
    private ArticulatedCombatPose() { }

    /** Slots of the additive delta array, in radians. Legs are absent on purpose. */
    static final int BODY_X = 0, BODY_Y = 1;
    static final int ATTACK_UPPER_X = 2, ATTACK_LOWER_X = 3, ATTACK_UPPER_Z = 4;
    static final int OFF_UPPER_X = 5, OFF_LOWER_X = 6;
    static final int BODY_Z = 7;
    static final int VALUE_COUNT = 8;

    /** Hard ceilings regardless of YAML. Past these it stops being a person swinging. */
    static final float MAXIMUM_BODY_YAW_DEGREES = 26f;
    static final float MAXIMUM_BODY_PITCH_DEGREES = 12f;
    static final float MAXIMUM_BODY_ROLL_DEGREES = 6f;
    static final float MAXIMUM_ARM_DEGREES = 95f;
    static final float MAXIMUM_ELBOW_DEGREES = 105f;

    /**
     * The elbow may never pass this far in the direction a human elbow does not go. Negative is the
     * elbow folding forwards, which is the only way it folds; a positive total would be a broken
     * arm, so the composed value is clamped at a small positive tolerance rather than at zero, to
     * leave the authored walk swing's own tiny positive values alone.
     */
    static final float ELBOW_BACKWARD_TOLERANCE_DEGREES = 2f;

    /**
     * The largest total lag the chain warp may carry. {@code d/dt (t - 4L t (1-t))} is
     * {@code 1 - 4L(1-2t)}, whose minimum over [0,1] is {@code 1 - 4L}, so anything past 0.25 makes
     * the warped clock run backwards somewhere. 0.24 keeps a margin and is far past any usable lag.
     */
    static final float MAXIMUM_CHAIN_LAG = 0.24f;

    /**
     * The documented safe one-frame rotation step, in degrees, for a default six-tick swing drawn
     * at 60 frames per second - three frames per tick, so 1/18 of the swing per frame.
     *
     * <p>This is a bound that catches a teleport, not an aesthetic target: a punch that travels
     * roughly 110 degrees of shoulder arc inside two ticks cannot step less than about 20 degrees a
     * frame and still be a punch. Measured on the shipped moves, the worst single frame is 22.2
     * degrees barehand and 27.1 degrees for the sword, both on the attacking upper arm in the
     * middle of the out-swing. 30 leaves each of them a little headroom and nothing else.
     */
    static final float MAXIMUM_FRAME_STEP_DEGREES = 30f;

    static float radians(float degrees) { return (float) Math.toRadians(degrees); }

    static float smoothstep(float amount) {
        float value = Math.clamp(amount, 0f, 1f);
        return value * value * (3f - 2f * value);
    }

    private static float segment(float value, float from, float to, float atFrom, float atTo) {
        if (to <= from) return atTo;
        return atFrom + (atTo - atFrom) * smoothstep((value - from) / (to - from));
    }

    /**
     * The swing drive at this point of the attack.
     *
     * @return 0 outside a swing and at both ends of one, so the layer adds literally nothing there
     */
    static float drive(float attackTime, float anticipation, float acceleration, float contact,
                       float windUp, float settle) {
        if (!Float.isFinite(attackTime) || attackTime <= 0f || attackTime >= 1f) return 0f;
        float a = Math.clamp(anticipation, 0.02f, 0.5f);
        float b = Math.clamp(acceleration, a + 0.02f, 0.8f);
        float c = Math.clamp(contact, b + 0.02f, 0.95f);
        float wind = Math.clamp(windUp, 0f, 1f);
        float rest = 1f - Math.clamp(settle, 0f, 0.5f);
        float value;
        if (attackTime < a) value = segment(attackTime, 0f, a, 0f, -wind);
        else if (attackTime < b) value = segment(attackTime, a, b, -wind, 1f);
        else if (attackTime < c) value = segment(attackTime, b, c, 1f, rest);
        else value = segment(attackTime, c, 1f, rest, 0f);
        return Float.isFinite(value) ? value : 0f;
    }

    /**
     * The swing clock as one link further down the chain sees it: the same instant, read a little
     * in the past, by an amount that vanishes at both ends of the swing.
     *
     * @param lag the peak lag, at the middle of the swing; clamped to {@link #MAXIMUM_CHAIN_LAG}
     * @return a value in [0,1] that is 0 at 0, 1 at 1, never ahead of {@code attackTime}, and
     *         monotone in it
     */
    static float chainTime(float attackTime, float lag) {
        if (!Float.isFinite(attackTime)) return attackTime;
        if (!Float.isFinite(lag) || lag <= 0f) return attackTime;
        float amount = Math.min(lag, MAXIMUM_CHAIN_LAG);
        float clamped = Math.clamp(attackTime, 0f, 1f);
        float shifted = clamped - 4f * amount * clamped * (1f - clamped);
        if (attackTime <= 0f || attackTime >= 1f) return attackTime;
        return Float.isFinite(shifted) ? Math.clamp(shifted, 0f, 1f) : attackTime;
    }

    /** The same curve read one link down the chain, which is what makes each joint trail the last. */
    static float chainedDrive(float attackTime, float lag, float anticipation, float acceleration,
                              float contact, float windUp, float settle) {
        return drive(chainTime(attackTime, lag), anticipation, acceleration, contact, windUp, settle);
    }

    /**
     * A rise-hold-fall envelope on the swing clock, 0..1, used for the two places the strike has to
     * take something over and give it back: how much of the upper body it owns against the guard,
     * and how far its own elbow term has faded in.
     *
     * <p>Zero at both ends and outside a swing, so a body with no strike running is left exactly as
     * it was found, and both smoothstep edges have zero slope, so neither hand-over corners.
     *
     * <p>The two callers deliberately use different windows. The elbow's own fade rises across the
     * anticipation, because the gather has to be there when the gather is. The guard hand-over
     * rises across the whole acceleration instead: the guard's rear elbow is up to 130 degrees, and
     * taking that out over one tick is a step you can see, while taking it out over the whole
     * out-swing is not.
     *
     * @param rise the point by which the envelope has reached 1
     * @param fall the point after which it starts going back to 0, reaching it at 1
     */
    static float ownership(float attackTime, float rise, float fall) {
        if (!Float.isFinite(attackTime) || attackTime <= 0f || attackTime >= 1f) return 0f;
        float a = Math.clamp(rise, 0.02f, 0.8f);
        float c = Math.clamp(fall, a + 0.04f, 0.95f);
        if (attackTime < a) return smoothstep(attackTime / a);
        if (attackTime < c) return 1f;
        return smoothstep((1f - attackTime) / (1f - c));
    }

    /**
     * How much of the upper body the strike owns against the fighting guard at this instant.
     * Rises across the out-swing, holds through the contact, and is back to zero by the time the
     * swing clock reaches 1 - which is what makes the recovery land on the guard instead of on the
     * neutral pose.
     */
    static float guardHandover(ArticulatedCombatSettings.Move move, float attackTime) {
        if (move == null) return 0f;
        return ownership(attackTime, move.acceleration(), move.contact())
                * Math.clamp(move.guardYield(), 0f, 1f);
    }

    /**
     * Takes the counter-swing back out of the off arm in proportion to how much guard is up.
     *
     * <p>With no guard the off arm balances the strike exactly as it always has. With the guard up
     * the off hand is supposed to be covering, not swinging away, so most of the counter motion is
     * removed and what the guard wrote stays. Only the two off-arm slots are touched.
     */
    static void yieldOffArm(float[] delta, float guardWeight, float share) {
        if (delta == null || delta.length < VALUE_COUNT) return;
        float guard = Float.isFinite(guardWeight) ? Math.clamp(guardWeight, 0f, 1f) : 0f;
        float amount = Float.isFinite(share) ? Math.clamp(share, 0f, 1f) : 0f;
        float keep = 1f - guard * amount;
        delta[OFF_UPPER_X] *= keep;
        delta[OFF_LOWER_X] *= keep;
    }

    /**
     * Fills {@code out} with the rotations to ADD to the pose the locomotion layers produced.
     *
     * <p>Signs, all from the convention read out of {@code ModelPart.translateAndRotate} in the
     * 26.2 jar ({@code Quaternionf.rotationZYX(zRot, yRot, xRot)}) plus this model's own axes:
     * <ul>
     *   <li>A hanging limb's lower end goes to +z for a positive xRot. Every {@code shin} value in
     *       every clip is positive and every {@code lower_arm} value is negative, and a knee and an
     *       elbow fold opposite ways, so +z is BEHIND and -z is in front. A punch going out is
     *       therefore a NEGATIVE upper-arm xRot, and gathering back is positive.</li>
     *   <li>The right arm's pivot is x = -5, so -x is the entity's right, and under {@code Ry(t)} a
     *       point at x = -5 moves to {@code z = +5 sin(t)}. A positive body yaw carries the right
     *       shoulder BACKWARDS, so a right-handed strike needs a NEGATIVE body yaw to bring that
     *       shoulder through.</li>
     *   <li>Under {@code Rz(t)} the same point goes to {@code y = -5 sin(t) + 2 cos(t)}, and +y is
     *       down, so a POSITIVE body roll lifts the right shoulder. The striking shoulder rolling
     *       up toward the chin is the weight transfer this axis is for, so the roll is positive for
     *       a right-handed strike and mirrored for a left-handed one.</li>
     *   <li>The elbow only folds forwards, which is negative. It is never allowed to reach zero
     *       during the strike either: it opens from the gather toward the strike bend and stops
     *       there, so there is no instant where the arm is locked straight.</li>
     * </ul>
     *
     * @param attackTime      0..1 from the render state; anything else produces all zeros
     * @param rightHanded     true when {@code attackArm} is RIGHT
     * @param currentAttackElbow the elbow angle the locomotion layers already wrote for that arm,
     *                        so the composed elbow can be kept on the human side of straight
     * @param out             receives VALUE_COUNT radians; untouched on a refusal
     * @return false when the input was not a live, finite swing, which the caller must treat as
     *         "add nothing this frame" rather than as a reason to fall back to another model
     */
    static boolean solve(ArticulatedCombatSettings.Move move, float attackTime, boolean rightHanded,
                         float weight, float currentAttackElbow, float[] out) {
        if (move == null || out == null || out.length < VALUE_COUNT) return false;
        if (!Float.isFinite(attackTime) || attackTime <= 0f || attackTime >= 1f) return false;
        if (!Float.isFinite(weight) || weight <= 0f) return false;
        if (!Float.isFinite(currentAttackElbow)) return false;
        float scale = Math.clamp(weight, 0f, 1f);
        float side = rightHanded ? 1f : -1f;

        // One curve, three taps. The torso is the root of the chain and reads it unshifted.
        float armLag = Math.clamp(move.armLag(), 0f, MAXIMUM_CHAIN_LAG);
        float foreLag = Math.min(armLag + Math.clamp(move.elbowLag(), 0f, MAXIMUM_CHAIN_LAG),
                MAXIMUM_CHAIN_LAG);
        float lead = drive(attackTime, move.anticipation(), move.acceleration(), move.contact(),
                move.windUp(), move.settle());
        float armSwing = chainedDrive(attackTime, armLag, move.anticipation(), move.acceleration(),
                move.contact(), move.windUp(), move.settle());
        float foreSwing = chainedDrive(attackTime, foreLag, move.anticipation(), move.acceleration(),
                move.contact(), move.windUp(), move.settle());
        if (!Float.isFinite(lead) || !Float.isFinite(armSwing) || !Float.isFinite(foreSwing)) return false;

        // The attacking shoulder comes through, so a right-handed strike is a negative yaw.
        float bodyYaw = limited(-lead * radians(move.bodyYawDegrees()),
                move.bodyYawDegrees(), MAXIMUM_BODY_YAW_DEGREES) * scale * side;
        // A small forward pitch at the strike only; the wind-up does not rock backwards, because a
        // visible backward lean on every punch is the thing that reads as a cartoon.
        float bodyPitch = limited(Math.max(0f, lead) * radians(move.bodyPitchDegrees()),
                move.bodyPitchDegrees(), MAXIMUM_BODY_PITCH_DEGREES) * scale;
        // The weight going onto the strike: the striking shoulder rolls up, and only on the way
        // out, so the gather does not rock the body the other way as well as the yaw.
        float bodyRoll = limited(Math.max(0f, lead) * radians(move.bodyRollDegrees()),
                move.bodyRollDegrees(), MAXIMUM_BODY_ROLL_DEGREES) * scale * side;

        float attackUpper = limited(-armSwing * radians(move.upperArmDegrees()),
                move.upperArmDegrees(), MAXIMUM_ARM_DEGREES) * scale;
        // The elbow opens from the gather bend toward the strike bend and never past it, so the arm
        // is never locked straight at contact and never passes through straight on the way out
        // either - the old form crossed zero in the middle of the acceleration, which is the snap.
        // The envelope is read at the forearm's own place in the chain, so its fade trails too.
        float wind = Math.clamp(move.windUp(), 0f, 1f);
        float opening = Math.clamp((foreSwing + wind) / (1f + wind), 0f, 1f);
        float envelope = ownership(chainTime(attackTime, foreLag), move.anticipation(), move.contact());
        float gatherBend = radians(move.elbowGatherDegrees());
        float strikeBend = radians(move.elbowStrikeDegrees());
        float elbow = -envelope * (gatherBend + (strikeBend - gatherBend) * opening);
        float attackLower = limited(elbow, Math.max(move.elbowGatherDegrees(), move.elbowStrikeDegrees()),
                MAXIMUM_ELBOW_DEGREES) * scale;
        // Composed with what the gait already wrote, the elbow must stay on the human side.
        float tolerance = radians(ELBOW_BACKWARD_TOLERANCE_DEGREES);
        if (currentAttackElbow + attackLower > tolerance) attackLower = tolerance - currentAttackElbow;

        // A sword travels a diagonal, so the upper arm also rolls; a fist does not, and the value
        // is 0 for the barehand move. Outward is +z for the right arm and -z for the left.
        float attackRoll = limited(Math.max(0f, armSwing) * radians(move.armRollDegrees()),
                move.armRollDegrees(), MAXIMUM_ARM_DEGREES) * scale * side;

        // The other arm balances rather than mirrors: it swings the opposite way, smaller. The
        // model takes most of this back out again while a guard is up - see yieldOffArm.
        float offUpper = limited(lead * radians(move.counterArmDegrees()),
                move.counterArmDegrees(), MAXIMUM_ARM_DEGREES) * scale;
        float offLower = limited(-Math.max(0f, -lead) * radians(move.counterElbowDegrees()),
                move.counterElbowDegrees(), MAXIMUM_ELBOW_DEGREES) * scale;

        out[BODY_X] = bodyPitch;
        out[BODY_Y] = bodyYaw;
        out[BODY_Z] = bodyRoll;
        out[ATTACK_UPPER_X] = attackUpper;
        out[ATTACK_LOWER_X] = attackLower;
        out[ATTACK_UPPER_Z] = attackRoll;
        out[OFF_UPPER_X] = offUpper;
        out[OFF_LOWER_X] = offLower;
        for (int slot = 0; slot < VALUE_COUNT; slot++) if (!Float.isFinite(out[slot])) return false;
        return true;
    }

    static float limited(float value, float ceilingDegrees, float hardCeilingDegrees) {
        if (!Float.isFinite(value)) return 0f;
        float ceiling = radians(Math.min(Math.abs(ceilingDegrees), Math.abs(hardCeilingDegrees)));
        return Math.clamp(value, -ceiling, ceiling);
    }

    /**
     * Where the articulated hand actually is, as a single angle from the arm pivot, so Mojang's
     * one-piece arm can be pointed at it and the held item lands on the hand instead of wherever
     * the vanilla walk swing happens to have put it.
     *
     * <p>Why this is needed at all: {@code ItemInHandLayer.submitArmWithItem} calls
     * {@code getParentModel().translateToHand(...)}, and the parent model is the renderer's own
     * {@code PlayerModel} - not the split model this project submits in its place. Our redirect
     * swaps only the base {@code submitModel} call, so the item has always been placed by Mojang's
     * arm. {@code HumanoidModel.translateToHand} is just {@code root.translateAndRotate} then
     * {@code arm.translateAndRotate}, so the item follows that arm's rotation and nothing else.
     *
     * <p>Both arms are 12 px from the pivot, and the split arm reaches its hand in two 5 px steps
     * from the pivot with the elbow between them. Pointing the rigid arm along the line from the
     * pivot to the split hand is the closest a single segment can get; what is left over is length,
     * {@code 10 - |hand|}, which is 0 with a straight elbow and grows as it folds.
     */
    static float handDirection(float upperArmX, float lowerArmX) {
        if (!Float.isFinite(upperArmX) || !Float.isFinite(lowerArmX)) return 0f;
        double y = 5d * Math.cos(upperArmX) + 5d * Math.cos(upperArmX + lowerArmX);
        double z = 5d * Math.sin(upperArmX) + 5d * Math.sin(upperArmX + lowerArmX);
        if (y == 0d && z == 0d) return upperArmX;
        float result = (float) Math.atan2(z, y);
        return Float.isFinite(result) ? result : upperArmX;
    }

    /** How far short of the rigid arm's 10 px the folded arm's hand falls, in pixels. */
    static float handShortfall(float upperArmX, float lowerArmX) {
        if (!Float.isFinite(upperArmX) || !Float.isFinite(lowerArmX)) return 0f;
        double y = 5d * Math.cos(upperArmX) + 5d * Math.cos(upperArmX + lowerArmX);
        double z = 5d * Math.sin(upperArmX) + 5d * Math.sin(upperArmX + lowerArmX);
        return (float) (10d - Math.hypot(y, z));
    }

    /**
     * True when the swing clock went backwards, which is how a second attack pressed before the
     * first finished shows up: {@code swingTime} is reset to 0 and {@code attackTime} drops. The
     * caller holds the pose it had and crosses into the new swing over a few ticks, so a fast
     * combo does not snap back to the rest pose on every press.
     */
    static boolean swingRestarted(float previousAttackTime, float attackTime) {
        if (!Float.isFinite(previousAttackTime) || !Float.isFinite(attackTime)) return false;
        return previousAttackTime > 0f && attackTime < previousAttackTime - 0.01f;
    }
}
