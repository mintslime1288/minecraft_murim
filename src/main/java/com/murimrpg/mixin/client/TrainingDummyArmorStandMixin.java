package com.murimrpg.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.murimrpg.client.render.TrainingDummyGlbRenderer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric's renderer registry adds factories but does not replace vanilla's armor-stand factory.
 * This client-only hook therefore swaps rendering only after the synchronized CMD 32003 head
 * item proves it is a MurimRPG training dummy. Ordinary armor stands stay vanilla.
 */
@Mixin(ArmorStandRenderer.class)
public abstract class TrainingDummyArmorStandMixin {
    private static final Set<ArmorStandRenderState> GLB_STATES = Collections.newSetFromMap(new WeakHashMap<>());

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V", at = @At("TAIL"))
    private void murimrpg$markTrainingDummy(ArmorStand stand, ArmorStandRenderState state, float partialTick, CallbackInfo callback) {
        if (TrainingDummyGlbRenderer.isActive() && TrainingDummyGlbRenderer.isTrainingDummyVisual(stand)) {
            GLB_STATES.add(state);
            TrainingDummyGlbRenderer.logTaggedDummyDetected();
        } else {
            GLB_STATES.remove(state);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void murimrpg$submitTrainingDummyGlb(ArmorStandRenderState state, PoseStack poses,
                                                  SubmitNodeCollector collector, CameraRenderState camera,
                                                  CallbackInfo callback) {
        if (!GLB_STATES.contains(state)) return;

        // The game-ready Meshy model is 2.0 blocks tall and about 0.436 blocks wide. Fit it to
        // the existing armor-stand hitbox: 1.75 blocks high and about 0.70 blocks wide.
        poses.pushPose();
        poses.scale(1.60f, 0.875f, 1.60f);
        // The pillar is an opaque asset, but its small end-cap triangles must be visible from
        // both sides. entityCutout is the non-culling opaque-friendly entity pass; it avoids
        // the translucent sorting path while preventing the missing-cap wedges seen with
        // entitySolid's one-sided geometry pass.
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(TrainingDummyGlbRenderer.texture()),
                (pose, vertices) -> TrainingDummyGlbRenderer.mesh().render(pose, vertices, state.lightCoords));
        poses.popPose();
        TrainingDummyGlbRenderer.logGeometrySubmitted();
        callback.cancel();
    }
}
