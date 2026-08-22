package com.murimrpg.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CultivationProgression {
    private final List<CultivationStageDefinition> stages;
    private final Map<String, CultivationStageDefinition> stagesById;
    private final Map<RealmKey, CultivationStageDefinition> stagesByRealm;

    public CultivationProgression(List<CultivationStageDefinition> definitions) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Cultivation progression must contain at least one stage");
        }
        List<CultivationStageDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparingInt(CultivationStageDefinition::order));
        Map<String, CultivationStageDefinition> byId = new HashMap<>();
        Map<Integer, CultivationStageDefinition> byOrder = new HashMap<>();
        Map<RealmKey, CultivationStageDefinition> byRealm = new HashMap<>();
        for (CultivationStageDefinition stage : sorted) {
            if (byId.putIfAbsent(stage.stageId(), stage) != null) {
                throw new IllegalArgumentException("Duplicate cultivation stage ID: " + stage.stageId());
            }
            if (byOrder.putIfAbsent(stage.order(), stage) != null) {
                throw new IllegalArgumentException("Duplicate cultivation stage order: " + stage.order());
            }
            RealmKey key = new RealmKey(stage.majorRealm(), stage.phase());
            if (byRealm.putIfAbsent(key, stage) != null) {
                throw new IllegalArgumentException(
                        "Duplicate realm combination: " + stage.majorRealm().id() + " + " + stage.phase().id()
                );
            }
        }
        for (int index = 0; index < sorted.size(); index++) {
            CultivationStageDefinition stage = sorted.get(index);
            int expectedOrder = index + 1;
            if (stage.order() != expectedOrder) {
                throw new IllegalArgumentException(
                        "Cultivation stage orders must be contiguous from 1; expected "
                                + expectedOrder + " but found " + stage.order()
                );
            }
            Optional<String> expectedNext = index + 1 < sorted.size()
                    ? Optional.of(sorted.get(index + 1).stageId())
                    : Optional.empty();
            if (!stage.nextStageId().equals(expectedNext)) {
                throw new IllegalArgumentException(
                        "Invalid next stage for " + stage.stageId() + "; expected "
                                + expectedNext.orElse("none") + " but found "
                                + stage.nextStageId().orElse("none")
                );
            }
        }
        this.stages = List.copyOf(sorted);
        this.stagesById = Map.copyOf(byId);
        this.stagesByRealm = Map.copyOf(byRealm);
    }

    public List<CultivationStageDefinition> stages() {
        return stages;
    }

    public CultivationStageDefinition firstStage() {
        return stages.getFirst();
    }

    public CultivationStageDefinition finalStage() {
        return stages.getLast();
    }

    public CultivationStageDefinition stage(String stageId) {
        CultivationStageDefinition stage = stagesById.get(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("Unknown cultivation stage ID: " + stageId);
        }
        return stage;
    }

    public CultivationStageDefinition stage(PlayerProfile profile) {
        return stage(profile.majorRealm(), profile.phase());
    }

    public CultivationStageDefinition stage(MajorRealm majorRealm, RealmPhase phase) {
        CultivationStageDefinition stage = stagesByRealm.get(new RealmKey(majorRealm, phase));
        if (stage == null) {
            throw new IllegalArgumentException(
                    "No configured cultivation stage for realm: "
                            + majorRealm.id() + " + " + phase.id()
            );
        }
        return stage;
    }

    public Optional<CultivationStageDefinition> next(CultivationStageDefinition stage) {
        return stage.nextStageId().map(this::stage);
    }

    public Set<String> requirementIds() {
        return stages.stream()
                .flatMap(stage -> stage.promotionRequirements().stream())
                .map(PromotionRequirement::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> unlocksThrough(CultivationStageDefinition target) {
        Set<String> result = new LinkedHashSet<>();
        for (CultivationStageDefinition stage : stages) {
            if (stage.order() > target.order()) {
                break;
            }
            result.addAll(stage.unlocks());
        }
        return Set.copyOf(result);
    }

    private record RealmKey(MajorRealm majorRealm, RealmPhase phase) {
    }
}
