package com.murimrpg.domain;

import java.util.Objects;

public record OrganizationRankDefinition(String id, String displayName, int order) {
    public OrganizationRankDefinition {
        id = ContentId.requireValid(id, "Rank ID");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || order <= 0) {
            throw new IllegalArgumentException("Invalid display name or order for rank " + id);
        }
    }
}
