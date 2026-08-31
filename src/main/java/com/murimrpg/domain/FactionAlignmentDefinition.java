package com.murimrpg.domain;

import java.util.Objects;

public record FactionAlignmentDefinition(FactionAlignment id, String displayName) {
    public FactionAlignmentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is blank for alignment " + id);
        }
    }
}
