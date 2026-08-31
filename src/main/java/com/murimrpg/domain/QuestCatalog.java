package com.murimrpg.domain;

import java.util.*;

public final class QuestCatalog {
    private final Map<String, QuestDefinition> quests;
    public QuestCatalog(List<QuestDefinition> definitions) {
        Map<String, QuestDefinition> indexed = new LinkedHashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            QuestDefinition value = definitions.get(i);
            if (indexed.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("quests.yml.quests[" + i + "].id: Duplicate quest ID: " + value.id());
            }
        }
        quests = Collections.unmodifiableMap(indexed);
    }
    public List<QuestDefinition> all() { return List.copyOf(quests.values()); }
    public Optional<QuestDefinition> find(String id) { return Optional.ofNullable(quests.get(id)); }
    public QuestDefinition require(String id) { return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown quest ID: " + id)); }
}
