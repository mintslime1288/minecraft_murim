package com.murimrpg.domain;

import java.util.*;

public record PlayerJourneyState(
        UUID uuid, Optional<String> activeActivityId, int dummyHits, int meditationSeconds,
        Optional<String> activeSkillId, long currentQi, Set<String> completedActivityIds
) {
    public PlayerJourneyState {
        Objects.requireNonNull(uuid); Objects.requireNonNull(activeActivityId);
        Objects.requireNonNull(activeSkillId); Objects.requireNonNull(completedActivityIds);
        activeActivityId = activeActivityId.map(v -> ContentId.requireValid(v, "Active activity ID"));
        activeSkillId = activeSkillId.map(v -> ContentId.requireValid(v, "Active skill ID"));
        if (dummyHits < 0 || meditationSeconds < 0 || currentQi < 0) throw new IllegalArgumentException("Negative journey state");
        completedActivityIds = Set.copyOf(completedActivityIds);
        completedActivityIds.forEach(v -> ContentId.requireValid(v, "Completed activity ID"));
    }
    public static PlayerJourneyState empty(UUID uuid) {
        return new PlayerJourneyState(uuid, Optional.empty(), 0, 0, Optional.empty(), 0, Set.of());
    }
}
