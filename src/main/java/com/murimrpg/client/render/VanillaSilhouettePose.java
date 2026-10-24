package com.murimrpg.client.render;

/**
 * Rotation-only locomotion for Mojang's own player model, so the silhouette never changes.
 *
 * <h2>Why this exists at all</h2>
 * The split model reproduces vanilla's standing bounds exactly - both were read out of the 26.2 jar
 * and they agree to the pixel - but it is not vanilla, so it has to hand the player back to
 * Mojang's model whenever a state it cannot pose comes up: armour, crouching, swimming, attacking,
 * item use. That handover is the body changing shape mid-play, which is the one thing the user
 * ruled out. Mojang's model has no elbow and no knee, so the price of never changing shape is that
 * this mode cannot bend them. That trade is the whole point of the mode and it is stated in
 * CLAUDE_VANILLA_SILHOUETTE_LOCOMOTION_V1.md rather than hidden here.
 *
 * <h2>What the hierarchy allows, read off HumanoidModel.createMesh in the 26.2 jar</h2>
 * <pre>
 *   head  8x8x8   pivot (0,0,0)      root y -8..0     child of root
 *   body  8x12x4  pivot (0,0,0)      root y  0..12    child of root
 *   arm   4x12x4  pivot (+-5,2,0)    root y  0..12    child of root, rigid
 *   leg   4x12x4  pivot (+-1.9,12,0) root y 12..24    child of root, rigid
 * </pre>
 * Every part is a child of root. Two consequences decide this class:
 * <ul>
 *   <li>Rotating body cannot disturb the head, so the world look stays exactly what vanilla wrote
 *       from the mouse. No head compensation is needed or wanted here.</li>
 *   <li>Rotating body does not carry the arms either. A body yaw therefore offsets the shoulder
 *       seam by {@code 4 * sin(yaw)} px - 0.209 at 3 degrees, 0.279 at 4 - and the arms are left
 *       alone so the hand path, and anything held in it, is exactly vanilla's.</li>
 * </ul>
 *
 * <h2>Measured limb shortening, which is the "short legs" report</h2>
 * A vanilla arm and leg are rigid 12 px forever. A hinged pair is only 12 px when it is straight:
 * <pre>
 *   walk V6                    shortest visible leg 10.937 px   -8.9 %   arm 11.701 px  -2.5 %
 *   run V3, motion-style off   shortest visible leg 11.441 px   -4.7 %   arm 11.936 px  -0.5 %
 *   run V3, motion-style on    shortest visible leg 10.756 px  -10.4 %   arm 10.821 px  -9.8 %
 * </pre>
 * So the proportions are not wrong while standing - they are identical - they shorten while
 * moving, and the deeper the authored knee and the added elbow bias, the shorter. On this path the
 * limbs cannot shorten at all, because they cannot bend.
 *
 * <p>Nothing here is derived from any third-party mod, model, rig or animation, and no Minecraft
 * type is imported, so every function is testable on a plain JVM.
 */
final class VanillaSilhouettePose {
    private VanillaSilhouettePose() { }

    /** Hard ceilings, independent of YAML. Past these it is not the vanilla player any more. */
    static final float MAXIMUM_LIMB_DEGREES = 62f;
    static final float MAXIMUM_LEAN_DEGREES = 18f;
    static final float MAXIMUM_YAW_DEGREES = 6f;
    static final float MAXIMUM_ROLL_DEGREES = 4f;

    /** Mojang's own limb-swing multiplier, read from HumanoidModel.setupAnim in the 26.2 jar. */
    static final float VANILLA_LIMB_SWING_SCALE = 0.6662f;

    static float radians(float degrees) { return (float) Math.toRadians(degrees); }

    static float unit(float value) { return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f; }

    static float smoothstep(float amount) {
        float value = unit(amount);
        return value * value * (3f - 2f * value);
    }

    /**
     * A bounded step toward a target. Used for every weight in this layer so that entering or
     * leaving a state fades instead of snapping, and so that no state can run away: the result is
     * always between the two inputs.
     *
     * @param maximumStep how far one render tick may move; a non-positive or non-finite step jumps
     */
    static float approach(float current, float target, float maximumStep) {
        if (!Float.isFinite(current) || !Float.isFinite(target)) return Float.isFinite(target) ? target : 0f;
        if (!Float.isFinite(maximumStep) || maximumStep <= 0f) return target;
        float delta = target - current;
        if (delta > maximumStep) return current + maximumStep;
        if (delta < -maximumStep) return current - maximumStep;
        return target;
    }

    /** Blend from whatever Mojang already wrote toward ours. Weight 0 returns vanilla untouched. */
    static float blend(float vanilla, float ours, float weight) {
        if (!Float.isFinite(vanilla)) return 0f;
        if (!Float.isFinite(ours) || !Float.isFinite(weight)) return vanilla;
        float amount = unit(weight);
        if (amount == 0f) return vanilla;
        float result = vanilla + (ours - vanilla) * amount;
        return Float.isFinite(result) ? result : vanilla;
    }

    static float limited(float radiansValue, float ceilingDegrees, float hardCeilingDegrees) {
        if (!Float.isFinite(radiansValue)) return 0f;
        float ceiling = radians(Math.min(Math.abs(ceilingDegrees), hardCeilingDegrees));
        return Math.clamp(radiansValue, -ceiling, ceiling);
    }

    // ------------------------------------------------------------------ gait

    /**
     * The hip or shoulder angle for a rigid limb, taken from the authored clip's UPPER segment.
     *
     * <p>The upper segment is the right answer because the vanilla part's pivot is the hip or the
     * shoulder, exactly where the authored upper segment pivots. Pointing the rigid limb at the
     * authored FOOT instead was measured and rejected: at the deepest knee fold of the run clip
     * that direction is 47.3 degrees against the thigh's 29.8, so a rigid leg would swing far past
     * the stride ceiling and read as a kick.
     *
     * @param strideScale lengthens or shortens the step without touching cadence; 1.0 is the clip
     */
    static float strideAngle(float authoredUpperSegment, float strideScale, float ceilingDegrees) {
        if (!Float.isFinite(authoredUpperSegment) || !Float.isFinite(strideScale)) return 0f;
        return limited(authoredUpperSegment * Math.clamp(strideScale, 0f, 2f),
                ceilingDegrees, MAXIMUM_LIMB_DEGREES);
    }

    /**
     * Forward lean, interpolated between the walk and the run value by the sprint blend and faded
     * in by gait speed so a standing player leans exactly zero.
     */
    static float lean(float walkDegrees, float runDegrees, float runWeight, float speed) {
        float weight = unit(runWeight);
        float degrees = walkDegrees + (runDegrees - walkDegrees) * weight;
        if (!Float.isFinite(degrees)) return 0f;
        return limited(radians(degrees) * unit(speed), Math.abs(degrees), MAXIMUM_LEAN_DEGREES);
    }

    /**
     * The torso following the arm that is swinging forward.
     *
     * <p>Sign, derived from the convention read out of the 26.2 jar - ModelPart.translateAndRotate
     * builds {@code Quaternionf.rotationZYX(zRot, yRot, xRot)}, so a part's matrix is
     * {@code Rz*Ry*Rx} - together with this model's own axes. The right arm's pivot is x = -5, so
     * -x is the entity's right; the front face of the head cube is the -z face, so -z is forward.
     * Under {@code Ry(t)} a point at x = -5 moves to {@code z = +5 sin(t)}. A positive yaw therefore
     * carries the RIGHT shoulder backwards and the left one forwards.
     *
     * <p>A hanging limb's lower end goes to +z for a positive xRot: every {@code shin} value in
     * every clip is positive and every {@code lower_arm} value is negative, and a knee and an elbow
     * fold opposite ways, which pins that. So the arm with the more NEGATIVE pitch is the forward
     * one. When the right arm is forward we want the right shoulder forward, which is a negative
     * yaw, and {@code rightPitch - leftPitch} is negative there. The difference is therefore used
     * with its own sign and no negation.
     *
     * @return 0 when the setting is 0, and never more than the ceiling
     */
    static float shoulderYaw(float rightArmPitch, float leftArmPitch, float gain, float ceilingDegrees) {
        if (!Float.isFinite(rightArmPitch) || !Float.isFinite(leftArmPitch) || !Float.isFinite(gain)) return 0f;
        float ceiling = radians(Math.min(Math.abs(ceilingDegrees), MAXIMUM_YAW_DEGREES));
        if (ceiling <= 0f || gain <= 0f) return 0f;
        float raw = (rightArmPitch - leftArmPitch) * Math.clamp(gain, 0f, 1f);
        if (!Float.isFinite(raw)) return 0f;
        // Soft saturation: the ceiling is approached, never crossed, and the curve has no corner.
        return ceiling * (float) Math.tanh(raw / ceiling);
    }

    // ------------------------------------------------------------------ idle

    /**
     * How much of the ready stance is showing. One at a standstill, zero as soon as the gait has
     * any speed worth animating, with a smooth shoulder between.
     */
    static float idleWeight(float gaitSpeed, float fadeSpeed) {
        if (!Float.isFinite(gaitSpeed) || !Float.isFinite(fadeSpeed) || fadeSpeed <= 0f) return 0f;
        return 1f - smoothstep(gaitSpeed / fadeSpeed);
    }

    /**
     * Breathing, as a rotation and nothing else. No translation, so the character cannot bob: the
     * torso rocks by a fraction of a degree about the hip line and that is all. The period is in
     * seconds and the phase comes from the render clock, not from the stride, because breathing is
     * not part of the gait.
     */
    static float breath(float ageInTicks, float periodSeconds, float degrees) {
        if (!Float.isFinite(ageInTicks) || !Float.isFinite(periodSeconds) || periodSeconds <= 0f) return 0f;
        float amplitude = radians(Math.clamp(Math.abs(degrees), 0f, 1.5f));
        if (amplitude == 0f) return 0f;
        double cycles = ageInTicks / (20d * periodSeconds);
        return (float) (Math.sin((cycles - Math.floor(cycles)) * Math.PI * 2d) * amplitude);
    }

    /**
     * The arm's outward hang. Vanilla leaves the arms flat against the torso, which is what reads
     * as "a vanilla character with bent elbows" once anything else changes. A degree or two of
     * outward roll is what a relaxed shoulder actually does, and it is the only axis on a rigid arm
     * that reads as relaxation rather than as a pose.
     *
     * @param rightSide true for the right arm, whose outward direction is the opposite sign
     */
    static float armRelaxRoll(float degrees, boolean rightSide, float weight) {
        float amount = unit(weight);
        if (amount == 0f) return 0f;
        float roll = radians(Math.clamp(Math.abs(degrees), 0f, 6f)) * amount;
        return rightSide ? -roll : roll;
    }

    // ------------------------------------------------------------------ jump

    /**
     * How much of the stride the take-off keeps while airborne.
     *
     * <p>The reported "a jump while moving looks like a standing jump" is this: the authored jump
     * frames are symmetric and identical whichever way the player is travelling, and the take-off
     * stride is released into them over take-off-blend-ticks, so a running jump becomes a standing
     * jump within a few render ticks. Holding a floor under that release keeps the legs split the
     * way they were at take-off for the whole flight, which is what a real jump does.
     *
     * @param airborneProgress 0 at take-off, 1 once the authored pose has fully taken over
     * @param heldFraction     how much of the take-off split survives at the end; 0 is today
     */
    static float takeOffHold(float airborneProgress, float heldFraction) {
        float progress = unit(airborneProgress);
        float floor = unit(heldFraction);
        return floor + (1f - floor) * (1f - smoothstep(progress));
    }

    /**
     * Lean into the direction of travel while airborne, from the player's own forward and strafe
     * velocity. Rotation only: the player is not moved, and the values are the ones the renderer
     * already reads for the gait direction.
     *
     * @param blocksPerTick signed velocity along one of the player's own axes
     * @param degreesPerBlockPerTick how much lean a unit of that velocity is worth
     * @return radians, bounded by the ceiling and by the hard ceiling above
     */
    static float travelLean(float blocksPerTick, float degreesPerBlockPerTick, float ceilingDegrees) {
        if (!Float.isFinite(blocksPerTick) || !Float.isFinite(degreesPerBlockPerTick)) return 0f;
        return limited(radians(blocksPerTick * degreesPerBlockPerTick), ceilingDegrees, MAXIMUM_LEAN_DEGREES);
    }

    // ------------------------------------------------------------------ cadence

    /**
     * The limb-swing multiplier this layer feeds the clip sampler.
     *
     * <p>Cadence is deliberately NOT slowed by default, and the arithmetic is why. Vanilla's
     * WalkAnimationState adds its speed to walkAnimationPos every tick and that speed is roughly
     * {@code min(1, 4 * blocksPerTick)}, so at the walk speed of 0.2158 blocks per tick the phase
     * advances 0.5751 rad per tick: 10.93 ticks per cycle, 3.66 steps per second, 1.179 m of ground
     * per step. A rigid 12 px leg swinging to 42 degrees renders 1.004 m of foot travel per step,
     * so the foot already slides 14.9 percent. Sprinting it is 4.24 steps per second, 1.323 m of
     * ground, 24.1 percent. The cadence that would remove the slide is FASTER, 4.30 and 5.59 steps
     * per second, so slowing the cadence makes the slide worse, not better. Lengthening the stride
     * is the change that helps both at once, which is what stride-scale is for.
     *
     * @param scale 1.0 is exactly Mojang's cadence; below 1 is slower and slides more
     */
    static float cadence(float scale) {
        if (!Float.isFinite(scale)) return VANILLA_LIMB_SWING_SCALE;
        return VANILLA_LIMB_SWING_SCALE * Math.clamp(scale, 0.5f, 1.5f);
    }

    // ------------------------------------------------------------------ assembly

    /**
     * Turns everything above into the nine rotations the mixin writes back, blended from what
     * Mojang's model already produced.
     *
     * <p>Order matters and is fixed here rather than in the mixin: the gait decides the base pose,
     * the airborne hold overrides the legs because a jump owns them outright, and the idle stance
     * takes over last because it only exists where the gait is silent. The support weight is
     * applied once, at the end, against vanilla's own values, which is what makes weight 0
     * bit-identical and every state change a fade.
     *
     * <p>body.zRot is left at vanilla except while airborne. Leading with side-to-side torso roll
     * is exactly the look the user asked us to stop using; the run's torso expression here is the
     * forward lean plus the shoulder following the forward arm.
     *
     * @param vanilla the nine values Mojang's setupAnim just wrote, in the driver's slot order
     * @param out     receives the result; may be the same array as vanilla
     */
    static void assemble(VanillaSilhouetteSettings settings, float[] vanilla, float[] out,
                         float supportWeight, float runWeight, float gaitSpeed, float ageInTicks,
                         boolean airborne, float airborneProgress,
                         float forwardBlocksPerTick, float strafeBlocksPerTick,
                         float clipRightThigh, float clipLeftThigh, float clipRightArm, float clipLeftArm,
                         float takeOffRightLeg, float takeOffLeftLeg) {
        if (vanilla == null || out == null
                || vanilla.length < VanillaSilhouetteDriver.VALUE_COUNT
                || out.length < VanillaSilhouetteDriver.VALUE_COUNT) {
            return;
        }
        for (int slot = 0; slot < VanillaSilhouetteDriver.VALUE_COUNT; slot++) {
            out[slot] = Float.isFinite(vanilla[slot]) ? vanilla[slot] : 0f;
        }
        float weight = unit(supportWeight);
        if (settings == null || !settings.enabled() || weight == 0f) return;

        float bodyX = out[VanillaSilhouetteDriver.BODY_X];
        float bodyY = out[VanillaSilhouetteDriver.BODY_Y];
        float bodyZ = out[VanillaSilhouetteDriver.BODY_Z];
        float rightArm = out[VanillaSilhouetteDriver.RIGHT_ARM_X];
        float leftArm = out[VanillaSilhouetteDriver.LEFT_ARM_X];
        float rightArmZ = out[VanillaSilhouetteDriver.RIGHT_ARM_Z];
        float leftArmZ = out[VanillaSilhouetteDriver.LEFT_ARM_Z];
        float rightLeg = out[VanillaSilhouetteDriver.RIGHT_LEG_X];
        float leftLeg = out[VanillaSilhouetteDriver.LEFT_LEG_X];

        if (settings.gaitEnabled()) {
            float ceiling = settings.limbCeilingDegrees();
            rightLeg = strideAngle(clipRightThigh, settings.strideScale(), ceiling);
            leftLeg = strideAngle(clipLeftThigh, settings.strideScale(), ceiling);
            rightArm = strideAngle(clipRightArm, settings.armStrideScale(), ceiling);
            leftArm = strideAngle(clipLeftArm, settings.armStrideScale(), ceiling);
            bodyX = lean(settings.walkLeanDegrees(), settings.runLeanDegrees(), runWeight, gaitSpeed);
            bodyY = shoulderYaw(rightArm, leftArm, settings.shoulderYawGain(), settings.shoulderYawDegrees())
                    * unit(gaitSpeed);
            bodyZ = 0f;
            rightArmZ = 0f;
            leftArmZ = 0f;
        }

        if (settings.jumpEnabled() && airborne) {
            // The legs stop scissoring and keep the split they left the ground with. Without this
            // the horizontal velocity keeps walkAnimationPos advancing all through the flight,
            // which is the reported "the legs swing about and it looks like a standing jump".
            float hold = takeOffHold(airborneProgress, settings.jumpHoldFraction());
            rightLeg += (takeOffRightLeg - rightLeg) * hold;
            leftLeg += (takeOffLeftLeg - leftLeg) * hold;
            bodyX += travelLean(forwardBlocksPerTick, settings.jumpLeanPerBlock(), settings.jumpLeanDegrees());
            bodyZ += limited(radians(strafeBlocksPerTick * settings.jumpRollPerBlock()),
                    settings.jumpRollDegrees(), MAXIMUM_ROLL_DEGREES);
        }

        if (settings.idleEnabled() && !airborne) {
            float idle = idleWeight(gaitSpeed, settings.idleFadeSpeed());
            if (idle > 0f) {
                float torso = radians(settings.idleLeanDegrees())
                        + breath(ageInTicks, settings.breathSeconds(), settings.breathDegrees());
                bodyX = blend(bodyX, limited(torso, MAXIMUM_LEAN_DEGREES, MAXIMUM_LEAN_DEGREES), idle);
                bodyY = blend(bodyY, 0f, idle);
                bodyZ = blend(bodyZ, 0f, idle);
                // Not a mannequin: one arm hangs a little further back than the other, and both
                // stand off the ribs instead of lying flat against them.
                rightArm = blend(rightArm, radians(-settings.idleArmPitchDegrees()), idle);
                leftArm = blend(leftArm, radians(settings.idleArmPitchDegrees() * 0.45f), idle);
                rightArmZ = blend(rightArmZ, armRelaxRoll(settings.idleArmSpreadDegrees(), true, 1f), idle);
                leftArmZ = blend(leftArmZ, armRelaxRoll(settings.idleArmSpreadDegrees(), false, 1f), idle);
                rightLeg = blend(rightLeg, 0f, idle);
                leftLeg = blend(leftLeg, 0f, idle);
            }
        }

        out[VanillaSilhouetteDriver.BODY_X] = blend(vanilla[VanillaSilhouetteDriver.BODY_X],
                limited(bodyX, MAXIMUM_LEAN_DEGREES, MAXIMUM_LEAN_DEGREES), weight);
        out[VanillaSilhouetteDriver.BODY_Y] = blend(vanilla[VanillaSilhouetteDriver.BODY_Y],
                limited(bodyY, MAXIMUM_YAW_DEGREES, MAXIMUM_YAW_DEGREES), weight);
        out[VanillaSilhouetteDriver.BODY_Z] = blend(vanilla[VanillaSilhouetteDriver.BODY_Z],
                limited(bodyZ, MAXIMUM_ROLL_DEGREES, MAXIMUM_ROLL_DEGREES), weight);
        out[VanillaSilhouetteDriver.RIGHT_ARM_X] = blend(vanilla[VanillaSilhouetteDriver.RIGHT_ARM_X],
                limited(rightArm, MAXIMUM_LIMB_DEGREES, MAXIMUM_LIMB_DEGREES), weight);
        out[VanillaSilhouetteDriver.LEFT_ARM_X] = blend(vanilla[VanillaSilhouetteDriver.LEFT_ARM_X],
                limited(leftArm, MAXIMUM_LIMB_DEGREES, MAXIMUM_LIMB_DEGREES), weight);
        out[VanillaSilhouetteDriver.RIGHT_ARM_Z] = blend(vanilla[VanillaSilhouetteDriver.RIGHT_ARM_Z],
                limited(rightArmZ, 6f, 6f), weight);
        out[VanillaSilhouetteDriver.LEFT_ARM_Z] = blend(vanilla[VanillaSilhouetteDriver.LEFT_ARM_Z],
                limited(leftArmZ, 6f, 6f), weight);
        out[VanillaSilhouetteDriver.RIGHT_LEG_X] = blend(vanilla[VanillaSilhouetteDriver.RIGHT_LEG_X],
                limited(rightLeg, MAXIMUM_LIMB_DEGREES, MAXIMUM_LIMB_DEGREES), weight);
        out[VanillaSilhouetteDriver.LEFT_LEG_X] = blend(vanilla[VanillaSilhouetteDriver.LEFT_LEG_X],
                limited(leftLeg, MAXIMUM_LIMB_DEGREES, MAXIMUM_LIMB_DEGREES), weight);
    }
}
