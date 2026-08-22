package com.murimrpg.domain;

import java.util.Optional;
import java.util.Set;

public record RealmProgressView(
        PlayerProfile profile,
        CultivationStageDefinition currentStage,
        Optional<CultivationStageDefinition> nextStage,
        long missingExp,
        Set<String> nextRequirements,
        Set<String> missingRequirements
) {
    public RealmProgressView {
        nextStage = nextStage == null ? Optional.empty() : nextStage;
        nextRequirements = Set.copyOf(nextRequirements);
        missingRequirements = Set.copyOf(missingRequirements);
    }
}
