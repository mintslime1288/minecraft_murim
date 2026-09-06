package com.murimrpg.service;

import com.murimrpg.domain.EffectCatalog;
import com.murimrpg.domain.EffectDefinition;
import com.murimrpg.network.EffectPlayPayload;
import com.murimrpg.network.MurimRpgNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Sends server-authorized presentation events only. No client event can mutate game state. */
public final class VisualEffectService {
    private final EffectCatalog catalog;
    public VisualEffectService(EffectCatalog catalog) { this.catalog = catalog; }
    public void play(ServerPlayer player, String effectId, Entity at) { play(player, effectId, at.getX(), at.getY() + at.getBbHeight() * .55, at.getZ()); }
    public void play(ServerPlayer player, String effectId, double x, double y, double z) {
        EffectDefinition definition = catalog.find(effectId).orElse(null);
        if (definition == null) return;
        MurimRpgNetworking.sendEffect(player, new EffectPlayPayload(effectId, x, y, z, definition.scale(), definition.durationTicks(), definition.particleCount(), player.getRandom().nextLong()));
    }
    public EffectCatalog catalog() { return catalog; }
}
