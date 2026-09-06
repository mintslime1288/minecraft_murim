package com.murimrpg.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.murimrpg.client.NaturalWalkDiagnostics;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renders the licensed Meshy character mesh for the local player in third person. */
public final class AnimatedPlayerGlbRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-AnimatedPlayer");
    private static final Identifier WALK_MODEL = Identifier.fromNamespaceAndPath("murimrpg", "models_glb/player/walking.glb");
    private static final Identifier RUN_MODEL = Identifier.fromNamespaceAndPath("murimrpg", "models_glb/player/running.glb");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("murimrpg", "dynamic/player_meshy_character");
    private static GlbAnimatedPlayerMesh walkMesh;
    private static GlbAnimatedPlayerMesh runMesh;
    private static boolean active;
    private static boolean submittedLogged;
    private static String failure = "아직 로드되지 않음";
    private static PlayerMeshSettings settings;
    private static float runBlend;
    private static float lastBlendAge = Float.NaN;
    private AnimatedPlayerGlbRenderer() { }

    public static void loadResources(Minecraft client) {
        settings = PlayerMeshSettings.load(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
        if (!settings.enabled()) { active = false; failure = "설정에서 꺼짐"; return; }
        try (var walkInput = client.getResourceManager().open(WALK_MODEL);
             var runInput = client.getResourceManager().open(RUN_MODEL)) {
            var walk = GlbAnimatedPlayerMesh.load(walkInput, WALK_MODEL.toString());
            var run = GlbAnimatedPlayerMesh.load(runInput, RUN_MODEL.toString());
            validateCompatible(walk.mesh(), run.mesh());
            NativeImage image = TrainingDummyGlbRenderer.decodeEmbeddedImage(walk.baseColorImage());
            DynamicTexture texture = new DynamicTexture(() -> "murimrpg_meshy_player", image);
            texture.upload();
            client.getTextureManager().register(TEXTURE, texture);
            walkMesh = walk.mesh(); runMesh = run.mesh(); active = true; failure = "없음";
            LOGGER.info("Loaded full Meshy player renderer: walk={}s run={}s vertices={} triangles={} joints={} scale={}/{}/{}",
                    walkMesh.durationSeconds(), runMesh.durationSeconds(), walkMesh.vertexCount(),
                    walkMesh.triangleCount(), walkMesh.jointCount(), settings.widthScale(), settings.heightScale(), settings.depthScale());
        } catch (IOException | RuntimeException error) {
            walkMesh = null; runMesh = null; active = false; failure = error.getMessage();
            LOGGER.warn("Full Meshy player renderer disabled; vanilla player renderer remains active", error);
        }
    }

    private static void validateCompatible(GlbAnimatedPlayerMesh walk, GlbAnimatedPlayerMesh run) throws IOException {
        if (walk.vertexCount() != run.vertexCount() || walk.triangleCount() != run.triangleCount()
                || walk.jointCount() != run.jointCount()) {
            throw new IOException("Walking and running GLBs use incompatible meshes or skeletons");
        }
        GlbAssetValidator.validatePlayerClip(walk, "walking.glb");
        GlbAssetValidator.validatePlayerClip(run, "running.glb");
    }

    public static boolean isActive() { return active; }
    public static String status() { return active ? "활성" : "비활성: " + failure; }

    public static boolean shouldReplace(AvatarRenderState state) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (!active || !NaturalWalkDiagnostics.enabled() || player == null || state.id != player.getId()) return false;
        // Armor cannot yet deform against this external skeleton. Preserve the complete
        // vanilla renderer rather than showing clipping or silently hiding equipment.
        return !state.isCrouching && !state.isFallFlying && !state.isVisuallySwimming && !state.isPassenger
                && !state.isUsingItem && state.attackTime <= .01f && state.deathTime == 0
                && state.headEquipment.isEmpty() && state.chestEquipment.isEmpty()
                && state.legsEquipment.isEmpty() && state.feetEquipment.isEmpty();
    }

    public static void submit(AvatarRenderState state, PoseStack poses, SubmitNodeCollector collector) {
        var player = Minecraft.getInstance().player;
        boolean running = player != null && player.isSprinting();
        float phase = state.walkAnimationSpeed > .03f ? state.walkAnimationPos : 0f;
        float targetRunWeight = running && state.walkAnimationSpeed > .03f ? 1f : 0f;
        float elapsed = Float.isFinite(lastBlendAge) ? Math.clamp((state.ageInTicks - lastBlendAge) / 20f, 0f, .10f) : 0f;
        lastBlendAge = state.ageInTicks;
        float maxStep = elapsed / settings.locomotionBlendSeconds();
        runBlend += Math.clamp(targetRunWeight - runBlend, -maxStep, maxStep);
        // LivingEntityRenderer has already converted entity space to its inverted model
        // space. Restore metre-based Y-up space; the GLB is validated at 1.8 m tall.
        poses.pushPose();
        poses.translate(0f, settings.groundOffset(), 0f);
        // The Minecraft humanoid render stack uses Y-down model space. GLB is Y-up, so
        // compensate only the vertical axis; this keeps the player's facing direction intact.
        if (settings.flipVertical()) poses.scale(1f, -1f, 1f);
        // Meshy's exported forward axis is opposite the Minecraft avatar forward axis.
        // Keep this calibration in YAML so a future re-export can adjust it without code edits.
        poses.mulPose(new Quaternionf().rotateY((float) Math.toRadians(settings.yawDegrees())));
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(TEXTURE), (pose, vertices) -> {
            if (runBlend <= .001f) {
                walkMesh.render(pose, vertices, state.lightCoords, phase,
                        settings.widthScale(), settings.heightScale(), settings.depthScale());
            } else if (runBlend >= .999f) {
                runMesh.render(pose, vertices, state.lightCoords, phase,
                        settings.widthScale(), settings.heightScale(), settings.depthScale());
            } else {
                walkMesh.renderBlended(pose, vertices, state.lightCoords, phase, runMesh, phase, runBlend,
                        settings.widthScale(), settings.heightScale(), settings.depthScale());
            }
        });
        poses.popPose();
        if (!submittedLogged) {
            submittedLogged = true;
            LOGGER.info("Submitted full Meshy player geometry with walk/run state blending.");
        }
    }
}
