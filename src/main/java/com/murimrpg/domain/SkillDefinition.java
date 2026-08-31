package com.murimrpg.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

public record SkillDefinition(
        String id,
        String displayName,
        SkillType type,
        Optional<String> organizationId,
        String requiredStageId,
        long qiCost,
        long cooldownTicks,
        double power,
        String grade,
        List<String> description,
        List<String> unlockConditions
) {
    public SkillDefinition {
        id = ContentId.requireValid(id, "Skill ID");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(requiredStageId, "requiredStageId");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(unlockConditions, "unlockConditions");
        if (displayName.isBlank() || requiredStageId.isBlank() || grade.isBlank()) {
            throw new IllegalArgumentException("Blank display name or required stage for skill " + id);
        }
        if (organizationId.isPresent()) {
            organizationId = Optional.of(ContentId.requireValid(
                    organizationId.orElseThrow(), "Organization ID for skill " + id
            ));
        }
        if (qiCost < 0 || cooldownTicks < 0 || !Double.isFinite(power) || power < 0) {
            throw new IllegalArgumentException("Negative or non-finite numeric value for skill " + id);
        }
        description = immutableLines(description, "description", id);
        unlockConditions = immutableLines(unlockConditions, "unlock conditions", id);
    }

    private static List<String> immutableLines(List<String> values, String field, String skillId) {
        List<String> copy = List.copyOf(values);
        if (copy.isEmpty() || copy.stream().anyMatch(line -> line == null || line.isBlank())) {
            throw new IllegalArgumentException("Blank or empty " + field + " for skill " + skillId);
        }
        return copy;
    }
}
