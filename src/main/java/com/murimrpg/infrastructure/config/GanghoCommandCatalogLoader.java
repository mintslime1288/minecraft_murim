package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.GanghoCommandCatalog;
import java.io.File;
import java.util.List;
import java.util.Map;

public final class GanghoCommandCatalogLoader {
    public GanghoCommandCatalog load(File file) {
        YamlDocument yaml = YamlDocument.load(file.toPath());
        return new GanghoCommandCatalog(entries(yaml, "organizations"), entries(yaml, "martial-arts"));
    }
    private List<GanghoCommandCatalog.Entry> entries(YamlDocument yaml, String path) {
        return yaml.mapList(path).stream().map(v -> new GanghoCommandCatalog.Entry(
                required(v, "id", path), required(v, "display-name", path), required(v, "description", path))).toList();
    }
    private String required(Map<?, ?> value, String key, String path) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + "." + key + " is required");
        return text;
    }
}
