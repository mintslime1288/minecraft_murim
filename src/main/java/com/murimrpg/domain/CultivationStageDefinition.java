package com.murimrpg.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record CultivationStageDefinition(
        String stageId,
        MajorRealm majorRealm,
        RealmPhase phase,
        String displayName,
        int order,
        long requiredCultivationExp,
        long maxQi,
        long qiRegeneration,
        Set<PromotionRequirement> promotionRequirements,
        Set<String> unlocks,
        Optional<String> nextStageId
) {
    private static final Pattern STAGE_ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern CONTENT_ID = Pattern.compile("[A-Z][A-Z0-9_]*");

    public CultivationStageDefinition {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(majorRealm, "majorRealm");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(promotionRequirements, "promotionRequirements");
        Objects.requireNonNull(unlocks, "unlocks");
        Objects.requireNonNull(nextStageId, "nextStageId");
        if (!STAGE_ID.matcher(stageId).matches()) {
            throw new IllegalArgumentException("Invalid cultivation stage ID: " + stageId);
        }
        if (!majorRealm.supports(phase)) {
            throw new IllegalArgumentException(
                    "Invalid realm combination for stage " + stageId + ": "
                            + majorRealm.id() + " + " + phase.id()
            );
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is blank for stage " + stageId);
        }
        if (order <= 0 || requiredCultivationExp < 0 || maxQi <= 0 || qiRegeneration <= 0) {
            throw new IllegalArgumentException("Invalid numeric values for stage " + stageId);
        }
        promotionRequirements = Set.copyOf(promotionRequirements);
        unlocks = Set.copyOf(unlocks);
        for (String contentId : unlocks) {
            if (!CONTENT_ID.matcher(contentId).matches()) {
                throw new IllegalArgumentException("Invalid content ID for stage " + stageId + ": " + contentId);
            }
        }
        nextStageId = nextStageId.map(value -> {
            if (!STAGE_ID.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid next stage ID for " + stageId + ": " + value);
            }
            return value;
        });
    }
}
