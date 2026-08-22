package com.murimrpg.domain;

import java.util.Optional;
import java.util.Set;

public record PromotionResult(
        Status status,
        Optional<PlayerProfile> profile,
        Optional<CultivationStageDefinition> currentStage,
        Optional<CultivationStageDefinition> nextStage,
        long missingExp,
        Set<String> missingRequirements,
        Set<String> newlyUnlocked
) {
    public enum Status {
        SUCCESS,
        BLOCKED,
        MAXIMUM_STAGE,
        IN_PROGRESS
    }

    public PromotionResult {
        profile = profile == null ? Optional.empty() : profile;
        currentStage = currentStage == null ? Optional.empty() : currentStage;
        nextStage = nextStage == null ? Optional.empty() : nextStage;
        missingRequirements = Set.copyOf(missingRequirements);
        newlyUnlocked = Set.copyOf(newlyUnlocked);
    }

    public static PromotionResult success(
            PlayerProfile profile,
            CultivationStageDefinition previousStage,
            CultivationStageDefinition currentStage,
            Set<String> newlyUnlocked
    ) {
        return new PromotionResult(
                Status.SUCCESS,
                Optional.of(profile),
                Optional.of(previousStage),
                Optional.of(currentStage),
                0,
                Set.of(),
                newlyUnlocked
        );
    }

    public static PromotionResult blocked(
            PlayerProfile profile,
            CultivationStageDefinition currentStage,
            CultivationStageDefinition nextStage,
            long missingExp,
            Set<String> missingRequirements
    ) {
        return new PromotionResult(
                Status.BLOCKED,
                Optional.of(profile),
                Optional.of(currentStage),
                Optional.of(nextStage),
                missingExp,
                missingRequirements,
                Set.of()
        );
    }

    public static PromotionResult maximum(PlayerProfile profile, CultivationStageDefinition currentStage) {
        return new PromotionResult(
                Status.MAXIMUM_STAGE,
                Optional.of(profile),
                Optional.of(currentStage),
                Optional.empty(),
                0,
                Set.of(),
                Set.of()
        );
    }

    public static PromotionResult inProgress() {
        return new PromotionResult(
                Status.IN_PROGRESS,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Set.of(),
                Set.of()
        );
    }
}
