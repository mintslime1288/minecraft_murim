package com.murimrpg.domain;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ContentCatalog {
    private final FactionCatalog factions;
    private final Map<String, SkillDefinition> skills;
    private final Map<String, MonsterDefinition> monsters;
    private final QuestCatalog quests;
    private final HuashanSettings huashanSettings;

    public ContentCatalog(
            FactionCatalog factions,
            List<SkillDefinition> skillDefinitions,
            List<MonsterDefinition> monsterDefinitions,
            QuestCatalog quests,
            HuashanSettings huashanSettings
    ) {
        this.factions = java.util.Objects.requireNonNull(factions, "factions");
        this.skills = index(skillDefinitions, SkillDefinition::id, "skill");
        this.monsters = index(monsterDefinitions, MonsterDefinition::id, "monster");
        this.quests = java.util.Objects.requireNonNull(quests, "quests");
        this.huashanSettings = java.util.Objects.requireNonNull(huashanSettings, "huashanSettings");
    }

    public FactionCatalog factions() {
        return factions;
    }

    public List<SkillDefinition> skills() {
        return List.copyOf(skills.values());
    }

    public List<MonsterDefinition> monsters() {
        return List.copyOf(monsters.values());
    }
    public QuestCatalog quests() { return quests; }
    public HuashanSettings huashanSettings() { return huashanSettings; }

    public Optional<SkillDefinition> findSkill(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public Optional<MonsterDefinition> findMonster(String id) {
        return Optional.ofNullable(monsters.get(id));
    }

    private static <T> Map<String, T> index(
            List<T> definitions,
            java.util.function.Function<T, String> idFunction,
            String type
    ) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T definition : definitions) {
            String id = idFunction.apply(definition);
            if (indexed.putIfAbsent(id, definition) != null) {
                throw new IllegalArgumentException("Duplicate " + type + " ID: " + id);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }
}
