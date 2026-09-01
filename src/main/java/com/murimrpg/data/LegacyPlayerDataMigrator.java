package com.murimrpg.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/** One-time, non-destructive relocation of the known v6 SQLite/YAML data directory. */
public final class LegacyPlayerDataMigrator {
    private static final List<String> CONFIGS = List.of("config.yml", "character.yml", "gangho.yml", "factions.yml",
            "sects.yml", "ranks.yml", "skills.yml", "monsters.yml", "quests.yml", "realms.yml", "ui.yml");

    public void migrate(Path legacyDirectory, Path fabricDirectory, Logger logger) throws IOException {
        if (!Files.isDirectory(legacyDirectory)) return;
        Files.createDirectories(fabricDirectory);
        for (String name : CONFIGS) copyIfMissing(legacyDirectory.resolve(name), fabricDirectory.resolve(name), logger);
        copyIfMissing(legacyDirectory.resolve("murimrpg.db"), fabricDirectory.resolve("murimrpg.db"), logger);
        copyIfMissing(legacyDirectory.resolve("murimrpg.db-wal"), fabricDirectory.resolve("murimrpg.db-wal"), logger);
        copyIfMissing(legacyDirectory.resolve("murimrpg.db-shm"), fabricDirectory.resolve("murimrpg.db-shm"), logger);
    }

    private void copyIfMissing(Path source, Path destination, Logger logger) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(destination)) return;
        Files.copy(source, destination);
        logger.info("Migrated legacy data file without modifying the source: " + source.getFileName());
    }
}
