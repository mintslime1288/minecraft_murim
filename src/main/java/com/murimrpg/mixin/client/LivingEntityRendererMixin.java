package com.murimrpg.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.murimrpg.client.render.AnimatedPlayerGlbRenderer;
import com.murimrpg.client.render.ArticulatedPlayerRenderer;
import com.murimrpg.client.render.ModelPartArticulatedPlayerRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Swaps only the base model submission; normal renderer layers and nameplates remain intact. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void murimrpg$replaceWalkingBody(SubmitNodeCollector collector, Model<?> model, Object state,
                                              PoseStack poses, RenderType renderType, int light, int overlay,
                                              int color, TextureAtlasSprite sprite, int outline,
                                              ModelFeatureRenderer.CrumblingOverlay crumbling) {
        // The Wuxia replacement is asked first and answers for the whole frame. When it takes
        // the frame nothing else submits a body, and when it declines it costs one boolean.
        if (state instanceof AvatarRenderState wuxia
                && com.murimrpg.client.render.WuxiaPlayerRenderer.shouldReplace(wuxia)) {
            com.murimrpg.client.render.WuxiaPlayerRenderer.submit(wuxia, poses, collector);
        } else if (state instanceof AvatarRenderState && (com.murimrpg.client.PalPlayerAnimationIntegration.isActive()
                || com.murimrpg.client.PalPlayerAnimationIntegration.isThirdPartyProviderActive())) {
            // PAL has already applied its layered transforms to Mojang's player model.
            // Submit that model unchanged so the legacy GLB/custom-body paths cannot
            // double-render it or reintroduce the giant-model transform failure.
            @SuppressWarnings({"rawtypes", "unchecked"}) Model raw = model;
            collector.submitModel(raw, state, poses, renderType, light, overlay, color, sprite, outline, crumbling);
        } else if (state instanceof AvatarRenderState avatar && ModelPartArticulatedPlayerRenderer.shouldReplace(avatar)) {
            if (!ModelPartArticulatedPlayerRenderer.submit(avatar, poses, collector, renderType, light, overlay, color, sprite, outline, crumbling)) {
                @SuppressWarnings({"rawtypes", "unchecked"}) Model fallback = model;
                collector.submitModel(fallback, state, poses, renderType, light, overlay, color, sprite, outline, crumbling);
            }
        } else if (state instanceof AvatarRenderState avatar && ArticulatedPlayerRenderer.shouldReplace(avatar)) {
            ArticulatedPlayerRenderer.submit(avatar, poses, collector);
        } else if (state instanceof AvatarRenderState avatar && AnimatedPlayerGlbRenderer.shouldReplace(avatar)) {
            AnimatedPlayerGlbRenderer.submit(avatar, poses, collector);
        } else {
            @SuppressWarnings({"rawtypes", "unchecked"}) Model raw = model;
            collector.submitModel(raw, state, poses, renderType, light, overlay, color, sprite, outline, crumbling);
        }
    }
}
