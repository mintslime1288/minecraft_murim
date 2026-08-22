package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.CultivationStageDefinition;
import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PromotionRequirement;
import com.murimrpg.domain.RealmPhase;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CultivationConfigLoader {
    public CultivationProgression load(Path path) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(path.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Failed to load cultivation config: " + path, exception);
        }

        List<Map<?, ?>> entries = configuration.getMapList("realms");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("realms.yml must define a non-empty realms list");
        }
        List<CultivationStageDefinition> stages = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            stages.add(parseStage(entries.get(index), index));
        }
        return new CultivationProgression(stages);
    }

    private CultivationStageDefinition parseStage(Map<?, ?> values, int index) {
        String context = "realms[" + index + "]";
        String stageId = requiredString(values, "stage-id", context);
        MajorRealm majorRealm = MajorRealm.fromId(requiredString(values, "major-realm-id", context))
                .orElseThrow(() -> new IllegalArgumentException("Unknown major realm in " + context));
        RealmPhase phase = RealmPhase.fromId(requiredString(values, "phase-id", context))
                .orElseThrow(() -> new IllegalArgumentException("Unknown realm phase in " + context));
        Object nextValue = values.get("next-stage-id");
        Optional<String> nextStageId = nextValue == null
                ? Optional.empty()
                : Optional.of(requiredString(values, "next-stage-id", context));
        return new CultivationStageDefinition(
                stageId,
                majorRealm,
                phase,
                requiredString(values, "display-name", context),
                requiredInt(values, "order", context),
                requiredLong(values, "required-cultivation-exp", context),
                requiredLong(values, "max-qi", context),
                requiredLong(values, "qi-regeneration", context),
                requirementSet(values, "promotion-requirements", context),
                stringSet(values, "unlocks", context),
                nextStageId
        );
    }

    private String requiredString(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing or invalid " + key + " in " + context);
        }
        return text;
    }

    private int requiredInt(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Missing or invalid " + key + " in " + context);
        }
        return number.intValue();
    }

    private long requiredLong(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Missing or invalid " + key + " in " + context);
        }
        return number.longValue();
    }

    private Set<PromotionRequirement> requirementSet(Map<?, ?> values, String key, String context) {
        Set<PromotionRequirement> result = new LinkedHashSet<>();
        for (String id : stringSet(values, key, context)) {
            result.add(new PromotionRequirement(id));
        }
        return Set.copyOf(result);
    }

    private Set<String> stringSet(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Missing or invalid " + key + " in " + context);
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object entry : list) {
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("Invalid value in " + key + " for " + context);
            }
            result.add(text);
        }
        return Set.copyOf(result);
    }
}
