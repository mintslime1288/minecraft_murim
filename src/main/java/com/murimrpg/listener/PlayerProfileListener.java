package com.murimrpg.listener;

import com.murimrpg.presentation.StatusMessageFactory;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.ui.CharacterCreationGui;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerProfileListener implements Listener {
    private final PlayerProfileService profileService;
    private final ProgressionService progressionService;
    private final Executor mainThreadExecutor;
    private final StatusMessageFactory messageFactory;
    private final Logger logger;
    private final CharacterCreationGui characterGui;

    public PlayerProfileListener(
            PlayerProfileService profileService,
            ProgressionService progressionService,
            Executor mainThreadExecutor,
            StatusMessageFactory messageFactory,
            Logger logger,
            CharacterCreationGui characterGui
    ) {
        this.profileService = profileService;
        this.progressionService = progressionService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.messageFactory = messageFactory;
        this.logger = logger;
        this.characterGui = characterGui;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        profileService.beginSession(uuid);
        profileService.loadOrCreate(uuid).whenCompleteAsync((profile, error) -> {
            if (error == null) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) characterGui.openIfNeeded(player);
                return;
            }
            logger.log(Level.SEVERE, "Failed to load player profile for " + uuid, rootCause(error));
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(messageFactory.error("프로필을 불러오지 못했습니다. 관리자에게 문의하세요."));
            }
        }, mainThreadExecutor);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        progressionService.forgetPromotionRequest(uuid);
        characterGui.forget(uuid);
        profileService.saveAndUnload(uuid).whenComplete((result, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to save player profile on quit for " + uuid, rootCause(error));
            }
        });
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
