package com.murimrpg.client.render;

/**
 * Per-frame state for {@link VanillaSilhouettePose}: the fade weight, the gait phase and the
 * take-off stride. Holds no Minecraft type, so the whole layer runs on a plain JVM under test.
 *
 * <p>Every value it produces is a blend from what Mojang's model already wrote toward what this
 * layer wants, weighted by a fade that only ever moves a bounded amount per render tick. So the
 * layer at rest is bit-identical to vanilla, and entering or leaving a state it does not pose -
 * crouching, swimming, attacking, riding, armour, death - fades rather than snaps.
 */
final class VanillaSilhouetteDriver {
    /** Slots of the value array, in the order the mixin writes them back onto the parts. */
    static final int BODY_X = 0, BODY_Y = 1, BODY_Z = 2;
    static final int RIGHT_ARM_X = 3, LEFT_ARM_X = 4, RIGHT_ARM_Z = 5, LEFT_ARM_Z = 6;
    static final int RIGHT_LEG_X = 7, LEFT_LEG_X = 8;
    static final int VALUE_COUNT = 9;

    /** A render tick step larger than this is a teleport, a pause or a world change: reseed. */
    private static final float MAXIMUM_STEP_TICKS = 8f;

    private final ArticulatedGaitPhase gaitPhase = new ArticulatedGaitPhase();
    private final ArticulatedGaitBlend gaitBlend = new ArticulatedGaitBlend();
    private float supportWeight;
    private float lastAge = Float.NaN;
    private float takeOffRightLeg;
    private float takeOffLeftLeg;
    private boolean holdingTakeOff;
    private float lastDelta;
    private float airborneTicks;

    void reset() {
        gaitPhase.reset();
        gaitBlend.reset();
        supportWeight = 0f;
        lastAge = Float.NaN;
        takeOffRightLeg = 0f;
        takeOffLeftLeg = 0f;
        holdingTakeOff = false;
        lastDelta = 0f;
        airborneTicks = 0f;
    }

    float supportWeight() { return supportWeight; }

    /** Gait phase in radians, advanced from vanilla's own accumulated limb-swing distance. */
    float advancePhase(float walkAnimationPos, float cadenceScale, boolean reversed) {
        return gaitPhase.advance(walkAnimationPos, VanillaSilhouettePose.cadence(cadenceScale), reversed);
    }

    float advanceRunWeight(float ageInTicks, boolean sprinting, int blendTicks) {
        return gaitBlend.advance(ageInTicks, sprinting, blendTicks);
    }

    /**
     * Moves the fade weight toward 1 while this is a state the layer poses, and toward 0 otherwise.
     *
     * @param fadeTicks how many render ticks a full fade takes; 0 or less makes it instant
     */
    float advanceSupport(float ageInTicks, boolean supported, float fadeTicks) {
        float target = supported ? 1f : 0f;
        if (!Float.isFinite(ageInTicks)) {
            supportWeight = target;
            return supportWeight;
        }
        float delta = Float.isNaN(lastAge) ? 0f : ageInTicks - lastAge;
        lastAge = ageInTicks;
        if (!(delta >= 0f) || delta > MAXIMUM_STEP_TICKS) delta = 0f;
        lastDelta = delta;
        if (fadeTicks <= 0f) {
            supportWeight = target;
            return supportWeight;
        }
        // No time passed, or a step so large it can only be a teleport or a pause: hold still.
        // Moving here would let a single impossible frame jump the whole fade.
        if (delta <= 0f) return supportWeight;
        supportWeight = VanillaSilhouettePose.unit(
                VanillaSilhouettePose.approach(supportWeight, target, delta / fadeTicks));
        return supportWeight;
    }

    /**
     * Remembers the stride the moment the feet leave the ground, so the flight can keep it instead
     * of collapsing into a symmetric standing-jump pose.
     */
    void noteGround(boolean airborne, float rightLegAngle, float leftLegAngle) {
        if (airborne && !holdingTakeOff) {
            takeOffRightLeg = Float.isFinite(rightLegAngle) ? rightLegAngle : 0f;
            takeOffLeftLeg = Float.isFinite(leftLegAngle) ? leftLegAngle : 0f;
            airborneTicks = 0f;
        }
        if (airborne) airborneTicks += lastDelta; else airborneTicks = 0f;
        holdingTakeOff = airborne;
    }

    /**
     * 0 on the frame the feet leave the ground and 1 once the flight has lasted the whole window,
     * so the take-off stride is released rather than dropped. Uses the same bounded render-tick
     * delta the fade uses, so a pause or a teleport cannot advance it.
     */
    float airborneProgress(float windowTicks) {
        if (!Float.isFinite(windowTicks) || windowTicks <= 0f) return 1f;
        return VanillaSilhouettePose.unit(airborneTicks / windowTicks);
    }

    float takeOffRightLeg() { return takeOffRightLeg; }
    float takeOffLeftLeg() { return takeOffLeftLeg; }
    boolean holdingTakeOff() { return holdingTakeOff; }
}
