package com.murimrpg.domain;

import java.util.Locale;

public enum SkillType {
    MARTIAL_ART,
    MOVEMENT,
    INTERNAL_ART;

    public static SkillType fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown skill type: " + id, exception);
        }
    }
}
