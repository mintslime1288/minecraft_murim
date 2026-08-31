package com.murimrpg.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RankSystemDefinition(
        String id,
        String displayName,
        OrganizationType organizationType,
        List<OrganizationRankDefinition> ranks
) {
    public RankSystemDefinition {
        id = ContentId.requireValid(id, "Rank system ID");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(organizationType, "organizationType");
        Objects.requireNonNull(ranks, "ranks");
        if (displayName.isBlank() || ranks.isEmpty()) {
            throw new IllegalArgumentException("Rank system must have a display name and ranks: " + id);
        }
        ranks = ranks.stream().sorted(java.util.Comparator.comparingInt(OrganizationRankDefinition::order)).toList();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < ranks.size(); index++) {
            OrganizationRankDefinition rank = ranks.get(index);
            if (!ids.add(rank.id())) {
                throw new IllegalArgumentException("Duplicate rank ID " + rank.id() + " in rank system " + id);
            }
            if (rank.order() != index + 1) {
                throw new IllegalArgumentException("Rank orders must be contiguous from 1 in rank system " + id);
            }
        }
        ranks = List.copyOf(ranks);
    }
}
