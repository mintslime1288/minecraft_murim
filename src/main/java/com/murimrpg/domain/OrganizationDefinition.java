package com.murimrpg.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

public record OrganizationDefinition(
        String id,
        String displayName,
        String allianceId,
        OrganizationCategory category,
        OrganizationType type,
        Optional<String> rankSystemId,
        Optional<String> leaderTitle,
        Optional<String> hanjaName,
        List<String> description,
        List<String> specialties
) {
    public OrganizationDefinition {
        id = ContentId.requireValid(id, "Organization ID");
        Objects.requireNonNull(displayName, "displayName");
        allianceId = ContentId.requireValid(allianceId, "Organization alliance ID");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rankSystemId, "rankSystemId");
        Objects.requireNonNull(leaderTitle, "leaderTitle");
        Objects.requireNonNull(hanjaName, "hanjaName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(specialties, "specialties");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is blank for organization " + id);
        }
        rankSystemId = rankSystemId.map(value -> ContentId.requireValid(value, "Rank system ID"));
        leaderTitle = leaderTitle.map(String::trim).filter(value -> !value.isEmpty());
        hanjaName = hanjaName.map(String::trim).filter(value -> !value.isEmpty());
        description = List.copyOf(description);
        specialties = List.copyOf(specialties);
        if (description.stream().anyMatch(String::isBlank) || specialties.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Organization UI metadata contains a blank line: " + id);
        }
        if (leaderTitle.isPresent() && type != OrganizationType.SECT) {
            throw new IllegalArgumentException("leader-title is only valid for SECT organization " + id);
        }
    }
}
