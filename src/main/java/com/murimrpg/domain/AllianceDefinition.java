package com.murimrpg.domain;

import java.util.Objects;

public record AllianceDefinition(String id, String displayName, FactionAlignment alignment) {
    public AllianceDefinition {
        id = ContentId.requireValid(id, "Alliance ID");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(alignment, "alignment");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is blank for alliance " + id);
        }
    }
}
