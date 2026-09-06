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
        if (com.murimrpg.client.PalPlayerAnimationIntegration.isActive()
                || com.murimrpg.client.PalPlayerAnimationIntegration.isThirdPartyProviderActive()) return;
        if (com.murimrpg.client.render.AnimatedPlayerGlbRenderer.shouldReplace(state)) return;
        // The articulated model replaces this submission entirely; avoid posing Mojang's
        // body too, which only adds work and confuses renderer diagnostics.
        if (com.murimrpg.client.render.ModelPartArticulatedPlayerRenderer.shouldReplace(state)) return;
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(state.id) instanceof Player player)) {
            com.murimrpg.client.NaturalWalkDiagnostics.record(state.id, "플레이어 조회 실패", state.walkAnimationSpeed, state.walkAnimationPos);
            return;
        }
        if (MurimRpgClient.playerAnimations() != null) MurimRpgClient.playerAnimations().apply(player, state, rightArm, leftArm, rightLeg, leftLeg, body);
    }
}
