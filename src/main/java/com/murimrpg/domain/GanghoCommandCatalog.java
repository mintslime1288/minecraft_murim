package com.murimrpg.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Config-backed public command metadata; gameplay ownership remains in future services. */
public record GanghoCommandCatalog(List<Entry> organizations, List<Entry> martialArts) {
    public GanghoCommandCatalog { organizations = List.copyOf(organizations); martialArts = List.copyOf(martialArts); }
    public Optional<Entry> organization(String value) { return organizations.stream().filter(entry -> entry.matches(value)).findFirst(); }
    public Optional<Entry> martialArt(String value) { return martialArts.stream().filter(entry -> entry.matches(value)).findFirst(); }
    public record Entry(String id, String displayName, String description) {
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(description); }
        public boolean matches(String value) { return id.equalsIgnoreCase(value) || displayName.equals(value); }
    }
}
