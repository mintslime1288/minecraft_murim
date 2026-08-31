package com.murimrpg.infrastructure.paper;

import com.murimrpg.domain.HuashanSettings;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Isolated main-thread Paper effect adapter for the Zixia development preview. */
public final class PurpleMistEffect {
    private final HuashanSettings settings;
    public PurpleMistEffect(HuashanSettings settings) { this.settings = settings; }
    public void show(Player player) {
        player.getWorld().spawnParticle(Particle.valueOf(settings.particleType()), player.getLocation().add(0, 1, 0),
                settings.particleCount(), .6, .8, .6, .01);
    }
}
