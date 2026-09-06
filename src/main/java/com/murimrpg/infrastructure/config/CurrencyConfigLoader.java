package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.*;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class CurrencyConfigLoader {
    public CurrencyCatalog load(Path path) {
        YamlDocument yaml = YamlDocument.load(path);
        Object raw = yaml.get("currencies");
        if (!(raw instanceof Map<?, ?> entries)) throw new IllegalArgumentException("currencies.yml.currencies must be a map");
        EnumMap<CurrencyType, CurrencyDefinition> result = new EnumMap<>(CurrencyType.class);
        for (CurrencyType type : CurrencyType.values()) {
            Object value = entries.get(type.name());
            if (!(value instanceof Map<?, ?> item)) throw new IllegalArgumentException("currencies.yml.currencies." + type + " must be a map");
            String display = string(item, "display-name", type); String materialId = string(item, "material", type);
            int cmd = number(item, "custom-model-data", type).intValue(); long valueWon = number(item, "modern-value-won", type).longValue();
            result.put(type, new CurrencyDefinition(type, display, material(materialId, type), cmd, valueWon, string(item, "lore", type)));
        }
        return new CurrencyCatalog(result);
    }
    private static String string(Map<?, ?> map, String key, CurrencyType type) { Object value = map.get(key); if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("currencies.yml.currencies." + type + "." + key + " must be a non-empty string"); return text; }
    private static Number number(Map<?, ?> map, String key, CurrencyType type) { Object value = map.get(key); if (!(value instanceof Number n) || n.longValue() < 0) throw new IllegalArgumentException("currencies.yml.currencies." + type + "." + key + " must be a non-negative number"); return n; }
    private static Item material(String id, CurrencyType type) { return switch (id) { case "COPPER_INGOT" -> Items.COPPER_INGOT; case "IRON_NUGGET" -> Items.IRON_NUGGET; case "IRON_INGOT" -> Items.IRON_INGOT; case "GOLD_INGOT" -> Items.GOLD_INGOT; default -> throw new IllegalArgumentException("currencies.yml.currencies." + type + ".material is unsupported: " + id); }; }
}
