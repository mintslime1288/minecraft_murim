package com.murimrpg.client.render.scabbard;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Settings for the single-slot sword and its virtual scabbard, from {@code sword-scabbard.yml}.
 *
 * <p>Nothing visual is hardcoded in the renderer. The sword is recognised by the
 * {@code minecraft:item_model} identifier it carries rather than by its base item, because that
 * component is what makes a plain iron sword look like this sword; recognising
 * {@code minecraft:iron_sword} itself would put a scabbard in the hand of anyone holding any
 * iron sword.
 *
 * <p>A missing, unreadable or malformed file yields a disabled settings object. Losing the
 * player's normal hands because of a typo in a config file is worse than not showing a scabbard.
 */
public record ScabbardSettings(boolean enabled,
                               String swordItemModel,
                               String scabbardItemModel,
                               boolean matchSwordTagInstead,
                               boolean firstPerson,
                               boolean thirdPerson,
                               boolean rightArmOnlyAttack,
                               boolean diagnostics) {

    public static final String FILE_NAME = "sword-scabbard.yml";

    public static ScabbardSettings disabled() {
        return new ScabbardSettings(false, "murimrpg:top_straight_sword",
                "murimrpg:extended_black_scabbard", false, true, true, true, false);
    }

    public static ScabbardSettings load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) {
                try (var input = ScabbardSettings.class.getResourceAsStream("/" + FILE_NAME)) {
                    if (input == null) return disabled();
                    Files.copy(input, file);
                }
            }
            return parse(YamlDocument.load(file));
        } catch (IOException | RuntimeException error) {
            return disabled();
        }
    }

    static ScabbardSettings parse(YamlDocument yaml) {
        if (yaml == null || !bool(yaml, "enabled", false)) return disabled();
        ScabbardSettings d = disabled();
        return new ScabbardSettings(true,
                identifier(yaml, "sword-item-model", d.swordItemModel()),
                identifier(yaml, "scabbard-item-model", d.scabbardItemModel()),
                bool(yaml, "match-sword-tag-instead", d.matchSwordTagInstead()),
                bool(yaml, "render.first-person", d.firstPerson()),
                bool(yaml, "render.third-person", d.thirdPerson()),
                bool(yaml, "attack.right-arm-only", d.rightArmOnlyAttack()),
                bool(yaml, "diagnostics", d.diagnostics()));
    }

    private static boolean bool(YamlDocument yaml, String key, boolean fallback) {
        Object raw = yaml.get(key);
        return raw instanceof Boolean value ? value : fallback;
    }

    /**
     * Reads a namespaced identifier. A value that is not a well-formed {@code namespace:path}
     * falls back to the default rather than reaching the model manager, where a malformed id
     * would throw inside a render call.
     */
    private static String identifier(YamlDocument yaml, String key, String fallback) {
        Object raw = yaml.get(key);
        if (!(raw instanceof String value)) return fallback;
        String trimmed = value.trim();
        return isWellFormed(trimmed) ? trimmed : fallback;
    }

    static boolean isWellFormed(String identifier) {
        int colon = identifier.indexOf(':');
        if (colon <= 0 || colon == identifier.length() - 1) return false;
        if (identifier.indexOf(':', colon + 1) >= 0) return false;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            boolean legal = c == ':' || c == '_' || c == '-' || c == '.' || c == '/'
                    || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z');
            if (!legal) return false;
        }
        return true;
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "enabled=%s sword=%s scabbard=%s tagMatch=%s first=%s third=%s rightArmOnly=%s",
                enabled(), swordItemModel(), scabbardItemModel(), matchSwordTagInstead(),
                firstPerson(), thirdPerson(), rightArmOnlyAttack());
    }
}
