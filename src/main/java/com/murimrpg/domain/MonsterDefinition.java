package com.murimrpg.domain;

import java.util.Objects;
import java.util.Set;

public record MonsterDefinition(
        String id,
        String displayName,
        double maxHealth,
        double attackDamage,
        long cultivationExpReward,
        Set<String> skillIds
) {
    public MonsterDefinition {
        id = ContentId.requireValid(id, "Monster ID");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(skillIds, "skillIds");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is blank for monster " + id);
        }
        if (!Double.isFinite(maxHealth) || maxHealth <= 0
                || !Double.isFinite(attackDamage) || attackDamage < 0
                || cultivationExpReward < 0) {
            throw new IllegalArgumentException("Invalid numeric value for monster " + id);
        }
        skillIds = Set.copyOf(skillIds);
        for (String skillId : skillIds) {
            ContentId.requireValid(skillId, "Skill ID for monster " + id);
        }
    }
}
