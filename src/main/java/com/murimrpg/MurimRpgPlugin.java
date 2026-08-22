package com.murimrpg;

import com.murimrpg.command.MurimCommand;
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
    private static final Duration DATABASE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private PlayerProfileService profileService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("skills.yml");
        saveResourceIfMissing("realms.yml");

        Logger logger = getLogger();
        CultivationProgression progression;
        try {
            progression = new CultivationConfigLoader().load(
                    getDataFolder().toPath().resolve("realms.yml")
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
        ProgressionService progressionService = new ProgressionService(profileService, progression);
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
        MurimCommand murimCommand = new MurimCommand(
                statusService,
                profileService,
                progressionService,
                messageFactory,
                promotionFeedback,
                mainThreadExecutor,
                logger
        );
        PluginCommand command = Objects.requireNonNull(
                getCommand("murim"),
                "The murim command must be declared in plugin.yml"
        );
        command.setExecutor(murimCommand);
        command.setTabCompleter(murimCommand);
        Bukkit.getPluginManager().registerEvents(
                new PlayerProfileListener(
                        profileService,
                        progressionService,
                        mainThreadExecutor,
                        messageFactory,
                        logger
                ),
                this
        );

        getLogger().info("MurimRPG enabled (version " + getPluginMeta().getVersion()
                + ", Paper API " + PAPER_API_VERSION + ", cultivation stages "
                + progression.stages().size() + ").");
    }

    @Override
    public void onDisable() {
        boolean profilesFlushed = true;
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
