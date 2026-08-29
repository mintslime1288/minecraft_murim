package com.murimrpg.ui;

import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.service.PlayerProfileService;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Restores the persistent in-game UI after the one-time character creation screen is complete. */
public final class GanghoUiManager implements Listener {
    private static final long HUD_PERIOD_TICKS = 10L;
    private final PlayerProfileService profiles;
    private final GanghoHud hud;
    private final GanghoProfileGui profileGui;
    private final BukkitTask task;

    public GanghoUiManager(JavaPlugin plugin, PlayerProfileService profiles) {
        this.profiles = profiles;
        this.hud = new GanghoHud(profiles);
        this.profileGui = new GanghoProfileGui();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, HUD_PERIOD_TICKS, HUD_PERIOD_TICKS);
    }

    public GanghoProfileGui profileGui() {
        return profileGui;
    }

    public boolean openProfile(Player player, PlayerProfile profile) {
        if (!profile.characterData().completed()) return false;
        profileGui.open(player, profile);
        return true;
    }

    public void shutdown() {
        task.cancel();
        hud.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hud.forget(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) hud.tick(player);
    }
}
