package com.murimrpg.ui;

import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.service.HuashanService;
import java.util.concurrent.Executor;
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
    private final GanghoMenuGui menuGui;
    private final ContentCatalog contentCatalog;
    private final QuestSidebar questSidebar;
    private final BukkitTask task;

    public GanghoUiManager(JavaPlugin plugin, PlayerProfileService profiles, PlayerContentService content,
            ContentCatalog contentCatalog, ProgressionService progression, HuashanService huashan, Executor mainThread) {
        this.profiles = profiles;
        this.hud = new GanghoHud(profiles, content, progression.progression(), contentCatalog);
        this.profileGui = new GanghoProfileGui();
        this.menuGui = new GanghoMenuGui(contentCatalog, progression, huashan, content, profiles, profileGui, mainThread);
        this.contentCatalog = contentCatalog;
        this.questSidebar = new QuestSidebar(content, contentCatalog);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, HUD_PERIOD_TICKS, HUD_PERIOD_TICKS);
    }

    public GanghoProfileGui profileGui() {
        return profileGui;
    }

    public GanghoMenuGui menuGui() { return menuGui; }

    public boolean openMenu(Player player, PlayerProfile profile, PlayerJourney journey) {
        if (!profile.characterData().completed()) return false;
        menuGui.openMain(player, profile, journey);
        return true;
    }

    public boolean openProfile(Player player, PlayerProfile profile, PlayerJourney journey) {
        if (!profile.characterData().completed()) return false;
        profileGui.open(player, profile, journey, journey.content().organizationId()
                .flatMap(contentCatalog.factions()::findOrganization));
        return true;
    }

    public void shutdown() {
        task.cancel();
        hud.clear();
        questSidebar.clearAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hud.forget(event.getPlayer().getUniqueId());
        questSidebar.clear(event.getPlayer());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hud.tick(player);
            questSidebar.tick(player);
        }
    }
}
