package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ContentConfigLoader {
    public ContentCatalog load(Path factionsPath, Path organizationsPath, Path ranksPath,
            Path skillsPath, Path monstersPath, Path questsPath, CultivationProgression progression) {
        FactionCatalog factions = new FactionCatalog(
                parseList(factionsPath, "alignments", false, this::parseAlignment),
                parseList(factionsPath, "alliances", false, this::parseAlliance),
                parseList(factionsPath, "categories", false, this::parseCategory),
                parseList(ranksPath, "rank-systems", false, this::parseRankSystem),
                parseList(organizationsPath, "organizations", false, this::parseOrganization)
        );
        ContentCatalog catalog = new ContentCatalog(
                factions,
                parseList(skillsPath, "skills", true, this::parseSkill),
                parseList(monstersPath, "monsters", false, this::parseMonster),
                new QuestCatalog(parseList(questsPath, "quests", false, this::parseQuest)),
                parseHuashanSettings(questsPath)
        );
        new ContentConfigValidator().validate(catalog, progression);
        return catalog;
    }

    private <T> List<T> parseList(Path path, String root, boolean allowEmpty, EntryParser<T> parser) {
        YamlConfiguration yaml = loadYaml(path);
        Object raw = yaml.get(root);
        if (!(raw instanceof List<?> entries)) {
            throw new IllegalArgumentException(path.getFileName() + "." + root + " must be a list");
        }
        if (!allowEmpty && entries.isEmpty()) {
            throw new IllegalArgumentException(path.getFileName() + "." + root + " must not be empty");
        }
        List<T> result = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            String context = path.getFileName() + "." + root + "[" + index + "]";
            if (!(entries.get(index) instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException(context + " must be a map");
            }
            try {
                result.add(parser.parse(values, context));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(context + ": " + exception.getMessage(), exception);
            }
        }
        return List.copyOf(result);
    }

    private YamlConfiguration loadYaml(Path path) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Failed to load " + path.getFileName() + ": " + exception.getMessage(), exception);
        }
    }

    private FactionAlignmentDefinition parseAlignment(Map<?, ?> v, String c) {
        return new FactionAlignmentDefinition(FactionAlignment.fromId(string(v, "id", c)), string(v, "display-name", c));
    }

    private AllianceDefinition parseAlliance(Map<?, ?> v, String c) {
        return new AllianceDefinition(string(v, "id", c), string(v, "display-name", c),
                FactionAlignment.fromId(string(v, "alignment-id", c)));
    }

    private OrganizationCategoryDefinition parseCategory(Map<?, ?> v, String c) {
        return new OrganizationCategoryDefinition(OrganizationCategory.fromId(string(v, "id", c)),
                string(v, "display-name", c), string(v, "alliance-id", c));
    }

    private OrganizationDefinition parseOrganization(Map<?, ?> v, String c) {
        return new OrganizationDefinition(string(v, "id", c), string(v, "display-name", c),
                string(v, "alliance-id", c), OrganizationCategory.fromId(string(v, "category-id", c)),
                OrganizationType.fromId(string(v, "type", c)), optional(v, "rank-system-id", c),
                optional(v, "leader-title", c), optional(v, "hanja-name", c),
                optionalLines(v, "description", c), optionalLines(v, "specialties", c));
    }

    private RankSystemDefinition parseRankSystem(Map<?, ?> v, String c) {
        Object raw = v.get("ranks");
        if (!(raw instanceof List<?> ranks) || ranks.isEmpty()) {
            throw new IllegalArgumentException(c + ".ranks must be a non-empty list");
        }
        List<OrganizationRankDefinition> parsed = new ArrayList<>();
        for (int index = 0; index < ranks.size(); index++) {
            String path = c + ".ranks[" + index + "]";
            if (!(ranks.get(index) instanceof Map<?, ?> rank)) {
                throw new IllegalArgumentException(path + " must be a map");
            }
            parsed.add(new OrganizationRankDefinition(
                    string(rank, "id", path), string(rank, "display-name", path), integer(rank, "order", path)
            ));
        }
        return new RankSystemDefinition(string(v, "id", c), string(v, "display-name", c),
                OrganizationType.fromId(string(v, "organization-type", c)), parsed);
    }

    private SkillDefinition parseSkill(Map<?, ?> v, String c) {
        return new SkillDefinition(string(v, "id", c), string(v, "display-name", c),
                SkillType.fromId(string(v, "type", c)), optional(v, "organization-id", c),
                string(v, "required-stage-id", c), whole(v, "qi-cost", c),
                whole(v, "cooldown-ticks", c), decimal(v, "power", c),
                string(v, "grade", c), lines(v, "description", c),
                lines(v, "unlock-conditions", c));
    }

    private MonsterDefinition parseMonster(Map<?, ?> v, String c) {
        return new MonsterDefinition(string(v, "id", c), string(v, "display-name", c),
                decimal(v, "max-health", c), decimal(v, "attack-damage", c),
                whole(v, "cultivation-exp-reward", c), strings(v, "skill-ids", c));
    }

    private QuestDefinition parseQuest(Map<?, ?> v, String c) {
        return new QuestDefinition(string(v, "id", c), string(v, "display-name", c),
                string(v, "organization-id", c), optional(v, "required-rank-id", c),
                optional(v, "resulting-rank-id", c), optional(v, "granted-skill-id", c),
                optional(v, "next-quest-id", c), QuestObjectiveType.valueOf(string(v, "objective-type", c)),
                optional(v, "target-id", c), optionalInteger(v, "required-hits", c, 0),
                optionalInteger(v, "required-meditation-seconds", c, 0),
                MeditationGoalMode.valueOf(optional(v, "meditation-mode", c).orElse("CUMULATIVE")),
                optionalDecimal(v, "threshold-percent", c, 0));
    }

    private HuashanSettings parseHuashanSettings(Path path) {
        YamlConfiguration yaml = loadYaml(path);
        String root = "quests.yml.huashan";
        int trialSpawnDelay = yaml.contains("huashan.trial-spawn-delay-seconds")
                ? requiredPathInt(yaml, "huashan.trial-spawn-delay-seconds", root + ".trial-spawn-delay-seconds")
                : 3;
        int preparation = requiredPathInt(yaml, "huashan.meditation-preparation-seconds",
                root + ".meditation-preparation-seconds");
        int count = requiredPathInt(yaml, "huashan.purple-mist-particle.count",
                root + ".purple-mist-particle.count");
        int interval = requiredPathInt(yaml, "huashan.purple-mist-particle.interval-ticks",
                root + ".purple-mist-particle.interval-ticks");
        if (trialSpawnDelay <= 0) throw new IllegalArgumentException(root + ".trial-spawn-delay-seconds must be positive");
        if (preparation <= 0) throw new IllegalArgumentException(root + ".meditation-preparation-seconds must be positive");
        if (count < 0 || count > 32) throw new IllegalArgumentException(root + ".purple-mist-particle.count must be 0..32");
        if (interval < 5) throw new IllegalArgumentException(root + ".purple-mist-particle.interval-ticks must be at least 5");
        return new HuashanSettings(
                requiredPathString(yaml, "huashan.organization-id", root + ".organization-id"),
                requiredPathString(yaml, "huashan.entrance-trial-id", root + ".entrance-trial-id"),
                requiredPathString(yaml, "huashan.liuhe-quest-id", root + ".liuhe-quest-id"),
                requiredPathString(yaml, "huashan.second-promotion-trial-id", root + ".second-promotion-trial-id"),
                requiredPathString(yaml, "huashan.first-promotion-trial-id", root + ".first-promotion-trial-id"),
                requiredPathString(yaml, "huashan.training-dummy-id", root + ".training-dummy-id"),
                requiredPathString(yaml, "huashan.training-dummy-display-name",
                        root + ".training-dummy-display-name"),
                requiredPathString(yaml, "huashan.examiner.id", root + ".examiner.id"),
                requiredPathString(yaml, "huashan.examiner.display-name", root + ".examiner.display-name"),
                requiredPathDouble(yaml, "huashan.examiner.max-health", root + ".examiner.max-health"),
                trialSpawnDelay,
                preparation,
                count,
                interval,
                requiredPathString(yaml, "huashan.purple-mist-particle.type",
                        root + ".purple-mist-particle.type")
        );
    }

    private String requiredPathString(YamlConfiguration yaml, String path, String context) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(context + " is missing or invalid");
        return value;
    }

    private int requiredPathInt(YamlConfiguration yaml, String path, String context) {
        Object value = yaml.get(path);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(context + " must be numeric");
        return number.intValue();
    }

    private double requiredPathDouble(YamlConfiguration yaml, String path, String context) {
        Object value = yaml.get(path);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(context + " must be numeric");
        return number.doubleValue();
    }

    private int optionalInteger(Map<?, ?> values, String key, String context, int fallback) {
        return values.containsKey(key) ? integer(values, key, context) : fallback;
    }

    private double optionalDecimal(Map<?, ?> values, String key, String context, double fallback) {
        return values.containsKey(key) ? decimal(values, key, context) : fallback;
    }

    private String string(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(context + "." + key + " is missing or invalid");
        }
        return text;
    }

    private Optional<String> optional(Map<?, ?> values, String key, String context) {
        return values.containsKey(key) ? Optional.of(string(values, key, context)) : Optional.empty();
    }

    private int integer(Map<?, ?> values, String key, String context) {
        long value = whole(values, key, context);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException(context + "." + key + " is too large");
        return (int) value;
    }

    private long whole(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.longValue()) {
            throw new IllegalArgumentException(context + "." + key + " must be an integer");
        }
        return number.longValue();
    }

    private double decimal(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(context + "." + key + " must be numeric");
        return number.doubleValue();
    }

    private Set<String> strings(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(context + "." + key + " must be a list");
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            Object entry = list.get(index);
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(context + "." + key + "[" + index + "] is invalid");
            }
            if (!result.add(text)) throw new IllegalArgumentException(context + "." + key + " has duplicate " + text);
        }
        return Set.copyOf(result);
    }

    private List<String> lines(Map<?, ?> values, String key, String context) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(context + "." + key + " must be a list");
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Object entry = list.get(index);
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(context + "." + key + "[" + index + "] is invalid");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private List<String> optionalLines(Map<?, ?> values, String key, String context) {
        return values.containsKey(key) ? lines(values, key, context) : List.of();
    }

    @FunctionalInterface
    private interface EntryParser<T> { T parse(Map<?, ?> values, String context); }
}
