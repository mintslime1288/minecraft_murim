package com.murimrpg;

import com.murimrpg.command.CommandRegistry;
import com.murimrpg.block.MurimBlocks;
import com.murimrpg.config.ModResources;
import com.murimrpg.data.LegacyPlayerDataMigrator;
import com.murimrpg.cultivation.MeditationController;
import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.event.EventRegistry;
import com.murimrpg.network.MurimRpgNetworking;
import com.murimrpg.infrastructure.config.ContentConfigLoader;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import com.murimrpg.infrastructure.sqlite.SqlitePlayerRepository;
import com.murimrpg.scheduler.ServerTickScheduler;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.service.CharacterRollService;
import com.murimrpg.service.CharacterSettings;
import com.murimrpg.service.HuashanService;
import com.murimrpg.service.MeditationService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.service.InventoryCurrencyService;
import com.murimrpg.service.TrainingItemService;
import com.murimrpg.service.TrainingDummyCollisionService;
import com.murimrpg.domain.CurrencyCatalog;
import com.murimrpg.infrastructure.config.CurrencyConfigLoader;
import com.murimrpg.infrastructure.config.EffectConfigLoader;
import com.murimrpg.domain.EffectCatalog;
import com.murimrpg.service.VisualEffectService;
import com.murimrpg.service.InteractiveMenuService;
import com.murimrpg.infrastructure.config.MenuConfigLoader;
import com.murimrpg.domain.MenuSettings;
import com.murimrpg.ui.FabricUiManager;
import com.murimrpg.ui.UiSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class GanghoImunrok implements ModInitializer {
    public static final String MOD_ID = "murimrpg";
    private static final int EXPECTED_STAGES = 32;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);
    private final Logger logger = Logger.getLogger("MurimRPG");
    private PlayerProfileService profiles;
    private PlayerContentService content;

    @Override public void onInitialize() {
        MurimBlocks.register();
        // Fabric invokes the common entrypoint in a client as well. The server owns
        // persistence, commands and YAML; the client entrypoint owns visual packets.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            logger.info("MurimRPG client detected: skipping server-only initialization.");
            return;
        }
        try {
            MurimRpgNetworking.registerPayloadTypes();
            Path directory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
            new LegacyPlayerDataMigrator().migrate(
                    FabricLoader.getInstance().getGameDir().resolve("plugins").resolve("MurimRPG"), directory, logger);
            new ModResources().installDefaults(directory);
            CultivationProgression progression = new CultivationConfigLoader().load(directory.resolve("realms.yml"), EXPECTED_STAGES);
            ContentCatalog catalog = new ContentConfigLoader().load(directory.resolve("factions.yml"), directory.resolve("sects.yml"),
                    directory.resolve("ranks.yml"), directory.resolve("skills.yml"), directory.resolve("monsters.yml"),
                    directory.resolve("quests.yml"), progression);
            CurrencyCatalog currencies = new CurrencyConfigLoader().load(directory.resolve("currencies.yml"));
            EffectCatalog effects = new EffectConfigLoader().load(directory.resolve("effects.yml"));
            MenuSettings menuSettings = new MenuConfigLoader().load(directory.resolve("menu.yml"));
            ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "MurimRPG-Database"); thread.setDaemon(false); return thread;
            });
            SqlitePlayerRepository repository = new SqlitePlayerRepository(directory.resolve("murimrpg.db"), logger);
            profiles = new PlayerProfileService(repository, databaseExecutor, Clock.systemUTC());
            content = new PlayerContentService(repository, databaseExecutor);
            ProgressionService progressionService = new ProgressionService(profiles, progression);
            CharacterCreationService characters = new CharacterCreationService(profiles,
                    new CharacterRollService(CharacterSettings.load(directory.resolve("character.yml")), RandomGenerator.getDefault()));
            HuashanService huashan = new HuashanService(content, catalog);
            ServerTickScheduler scheduler = new ServerTickScheduler();
            MeditationController meditation = new MeditationController(content, profiles, progression,
                    new MeditationService(huashan), catalog.huashanSettings(), scheduler);
            FabricUiManager ui = new FabricUiManager(UiSettings.load(directory.resolve("ui.yml")), profiles,
                    content, characters, catalog, progression, scheduler);
            TrainingDummyCollisionService dummyCollision = new TrainingDummyCollisionService();
            GanghoContext context = new GanghoContext(profiles, content, progressionService, progression, catalog,
                    huashan, characters, scheduler, meditation, ui, new InventoryCurrencyService(currencies),
                    new TrainingItemService(), dummyCollision, new VisualEffectService(effects), new InteractiveMenuService(menuSettings));
            profiles.initialize().join();
            new CommandRegistry(context).register();
            new EventRegistry(context, logger).register();
            dummyCollision.register();
            scheduler.register();
            ui.register();
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdown());
            logger.info("Gangho Imunrok Fabric initialized: Minecraft 26.2, stages " + progression.stages().size()
                    + ", organizations " + catalog.factions().organizations().size() + ", skills " + catalog.skills().size());
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Gangho Imunrok could not initialize safely", exception);
            throw new IllegalStateException("MurimRPG initialization failed", exception);
        }
    }

    private void shutdown() {
        try {
            if (content != null) content.saveCachedStates().get(SHUTDOWN_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (profiles != null) profiles.shutdown(SHUTDOWN_TIMEOUT);
            logger.info("Gangho Imunrok stopped after flushing player data.");
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Player data flush failed during shutdown", exception);
        }
    }
}
