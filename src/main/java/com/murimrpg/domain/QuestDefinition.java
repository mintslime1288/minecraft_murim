package com.murimrpg.domain;

import java.util.Objects;
import java.util.Optional;

public record QuestDefinition(
        String id, String displayName, String organizationId,
        Optional<String> requiredRankId, Optional<String> resultingRankId,
        Optional<String> grantedSkillId, Optional<String> nextQuestId,
        QuestObjectiveType objectiveType, Optional<String> targetId,
        int requiredHits, int requiredMeditationSeconds,
        MeditationGoalMode meditationMode, double healthThresholdPercent
) {
    public QuestDefinition {
        id = ContentId.requireValid(id, "Quest ID");
        organizationId = ContentId.requireValid(organizationId, "Quest organization ID");
        Objects.requireNonNull(objectiveType); Objects.requireNonNull(targetId);
        targetId = targetId.map(v -> ContentId.requireValid(v, "Quest target ID"));
        Objects.requireNonNull(displayName); Objects.requireNonNull(requiredRankId);
        Objects.requireNonNull(resultingRankId); Objects.requireNonNull(grantedSkillId);
        Objects.requireNonNull(nextQuestId); Objects.requireNonNull(meditationMode);
        requiredRankId = requiredRankId.map(v -> ContentId.requireValid(v, "Required rank ID"));
        resultingRankId = resultingRankId.map(v -> ContentId.requireValid(v, "Resulting rank ID"));
        grantedSkillId = grantedSkillId.map(v -> ContentId.requireValid(v, "Granted skill ID"));
        nextQuestId = nextQuestId.map(v -> ContentId.requireValid(v, "Next quest ID"));
        if (displayName.isBlank()) throw new IllegalArgumentException("Blank quest display name for " + id);
        if (requiredHits < 0 || requiredMeditationSeconds < 0)
            throw new IllegalArgumentException("Negative quest goal for " + id);
        if (objectiveType == QuestObjectiveType.TRAINING) {
            if (targetId.isEmpty() || requiredHits + requiredMeditationSeconds == 0)
                throw new IllegalArgumentException("Training quest requires target and at least one goal: " + id);
        } else if (objectiveType == QuestObjectiveType.NPC_HEALTH_THRESHOLD) {
            if (targetId.isEmpty() || !Double.isFinite(healthThresholdPercent)
                    || healthThresholdPercent <= 0 || healthThresholdPercent >= 100)
                throw new IllegalArgumentException("NPC health quest requires target and threshold-percent between 0 and 100: " + id);
        } else if (objectiveType == QuestObjectiveType.MONSTER_KILL && targetId.isEmpty()) {
            throw new IllegalArgumentException("Monster kill quest requires target: " + id);
        }
    }
}
