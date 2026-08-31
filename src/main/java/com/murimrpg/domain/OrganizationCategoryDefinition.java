package com.murimrpg.domain;

import java.util.Objects;

public record OrganizationCategoryDefinition(
        OrganizationCategory id,
        String displayName,
        String allianceId
) {
    public OrganizationCategoryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        allianceId = ContentId.requireValid(allianceId, "Category alliance ID");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is blank for category " + id);
        }
    }
}
