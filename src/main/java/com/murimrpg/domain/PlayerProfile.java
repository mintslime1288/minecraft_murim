package com.murimrpg.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record PlayerProfile(
        UUID uuid,
        MajorRealm majorRealm,
        RealmPhase phase,
        long cultivationExp,
        Set<String> completedRequirements,
        Set<String> unlockedContent,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String DEFAULT_UNLOCKED_CONTENT = "QI_BAR";
    private static final Pattern STABLE_ID = Pattern.compile("[A-Z][A-Z0-9_.:-]*");

    public PlayerProfile {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(majorRealm, "majorRealm");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(completedRequirements, "completedRequirements");
        Objects.requireNonNull(unlockedContent, "unlockedContent");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (!majorRealm.supports(phase)) {
            throw new IllegalArgumentException(
                    "Invalid realm combination: " + majorRealm.id() + " + " + phase.id()
            );
        }
        if (cultivationExp < 0) {
            throw new IllegalArgumentException("Cultivation EXP cannot be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }

        completedRequirements = immutableIds(completedRequirements, "completedRequirements");
        unlockedContent = immutableIds(unlockedContent, "unlockedContent");
    }

    public static PlayerProfile createDefault(UUID uuid, Instant now) {
        return new PlayerProfile(
                uuid,
                MajorRealm.THIRD_RATE,
                RealmPhase.INITIATE,
                0,
                Set.of(),
                Set.of(DEFAULT_UNLOCKED_CONTENT),
                now,
                now
        );
    }

    public PlayerProfile withProgress(MajorRealm newMajorRealm, RealmPhase newPhase, long newExp, Instant now) {
        return new PlayerProfile(
                uuid,
                newMajorRealm,
                newPhase,
                newExp,
                completedRequirements,
                unlockedContent,
                createdAt,
                now
        );
    }

    public PlayerProfile resetProgress(Instant now) {
        return new PlayerProfile(
                uuid,
                MajorRealm.THIRD_RATE,
                RealmPhase.INITIATE,
                0,
                Set.of(),
                Set.of(DEFAULT_UNLOCKED_CONTENT),
                createdAt,
                now
        );
    }

    public PlayerProfile touch(Instant now) {
        return new PlayerProfile(
                uuid,
                majorRealm,
                phase,
                cultivationExp,
                completedRequirements,
                unlockedContent,
                createdAt,
                now
        );
    }

    public String realmDisplayName() {
        return majorRealm.displayName() + "·" + phase.displayName(majorRealm);
    }

    private static Set<String> immutableIds(Set<String> values, String fieldName) {
        Set<String> copy = Set.copyOf(values);
        for (String value : copy) {
            if (value == null || !STABLE_ID.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid stable ID in " + fieldName + ": " + value);
            }
        }
        return copy;
    }
}
