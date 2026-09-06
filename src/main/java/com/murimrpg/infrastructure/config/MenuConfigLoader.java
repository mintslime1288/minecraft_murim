package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.MenuSettings;
import java.nio.file.Path;
import java.util.Map;

public final class MenuConfigLoader {
    public MenuSettings load(Path path) {
        Object raw = YamlDocument.load(path).get("menu");
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("menu.yml.menu must be a map");
        return new MenuSettings(number(map, "cooldown-millis"), text(map, "title"), text(map, "spawn-message"), text(map, "training-message"));
    }
    private static long number(Map<?, ?> map, String key) { Object value = map.get(key); if (!(value instanceof Number number) || number.longValue() < 0) throw new IllegalArgumentException("menu.yml.menu." + key + " must be a non-negative number"); return number.longValue(); }
    private static String text(Map<?, ?> map, String key) { Object value = map.get(key); if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("menu.yml.menu." + key + " must be a non-empty string"); return text; }
}
