package com.murimrpg.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;

/** Snapshot used solely to choose safe visual arm adjustments. */
public record PlayerAnimationState(boolean moving, boolean sprinting, boolean idle, boolean meleeAttacking,
        boolean swordHeld, boolean crouching, boolean swimming, boolean passenger, boolean usingItem) {
    public static PlayerAnimationState from(Player player, AvatarRenderState state) {
        // Some 26.2 render paths leave walkAnimationSpeed at zero even while the
        // player is moving. Use the local physics velocity only as a visual state
        // fallback; it never changes server movement or collision.
        boolean moving = state.walkAnimationSpeed > .03f
                || player.getDeltaMovement().horizontalDistance() > .005;
        boolean sword = player.getMainHandItem().getItem().builtInRegistryHolder().is(ItemTags.SWORDS);
        return new PlayerAnimationState(moving, player.isSprinting(), !moving, state.attackTime > .01f, sword,
                state.isCrouching, state.isVisuallySwimming, state.isPassenger, state.isUsingItem);
    }
}
