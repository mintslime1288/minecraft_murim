package com.murimrpg.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum MajorRealm {
    THIRD_RATE("THIRD_RATE", "삼류", Set.of(
            RealmPhase.INITIATE,
            RealmPhase.BEGINNER,
            RealmPhase.INTERMEDIATE,
            RealmPhase.PEAK
    )),
    SECOND_RATE("SECOND_RATE", "이류", Set.of(
            RealmPhase.EARLY,
            RealmPhase.MIDDLE,
            RealmPhase.LATE,
            RealmPhase.COMPLETE
    )),
    FIRST_RATE("FIRST_RATE", "일류", Set.of(
            RealmPhase.EARLY,
            RealmPhase.MIDDLE,
            RealmPhase.LATE,
            RealmPhase.PEAK
    ));

    private final String id;
    private final String displayName;
    private final Set<RealmPhase> phases;

    MajorRealm(String id, String displayName, Set<RealmPhase> phases) {
        this.id = id;
        this.displayName = displayName;
        this.phases = phases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supports(RealmPhase phase) {
        return phases.contains(phase);
    }

    public List<RealmPhase> phases() {
        return Arrays.stream(RealmPhase.values()).filter(phases::contains).toList();
    }

    public static Optional<MajorRealm> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.id.equals(normalized)).findFirst();
    }
}
