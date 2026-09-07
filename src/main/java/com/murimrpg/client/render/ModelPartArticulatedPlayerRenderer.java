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
    private static boolean active;
    private static boolean loggedSubmit;
    private static boolean loggedFallback;
    private ModelPartArticulatedPlayerRenderer() { }
    public static void loadResources(Minecraft client) {
        loggedSubmit = false;
        loggedFallback = false;
        try {
            settings = ArticulatedPlayerSettings.load(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
            walkAnimation = AuthoredRigWalkAnimation.load(client,
                    Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_walk.json"), "walk");
            runAnimation = AuthoredRigWalkAnimation.load(client,
                    Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_run.json"), "run");
            jumpAnimation = AuthoredRigWalkAnimation.load(client,
                    Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_jump.json"), "jump");
            active = settings.enabled();
            model = active ? new ArticulatedHumanoidModel(settings, walkAnimation, runAnimation, jumpAnimation) : null;
        } catch (Exception error) {
            active = false; model = null; walkAnimation = null; runAnimation = null; jumpAnimation = null;
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").warn("Authored Blender locomotion unavailable; vanilla fallback remains active", error);
        }
    }
    public static boolean shouldReplace(AvatarRenderState state) {
        var player = Minecraft.getInstance().player;
        return active && model != null && player != null && state.id == player.getId()
                && !state.isCrouching && !state.isFallFlying && !state.isVisuallySwimming && !state.isPassenger
                && !state.isUsingItem && state.attackTime <= .01f && state.deathTime == 0
                && state.headEquipment.isEmpty() && state.chestEquipment.isEmpty() && state.legsEquipment.isEmpty() && state.feetEquipment.isEmpty();
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
        var jumpPhase = jumpPhase(player, state.ageInTicks);
        model.setLocomotion(running, running && horizontalSpeed >= settings.fastRunHorizontalSpeed());
        model.setJumpPhase(jumpPhase, JUMP_TRACKER.landingProgress());
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

    private static ArticulatedJumpTracker.Phase jumpPhase(net.minecraft.client.player.LocalPlayer player, float ageInTicks) {
        if (player == null || settings == null) return ArticulatedJumpTracker.Phase.GROUND;
        return JUMP_TRACKER.update(ageInTicks, player.onGround(), player.getDeltaMovement().y,
                settings.jumpRiseVelocityThreshold(), settings.jumpApexVelocityThreshold(),
                settings.jumpLandingDurationTicks(), settings.jumpMinimumAirborneTicks());
    }
}
