package com.murimrpg.service;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.MonsterDefinition;
import java.util.List;
import java.util.Objects;

/** Exposes monster templates without spawning or mutating Bukkit entities. */
public final class MonsterService {
    private final ContentCatalog catalog;

    public MonsterService(ContentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public List<MonsterDefinition> all() {
        return catalog.monsters();
    }

    public MonsterDefinition require(String id) {
        return catalog.findMonster(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown monster ID: " + id));
    }
}
