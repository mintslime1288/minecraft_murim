package com.murimrpg.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ModResources {
    public static final List<String> CONFIG_FILES = List.of("config.yml", "character.yml", "gangho.yml",
            "factions.yml", "sects.yml", "ranks.yml", "skills.yml", "monsters.yml", "quests.yml", "realms.yml", "ui.yml");

    public void installDefaults(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (String name : CONFIG_FILES) {
            Path destination = directory.resolve(name);
            if (Files.exists(destination)) continue;
            try (InputStream source = ModResources.class.getClassLoader().getResourceAsStream(name)) {
                if (source == null) throw new IOException("Bundled resource is missing: " + name);
                Files.copy(source, destination);
            }
        }
    }
}
