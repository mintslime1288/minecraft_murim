package com.murimrpg.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stable storage IDs for physical currency. Player-facing text comes from YAML. */
public enum CurrencyType {
    YEOPJEON(List.of()), CHEOLJEON(List.of()), EUNWONBO(List.of("EUNHWA")), GEUMWONBO(List.of("GEUMHWA"));

    private final List<String> legacyIds;
    CurrencyType(List<String> legacyIds) { this.legacyIds = List.copyOf(legacyIds); }
    public List<String> legacyIds() { return legacyIds; }
    public static Optional<CurrencyType> fromId(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (CurrencyType type : values()) {
            if (type.name().equals(normalized) || type.legacyIds.contains(normalized)) return Optional.of(type);
        }
        return Optional.empty();
    }
}
