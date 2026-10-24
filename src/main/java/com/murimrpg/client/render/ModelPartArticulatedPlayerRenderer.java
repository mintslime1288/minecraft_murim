package com.murimrpg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** Safe native-model pipeline for the articulated local-player body. */
public final class ModelPartArticulatedPlayerRenderer {
    private static ArticulatedHumanoidModel model;
    private static AuthoredRigWalkAnimation walkAnimation;
    private static AuthoredRigWalkAnimation runAnimation;
    private static AuthoredRigWalkAnimation jumpAnimation;
    private static ArticulatedPlayerSettings settings;
    private static final ArticulatedJumpTracker JUMP_TRACKER = new ArticulatedJumpTracker();
    private static boolean reversed;
    private static boolean active;
    private static boolean loggedSubmit;
    private static boolean loggedFallback;
    private ModelPartArticulatedPlayerRenderer() { }
    public static void loadResources(Minecraft client) {
        loggedSubmit = false;
        loggedFallback = false;
        reversed = false;
        try {
            settings = ArticulatedPlayerSettings.load(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
            walkAnimation = AuthoredRigWalkAnimation.load(client,
                    Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_walk_v6_torso.json"), "walk-torso-v6");
            runAnimation = AuthoredRigWalkAnimation.load(client,
                    Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_run_v3_torso.json"), "run-torso-v3");
            jumpAnimation = AuthoredRigWalkAnimation.load(client,
                    Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_jump.json"), "jump");
            active = settings.enabled();
            model = active ? new ArticulatedHumanoidModel(settings, walkAnimation, runAnimation, jumpAnimation) : null;
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                    "Run arm inertia: enabled={}, followTicks={}, maxLagDegrees={}",
                    active && settings.torsoHipPivot() && settings.runArmFollowTicks() > 0f && settings.runArmLagDegrees() > 0f,
                    settings.runArmFollowTicks(), settings.runArmLagDegrees());
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                    "Locomotion style: run={}, shoulderYaw={}, idle={}", settings.motionStyle().runEnabled(),
                    settings.motionStyle().torsoEnabled(), settings.motionStyle().idleEnabled());
            VanillaSilhouetteRenderer.reset();
            if (settings.combatMotionFailure() != null) {
                org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").warn(
                        "Combat motion disabled for this run: {}", settings.combatMotionFailure());
            }
            if (settings.combatMotion().stanceFailure() != null) {
                org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").warn(
                        "Barehand combat stance disabled for this run: {}", settings.combatMotion().stanceFailure());
            }
            if (settings.combatMotion().logOnce()) {
                org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                        "Combat motion: enabled={}, barehand={}, sword={}, handAnchor={}",
                        settings.combatMotion().enabled(), settings.combatMotion().barehandEnabled(),
                        settings.combatMotion().swordEnabled(), settings.combatMotion().handAnchorEnabled());
                org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                        "Barehand stance: enabled={}, holdTicks={}, blendIn={}, blendOut={}",
                        settings.combatMotion().stance().enabled(), settings.combatMotion().stance().holdTicks(),
                        settings.combatMotion().stance().blendInTicks(), settings.combatMotion().stance().blendOutTicks());
            }
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                    "Vanilla silhouette: enabled={}, gait={}, idle={}, jump={} (split joint model eligible={})",
                    settings.vanillaSilhouette().enabled(), settings.vanillaSilhouette().gaitEnabled(),
                    settings.vanillaSilhouette().idleEnabled(), settings.vanillaSilhouette().jumpEnabled(),
                    active && !settings.vanillaSilhouette().enabled());
        } catch (Exception error) {
            active = false; model = null; walkAnimation = null; runAnimation = null; jumpAnimation = null;
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").warn("Authored Blender locomotion unavailable; vanilla fallback remains active", error);
        }
    }
    /** Package-private views so the vanilla-silhouette path can reuse the one resource load. */
    static ArticulatedPlayerSettings settings() { return settings; }

    /**
     * Points Mojang's one-piece arm at the split model's hand so a held item lands on it.
     *
     * <p>ItemInHandLayer.submitArmWithItem calls getParentModel().translateToHand(...), and the
     * parent model is the renderer's PlayerModel, not the model this class submits in its place -
     * the redirect swaps only the base submitModel call. HumanoidModel.translateToHand is
     * root.translateAndRotate then arm.translateAndRotate, so the item follows that arm's rotation
     * and nothing else. Writing to it moves the item only: this model is never drawn while the
     * split one is submitted, so there is no second body and nothing to z-fight.
     *
     * @return true when the arm was written, so the caller must not also run the older path
     */
    public static boolean anchorHeldItemArms(AvatarRenderState state, net.minecraft.client.model.geom.ModelPart rightArm,
                                             net.minecraft.client.model.geom.ModelPart leftArm) {
        if (model == null || settings == null || rightArm == null || leftArm == null) return false;
        if (!settings.combatMotion().handAnchorEnabled()) return false;
        if (!shouldReplace(state)) return false;
        float rightDirection = model.handDirection(true);
        float leftDirection = model.handDirection(false);
        if (!Float.isFinite(rightDirection) || !Float.isFinite(leftDirection)) return false;
        rightArm.xRot = rightDirection;
        leftArm.xRot = leftDirection;
        rightArm.zRot = model.shoulderRoll(true);
        leftArm.zRot = model.shoulderRoll(false);
        rightArm.yRot = 0f;
        leftArm.yRot = 0f;
        return true;
    }
    static AuthoredRigWalkAnimation walkAnimation() { return walkAnimation; }
    static AuthoredRigWalkAnimation runAnimation() { return runAnimation; }

    public static boolean shouldReplace(AvatarRenderState state) {
        var player = Minecraft.getInstance().player;
        // The vanilla-silhouette block owns the body outright while it is on: Mojang's model is
        // submitted in every state and this split model is never submitted at all, so the two can
        // never be drawn together and there is nothing to z-fight with.
        if (settings != null && settings.vanillaSilhouette().enabled()) return false;
        // The attack gate is the whole reason the joints used to vanish the moment you swung. With
        // the combat block on it is dropped, so the split model keeps being submitted through the
        // swing; with it off the condition is exactly what it has always been. Everything else -
        // item use, swimming, riding, death, armour - keeps its existing fallback untouched.
        boolean combat = settings != null && settings.combatMotion().enabled();
        boolean replace = active && model != null && player != null && state.id == player.getId()
                && !state.isCrouching && !state.isFallFlying && !state.isVisuallySwimming && !state.isPassenger
                && !state.isUsingItem && (combat || state.attackTime <= .01f) && state.deathTime == 0
                && state.headEquipment.isEmpty() && state.chestEquipment.isEmpty() && state.legsEquipment.isEmpty() && state.feetEquipment.isEmpty();
        if (!replace && model != null && player != null && state.id == player.getId()) {
            model.resetArmInertia();
            model.resetCombat();
        }
        return replace;
    }
    /** @return true when the articulated model was submitted; false means the caller must draw vanilla. */
    public static boolean submit(AvatarRenderState state, PoseStack poses, SubmitNodeCollector collector, RenderType ignored,
                                 int light, int overlay, int color, TextureAtlasSprite sprite, int outline,
                                 ModelFeatureRenderer.CrumblingOverlay crumbling) {
        if (model == null || settings == null) return false;
        var player = Minecraft.getInstance().player;
        double horizontalSpeed = player == null ? 0d : Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z);
        boolean running = player != null && player.isSprinting();
        var direction = direction(player);
        reversed = direction.reversed();
        double directionSpeed = Math.hypot(direction.forward(), direction.strafe());
        model.setForwardRunWeight(directionSpeed > 0d
                ? (float) Math.max(0d, direction.forward() / directionSpeed) : 0f);
        var jumpPhase = jumpPhase(player, state.ageInTicks);
        model.setLocomotion(running, running && horizontalSpeed >= settings.fastRunHorizontalSpeed(),
                direction.reversed(), direction.gaitWeight());
        model.setJumpPhase(jumpPhase, JUMP_TRACKER.landingProgress(), JUMP_TRACKER.priorPhase(),
                JUMP_TRACKER.phaseProgress(), JUMP_TRACKER.airborneProgress());
        // attackArm is already the swinging hand; SwingAnimationType is not usable to tell a sword
        // from a fist because ItemStack.getSwingAnimation defaults to WHACK for everything, so the
        // classification is simply whether that hand is holding anything.
        boolean rightHanded = state.attackArm != net.minecraft.world.entity.HumanoidArm.LEFT;
        var attackStack = rightHanded ? state.rightHandItemStack : state.leftHandItemStack;
        model.setBothHandsEmpty((state.rightHandItemStack == null || state.rightHandItemStack.isEmpty())
                && (state.leftHandItemStack == null || state.leftHandItemStack.isEmpty()));
        model.setAttack(state.attackTime, rightHanded, attackStack != null && !attackStack.isEmpty(),
                player != null && player.swinging);
        model.setupAnim(state);
        if (!model.poseValid()) {
            logOnce(false, "관절 포즈 검증 실패(비유한값/범위 초과). 바닐라 모델로 폴백");
            return false;
        }
        @SuppressWarnings({"rawtypes", "unchecked"}) Model raw = model;
        collector.submitModel(raw, state, poses, model.renderType(state.skin.body().texturePath()), light, overlay, color, sprite, outline, crumbling);
        logOnce(true, "관절 ModelPart 제출 확인(최종 렌더 단계)");
        return true;
    }
    /** One line per client run, never per frame, and only when diagnostics.log-once is true. */
    private static void logOnce(boolean submitted, String message) {
        if (settings == null || !settings.logOnce()) return;
        if (submitted ? loggedSubmit : loggedFallback) return;
        if (submitted) loggedSubmit = true; else loggedFallback = true;
        org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(message);
    }
    public static String status() { return active ? "활성(ModelPart 관절 리그)" : "설정에서 꺼짐"; }

    // yBodyRot is the body yaw rather than the head yaw, so looking around while walking
    // must not flip the gait. This reads client state only; nothing is written back.
    private static LocomotionDirection direction(net.minecraft.client.player.LocalPlayer player) {
        if (player == null || settings == null) {
            return LocomotionDirection.of(0d, 0d, 0f, 0f, 0f, reversed);
        }
        var velocity = player.getDeltaMovement();
        return LocomotionDirection.of(velocity.x, velocity.z, player.yBodyRot,
                settings.strafeGaitWeight(), settings.reverseDeadband(), reversed);
    }

    private static ArticulatedJumpTracker.Phase jumpPhase(net.minecraft.client.player.LocalPlayer player, float ageInTicks) {
        if (player == null || settings == null) return ArticulatedJumpTracker.Phase.GROUND;
        return JUMP_TRACKER.update(ageInTicks, player.onGround(), player.getDeltaMovement().y,
                settings.jumpRiseVelocityThreshold(), settings.jumpApexVelocityThreshold(),
                settings.jumpLandingDurationTicks(), settings.jumpMinimumAirborneTicks(),
                settings.jumpPhaseBlendTicks(), settings.jumpTakeOffBlendTicks());
    }
}
