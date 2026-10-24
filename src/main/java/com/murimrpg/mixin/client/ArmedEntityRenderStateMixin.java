package com.murimrpg.mixin.client;

import com.murimrpg.client.render.scabbard.VirtualScabbardRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person virtual scabbard, for the local player in F5 and for other players alike.
 *
 * <p>Tail-injected into the armed-entity extraction so vanilla has already filled both hands
 * from the real inventory. Only the off arm's render state is added, and only when vanilla left
 * it empty. The off arm's real {@code ItemStack} is untouched, so the arm pose and every
 * gameplay reader still see an empty hand.
 *
 * <p>This extraction runs for every armed entity, not only players, so the renderer itself
 * checks the entity type. Both self and other players go through this one path, which is what
 * keeps the two views consistent without a second code path to keep in step.
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {

    @Inject(method = "extractArmedEntityRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemModelResolver;F)V",
            at = @At("TAIL"))
    private static void murimrpg$addVirtualScabbard(LivingEntity entity, ArmedEntityRenderState state,
                                                    ItemModelResolver resolver, float partialTick,
                                                    CallbackInfo callback) {
        VirtualScabbardRenderer.fillThirdPersonOffHand(entity, state, resolver);
    }
}
