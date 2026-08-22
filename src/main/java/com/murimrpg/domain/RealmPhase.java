package com.murimrpg.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum RealmPhase {
    INITIATE("INITIATE", "입문"),
    BEGINNER("BEGINNER", "하수"),
    INTERMEDIATE("INTERMEDIATE", "중수"),
    PEAK("PEAK", "평정/절정"),
    EARLY("EARLY", "초기"),
    MIDDLE("MIDDLE", "중기"),
    LATE("LATE", "후기"),
    COMPLETE("COMPLETE", "원만");

    private final String id;
    private final String defaultDisplayName;

    RealmPhase(String id, String defaultDisplayName) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
    }

    public String id() {
        return id;
    }

    public String displayName(MajorRealm majorRealm) {
        if (this == PEAK) {
            return majorRealm == MajorRealm.FIRST_RATE ? "절정" : "평정";
        }
        return defaultDisplayName;
    }

    public static Optional<RealmPhase> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.id.equals(normalized)).findFirst();
    }
}
