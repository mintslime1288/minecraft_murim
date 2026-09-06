package com.murimrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

/** Local-only footstep dust. It sends no network traffic and changes no movement. */
public final class LocalMovementVisualService {
    private final ClientVisualSettings settings;
    private int nextFootstepTick;
    public LocalMovementVisualService(ClientVisualSettings settings) { this.settings = settings; }
    public void tick(Minecraft client) {
        var player = client.player;
        if (client.level == null || player == null || settings.quality() == ClientVisualSettings.Quality.LOW || player.isSpectator()
                || !player.onGround() || player.isCrouching() || player.isSwimming() || player.isFallFlying()) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() < .004 || player.tickCount < nextFootstepTick) return;
        nextFootstepTick = player.tickCount + settings.footstepIntervalTicks();
        client.level.addParticle(ParticleTypes.POOF, player.getX(), player.getY() + .03, player.getZ(), 0, .012, 0);
    }
}
