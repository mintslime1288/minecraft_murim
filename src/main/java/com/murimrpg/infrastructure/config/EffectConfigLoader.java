package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.EffectCatalog;
import com.murimrpg.domain.EffectDefinition;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EffectConfigLoader {
    public EffectCatalog load(Path path) {
        Object raw = YamlDocument.load(path).get("effects");
        if (!(raw instanceof Map<?, ?> values)) throw new IllegalArgumentException("effects.yml.effects must be a map");
        Map<String, EffectDefinition> definitions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String id) || !(entry.getValue() instanceof Map<?, ?> value)) throw new IllegalArgumentException("effects.yml.effects entries must be maps");
            if (definitions.put(id, new EffectDefinition(id, string(value, "display-name", id), number(value, "duration-ticks", id),
                    decimal(value, "scale", id), number(value, "max-distance", id), number(value, "particle-count", id))) != null) {
                throw new IllegalArgumentException("Duplicate effect id: " + id);
            }
        }
        return new EffectCatalog(definitions);
    }
    private static String string(Map<?, ?> value, String key, String id) { Object result = value.get(key); if (!(result instanceof String text) || text.isBlank()) throw new IllegalArgumentException("effects.yml.effects." + id + "." + key + " must be a string"); return text; }
    private static int number(Map<?, ?> value, String key, String id) { Object result = value.get(key); if (!(result instanceof Number number) || number.intValue() < 1) throw new IllegalArgumentException("effects.yml.effects." + id + "." + key + " must be a positive number"); return number.intValue(); }
    private static float decimal(Map<?, ?> value, String key, String id) { Object result = value.get(key); if (!(result instanceof Number number) || number.floatValue() <= 0) throw new IllegalArgumentException("effects.yml.effects." + id + "." + key + " must be a positive number"); return number.floatValue(); }
}
