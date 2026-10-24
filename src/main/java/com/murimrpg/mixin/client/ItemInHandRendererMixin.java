package com.murimrpg.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.murimrpg.client.render.scabbard.VirtualScabbardRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person virtual scabbard in the empty off hand.
 *
 * <p>Vanilla skips the off hand entirely when its stack is empty, so there is nothing to
 * intercept mid-method; the scabbard is added after both hands are done. Rather than positioning
 * the model, this replays vanilla's own off-hand invocation with the render-only scabbard stack
 * in place of the inventory stack. Everything else - the equip height, the attack swing offset,
 * the arm transform and the {@code firstperson_lefthand} display entry - is therefore vanilla's.
 *
 * <p>The equip progress is computed exactly as vanilla computes it for the off hand. With an
 * empty off hand the height sits at 1, so the progress is 0 and the scabbard is fully raised
 * instead of sliding in and out every frame.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow @Final private ItemModelResolver itemModelResolver;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;

    @Invoker("submitArmWithItem")
    abstract void murimrpg$submitArmWithItem(AbstractClientPlayer player, float partialTick, float xRot,
                                             InteractionHand hand, float swingProgress, ItemStack stack,
                                             float equipProgress, PoseStack poses,
                                             SubmitNodeCollector collector, int light);

    @Inject(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("TAIL"))
    private void murimrpg$submitVirtualScabbard(float partialTick, PoseStack poses,
                                                SubmitNodeCollector collector, LocalPlayer player,
                                                int light, CallbackInfo callback) {
        ItemStack scabbard = VirtualScabbardRenderer.firstPersonOffHandStack(player);
        if (scabbard.isEmpty()) return;
        // The scabbard is never the swinging hand: the sword is in the main hand, and a swing of
        // the off hand would mean the player is using the off hand, which the visibility rules
        // already exclude. Passing 0 keeps the scabbard still during an attack.
        float equipProgress = this.itemModelResolver.swapAnimationScale(scabbard)
                * (1f - Mth.lerp(partialTick, this.oOffHandHeight, this.offHandHeight));
        murimrpg$submitArmWithItem(player, partialTick, player.getXRot(partialTick),
                InteractionHand.OFF_HAND, 0f, scabbard, equipProgress, poses, collector, light);
    }
}
