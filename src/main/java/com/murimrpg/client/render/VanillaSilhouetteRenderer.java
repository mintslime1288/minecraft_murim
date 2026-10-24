package com.murimrpg.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Poses Mojang's own player model instead of replacing it.
 *
 * <p>This is the whole answer to "the body changes shape when I attack or crouch or put armour on".
 * The model submitted is always {@code net.minecraft.client.model.player.PlayerModel}, in every
 * state, so there is no second body to switch to and nothing to keep in sync: the skin, the outer
 * layers, the slim and classic arms, the cape, the armour and the held item are all Mojang's, and
 * they stay Mojang's. This class only writes rotations onto parts that already exist.
 *
 * <h2>Where it runs, and why that is the last safe point</h2>
 * Read out of the 26.2 jar: {@code PlayerModel.setupAnim(AvatarRenderState)} sets the overlay
 * visibility flags and then calls {@code HumanoidModel.setupAnim} LAST, so a TAIL injection on
 * {@code HumanoidModel.setupAnim} is the final thing that happens to the pose. Everything Mojang
 * does - the look rotation, the limb swing, crouching, swimming, riding, the attack swing, item
 * use, the shield and spyglass poses - has already been written by then, so this layer can blend
 * from it rather than fight it.
 *
 * <p>All six parts are children of root in {@code HumanoidModel.createMesh}, which decides two
 * things. Rotating {@code body} cannot move {@code head}, so the world look stays exactly what
 * Mojang wrote from the mouse and no head compensation exists here. And rotating {@code body} does
 * not carry the arms either, so the arms are never yawed: their pivot, their swing plane and
 * therefore the held item's position stay exactly vanilla, at the cost of a shoulder seam offset of
 * {@code 4 * sin(bodyYaw)} px, which is 0.209 at the 3 degree default and 0.279 at the 4 degree
 * hard ceiling.
 *
 * <h2>What it does not do</h2>
 * There is no elbow and no knee on this model, so this mode cannot bend them. That is the price of
 * never changing shape, and it is the reason the split {@link ArticulatedHumanoidModel} is kept
 * rather than deleted: it stays available as the experimental joint path and this block turns it
 * off while it is on. The two are never rendered together.
 */
public final class VanillaSilhouetteRenderer {
    private static final VanillaSilhouetteDriver DRIVER = new VanillaSilhouetteDriver();
    private static final float[] VANILLA = new float[VanillaSilhouetteDriver.VALUE_COUNT];
    private static final float[] POSED = new float[VanillaSilhouetteDriver.VALUE_COUNT];
    /** How long a flight takes to release the take-off stride, in render ticks. */
    private static final float AIRBORNE_WINDOW_TICKS = 10f;

    private VanillaSilhouetteRenderer() { }

    /** True when this block is on, which also switches the split-model submission off. */
    public static boolean active() {
        var settings = ModelPartArticulatedPlayerRenderer.settings();
        return settings != null && settings.vanillaSilhouette().enabled();
    }

    /** Called when the client reloads resources, so a fade cannot survive a world change. */
    public static void reset() { DRIVER.reset(); }

    /**
     * @return true when this layer wrote to the parts, so the caller must not also run the older
     *         natural-walk path on the same model
     */
    public static boolean apply(AvatarRenderState state, ModelPart body, ModelPart rightArm,
                                ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        var settings = ModelPartArticulatedPlayerRenderer.settings();
        if (settings == null || !settings.vanillaSilhouette().enabled()) return false;
        if (body == null || rightArm == null || leftArm == null || rightLeg == null || leftLeg == null) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || state.id != player.getId()) return false;

        var block = settings.vanillaSilhouette();
        // Armour is deliberately NOT a reason to stop: the silhouette is Mojang's either way, and
        // the armour parts follow these same rotations, so the pose survives putting a chestplate
        // on instead of the body changing shape underneath it.
        boolean supported = !state.isCrouching && !state.isFallFlying && !state.isVisuallySwimming
                && !state.isPassenger && !state.isUsingItem && state.attackTime <= .01f && state.deathTime == 0;
        float weight = DRIVER.advanceSupport(state.ageInTicks, supported, block.fadeTicks());
        if (weight <= 0f) return false;

        boolean airborne = !player.onGround();
        var velocity = player.getDeltaMovement();
        var direction = LocomotionDirection.of(velocity.x, velocity.z, player.yBodyRot,
                settings.strafeGaitWeight(), settings.reverseDeadband(), false);
        float gaitSpeed = Math.clamp(state.walkAnimationSpeed, 0f, 1f) * direction.gaitWeight();
        float runWeight = DRIVER.advanceRunWeight(state.ageInTicks, player.isSprinting(), settings.gaitBlendTicks());
        float phase = DRIVER.advancePhase(state.walkAnimationPos, block.cadenceScale(), direction.reversed());

        var walk = ModelPartArticulatedPlayerRenderer.walkAnimation();
        var run = ModelPartArticulatedPlayerRenderer.runAnimation();
        if (walk == null || run == null || !Float.isFinite(phase) || !Float.isFinite(gaitSpeed)) return false;
        var walkPose = walk.sample(phase).scaled(gaitSpeed);
        var runPose = run.sample(phase).scaled(gaitSpeed);
        float rightThigh = mix(walkPose.rightLeg().x, runPose.rightLeg().x, runWeight) * settings.upperLegGain();
        float leftThigh = mix(walkPose.leftLeg().x, runPose.leftLeg().x, runWeight) * settings.upperLegGain();
        float rightUpperArm = mix(walkPose.rightArm().x, runPose.rightArm().x, runWeight) * settings.upperArmGain();
        float leftUpperArm = mix(walkPose.leftArm().x, runPose.leftArm().x, runWeight) * settings.upperArmGain();

        DRIVER.noteGround(airborne, rightLeg.xRot, leftLeg.xRot);

        VANILLA[VanillaSilhouetteDriver.BODY_X] = body.xRot;
        VANILLA[VanillaSilhouetteDriver.BODY_Y] = body.yRot;
        VANILLA[VanillaSilhouetteDriver.BODY_Z] = body.zRot;
        VANILLA[VanillaSilhouetteDriver.RIGHT_ARM_X] = rightArm.xRot;
        VANILLA[VanillaSilhouetteDriver.LEFT_ARM_X] = leftArm.xRot;
        VANILLA[VanillaSilhouetteDriver.RIGHT_ARM_Z] = rightArm.zRot;
        VANILLA[VanillaSilhouetteDriver.LEFT_ARM_Z] = leftArm.zRot;
        VANILLA[VanillaSilhouetteDriver.RIGHT_LEG_X] = rightLeg.xRot;
        VANILLA[VanillaSilhouetteDriver.LEFT_LEG_X] = leftLeg.xRot;

        VanillaSilhouettePose.assemble(block, VANILLA, POSED, weight, runWeight, gaitSpeed,
                state.ageInTicks, airborne, DRIVER.airborneProgress(AIRBORNE_WINDOW_TICKS),
                direction.forward(), direction.strafe(),
                rightThigh, leftThigh, rightUpperArm, leftUpperArm,
                DRIVER.takeOffRightLeg(), DRIVER.takeOffLeftLeg());

        for (float value : POSED) if (!Float.isFinite(value)) return false;
        body.xRot = POSED[VanillaSilhouetteDriver.BODY_X];
        body.yRot = POSED[VanillaSilhouetteDriver.BODY_Y];
        body.zRot = POSED[VanillaSilhouetteDriver.BODY_Z];
        rightArm.xRot = POSED[VanillaSilhouetteDriver.RIGHT_ARM_X];
        leftArm.xRot = POSED[VanillaSilhouetteDriver.LEFT_ARM_X];
        rightArm.zRot = POSED[VanillaSilhouetteDriver.RIGHT_ARM_Z];
        leftArm.zRot = POSED[VanillaSilhouetteDriver.LEFT_ARM_Z];
        rightLeg.xRot = POSED[VanillaSilhouetteDriver.RIGHT_LEG_X];
        leftLeg.xRot = POSED[VanillaSilhouetteDriver.LEFT_LEG_X];
        return true;
    }

    private static float mix(float walkValue, float runValue, float runWeight) {
        float weight = VanillaSilhouettePose.unit(runWeight);
        return walkValue + (runValue - walkValue) * weight;
    }
}
