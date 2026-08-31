package com.murimrpg.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record PlayerContentState(
        UUID uuid,
        Optional<String> organizationId,
        Optional<String> organizationRankId,
        long organizationContribution,
        Set<String> learnedSkillIds
) {
    public PlayerContentState {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(organizationRankId, "organizationRankId");
        Objects.requireNonNull(learnedSkillIds, "learnedSkillIds");
        organizationId = organizationId.map(value -> ContentId.requireValid(value, "Player organization ID"));
        organizationRankId = organizationRankId.map(value -> ContentId.requireValid(value, "Player organization rank ID"));
        if (organizationId.isEmpty() && organizationRankId.isPresent()) {
            throw new IllegalArgumentException("Organization rank requires direct organization membership");
        }
        if (organizationContribution < 0) {
            throw new IllegalArgumentException("Organization contribution cannot be negative");
        }
        learnedSkillIds = Set.copyOf(learnedSkillIds);
        for (String skillId : learnedSkillIds) {
            ContentId.requireValid(skillId, "Learned skill ID");
        }
    }

    public static PlayerContentState empty(UUID uuid) {
        return new PlayerContentState(uuid, Optional.empty(), Optional.empty(), 0, Set.of());
    }
}
