package com.murimrpg.mixin.client;

import com.murimrpg.client.MurimRpgClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only and tail-injected, so the normal Java Edition player model remains authoritative. */
@Mixin(HumanoidModel.class)
public abstract class PlayerModelMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart body;
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void murimrpg$applySubtleMotion(HumanoidRenderState renderState, CallbackInfo callback) {
        if (!(renderState instanceof AvatarRenderState state)) return;
        // The Wuxia mesh replaces this submission outright, so there is nothing to pose here.
        if (com.murimrpg.client.render.WuxiaPlayerRenderer.shouldReplace(state)) return;
        if (com.murimrpg.client.PalPlayerAnimationIntegration.isActive()
                || com.murimrpg.client.PalPlayerAnimationIntegration.isThirdPartyProviderActive()) return;
        if (com.murimrpg.client.render.AnimatedPlayerGlbRenderer.shouldReplace(state)) return;
        // The vanilla-silhouette path poses Mojang's own parts. It runs before the older
        // natural-walk path and takes the frame when it applies, so the two never stack.
        if (com.murimrpg.client.render.VanillaSilhouetteRenderer.apply(state, body, rightArm, leftArm, rightLeg, leftLeg)) return;
        if (com.murimrpg.client.render.VanillaSilhouetteRenderer.active()) return;
        // The articulated model replaces this submission entirely; avoid posing Mojang's
        // body too, which only adds work and confuses renderer diagnostics.
        if (com.murimrpg.client.render.ModelPartArticulatedPlayerRenderer.shouldReplace(state)) {
            // The one exception to leaving this model alone: the held-item layer places the item
            // from THIS model's arm, not the submitted one, so pointing these two at the
            // articulated hand is what keeps a sword in the hand. Nothing else here is touched and
            // this model is not drawn while the split one is submitted, so there is no double render.
            com.murimrpg.client.render.ModelPartArticulatedPlayerRenderer.anchorHeldItemArms(state, rightArm, leftArm);
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(state.id) instanceof Player player)) {
            com.murimrpg.client.NaturalWalkDiagnostics.record(state.id, "플레이어 조회 실패", state.walkAnimationSpeed, state.walkAnimationPos);
            return;
        }
        if (MurimRpgClient.playerAnimations() != null) MurimRpgClient.playerAnimations().apply(player, state, rightArm, leftArm, rightLeg, leftLeg, body);
    }
}
