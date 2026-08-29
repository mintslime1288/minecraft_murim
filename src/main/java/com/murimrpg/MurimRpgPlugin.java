package com.murimrpg;

import com.murimrpg.command.MurimCommand;
import com.murimrpg.command.GanghoCommand;
import com.murimrpg.domain.GanghoCommandCatalog;
import com.murimrpg.infrastructure.config.GanghoCommandCatalogLoader;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import com.murimrpg.infrastructure.paper.BukkitMainThreadExecutor;
import com.murimrpg.infrastructure.paper.PromotionFeedback;
import com.murimrpg.infrastructure.paper.PromotionFeedbackSettings;
import com.murimrpg.infrastructure.sqlite.SqlitePlayerRepository;
import com.murimrpg.listener.PlayerProfileListener;
import com.murimrpg.presentation.StatusMessageFactory;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.service.StatusService;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.service.CharacterRollService;
import com.murimrpg.service.CharacterSettings;
import com.murimrpg.ui.CharacterCreationGui;
import com.murimrpg.ui.GanghoUiManager;
import java.util.random.RandomGenerator;
import java.time.Clock;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MurimRpgPlugin extends JavaPlugin {
    public static final String PAPER_API_VERSION = "26.2.build.112-stable";
    private static final int EXPECTED_CULTIVATION_STAGE_COUNT = 32;
    private static final Duration DATABASE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private PlayerProfileService profileService;
    private GanghoUiManager uiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("skills.yml");
        saveResourceIfMissing("realms.yml");
        saveResourceIfMissing("character.yml");
        saveResourceIfMissing("gangho.yml");

        Logger logger = getLogger();
        CultivationProgression progression;
        try {
            progression = new CultivationConfigLoader().load(
                    getDataFolder().toPath().resolve("realms.yml"),
                    EXPECTED_CULTIVATION_STAGE_COUNT
            );
        } catch (IllegalArgumentException exception) {
            logger.log(Level.SEVERE, "Invalid realms.yml; MurimRPG cannot start safely.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        PromotionFeedbackSettings feedbackSettings;
        try {
            feedbackSettings = new PromotionFeedbackSettings(
                    getConfig().getBoolean("promotion-feedback.title-enabled", true),
                    getConfig().getBoolean("promotion-feedback.sound-enabled", true),
                    getConfig().getBoolean("promotion-feedback.particles-enabled", true),
                    getConfig().getBoolean("promotion-feedback.chat-enabled", true),
                    getConfig().getInt("promotion-feedback.particle-count", 18)
            );
        } catch (IllegalArgumentException exception) {
            logger.log(Level.SEVERE, "Invalid promotion-feedback configuration; MurimRPG cannot start safely.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        StatusService statusService = new StatusService(
                getPluginMeta().getName(),
                getPluginMeta().getVersion(),
                PAPER_API_VERSION
        );
        statusService.setDataStoreStatus("initializing");

        ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "MurimRPG-Database");
            thread.setDaemon(false);
            return thread;
        });
        SqlitePlayerRepository repository = new SqlitePlayerRepository(
                getDataFolder().toPath().resolve("murimrpg.db"),
                logger
        );
        profileService = new PlayerProfileService(repository, databaseExecutor, Clock.systemUTC());
        CharacterCreationService characterCreationService = new CharacterCreationService(profileService,
                new CharacterRollService(CharacterSettings.from(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("character.yml").toFile())), RandomGenerator.getDefault()));
        ProgressionService progressionService = new ProgressionService(profileService, progression);
        GanghoCommandCatalog ganghoCatalog;
        try {
            ganghoCatalog = new GanghoCommandCatalogLoader().load(getDataFolder().toPath().resolve("gangho.yml").toFile());
        } catch (IllegalArgumentException exception) {
            logger.log(Level.SEVERE, "Invalid gangho.yml; MurimRPG cannot start safely.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        profileService.initialize().whenComplete((result, error) -> {
            if (error == null) {
                statusService.setDataStoreStatus("configured (SQLite)");
                logger.info("SQLite player profile storage initialized (schema version 1).");
                return;
            }
            statusService.setDataStoreStatus("error");
            logger.log(Level.SEVERE, "Failed to initialize SQLite player profile storage.", rootCause(error));
        });

        StatusMessageFactory messageFactory = new StatusMessageFactory();
        PromotionFeedback promotionFeedback = new PromotionFeedback(feedbackSettings);
        Executor mainThreadExecutor = new BukkitMainThreadExecutor(this);
        CharacterCreationGui characterGui = new CharacterCreationGui(characterCreationService, profileService, mainThreadExecutor, logger);
        uiManager = new GanghoUiManager(this, profileService);
        MurimCommand murimCommand = new MurimCommand(
                statusService,
                profileService,
                progressionService,
                messageFactory,
                promotionFeedback,
                mainThreadExecutor,
                logger,
                characterCreationService,
                characterGui
        );
        PluginCommand command = Objects.requireNonNull(
                getCommand("murim"),
                "The murim command must be declared in plugin.yml"
        );
        command.setExecutor(murimCommand);
        command.setTabCompleter(murimCommand);
        GanghoCommand ganghoCommand = new GanghoCommand(ganghoCatalog, profileService, progressionService,
                characterCreationService, characterGui, mainThreadExecutor, uiManager);
        for (String commandName : java.util.List.of("gangho", "gangho_profile", "gangho_sect", "gangho_martial",
                "gangho_qi", "gangho_meditate", "gangho_realm", "gangho_quest", "gangho_encounter",
                "gangho_codex", "gangho_map", "gangho_region", "gangho_route", "gangho_admin", "gangho_dev")) {
            PluginCommand registered = Objects.requireNonNull(getCommand(commandName),
                    "The " + commandName + " command must be declared in plugin.yml");
            registered.setExecutor(ganghoCommand);
            registered.setTabCompleter(ganghoCommand);
        }
        Bukkit.getPluginManager().registerEvents(
                new PlayerProfileListener(
                        profileService,
                        progressionService,
                        mainThreadExecutor,
                        messageFactory,
                        logger,
                        characterGui
                ),
                this
        );
        Bukkit.getPluginManager().registerEvents(characterGui, this);
        Bukkit.getPluginManager().registerEvents(uiManager, this);
        Bukkit.getPluginManager().registerEvents(uiManager.profileGui(), this);

        getLogger().info("MurimRPG enabled (version " + getPluginMeta().getVersion()
                + ", Paper API " + PAPER_API_VERSION + ", cultivation stages "
                + progression.stages().size() + ").");
    }

    @Override
    public void onDisable() {
        boolean profilesFlushed = true;
        if (uiManager != null) uiManager.shutdown();
        if (profileService != null) {
            try {
                profileService.shutdown(DATABASE_SHUTDOWN_TIMEOUT);
            } catch (InterruptedException exception) {
                profilesFlushed = false;
                Thread.currentThread().interrupt();
                getLogger().log(Level.SEVERE, "Interrupted while flushing player profiles during shutdown.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                profilesFlushed = false;
                getLogger().log(Level.SEVERE, "Could not safely flush every player profile during shutdown.", exception);
            }
        }
        if (profilesFlushed) {
            getLogger().info("MurimRPG disabled cleanly after flushing player profiles.");
        } else {
            getLogger().severe("MurimRPG disabled with player profile flush errors; inspect the log above.");
        }
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void saveResourceIfMissing(String resourceName) {
        Path destination = getDataFolder().toPath().resolve(resourceName);
        if (Files.notExists(destination)) {
            saveResource(resourceName, false);
        }
    }
}
