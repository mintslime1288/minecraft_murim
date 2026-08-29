package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.GanghoCommandCatalog;
import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GanghoCommandCatalogLoader {
    public GanghoCommandCatalog load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new GanghoCommandCatalog(entries(yaml, "organizations"), entries(yaml, "martial-arts"));
    }
    private List<GanghoCommandCatalog.Entry> entries(YamlConfiguration yaml, String path) {
        return yaml.getMapList(path).stream().map(map -> new GanghoCommandCatalog.Entry(
                required(map, "id", path), required(map, "display-name", path), required(map, "description", path))).toList();
    }
    private String required(java.util.Map<?, ?> map, String key, String path) {
        Object value = map.get(key); if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + "." + key + " is required"); return text;
    }
}
