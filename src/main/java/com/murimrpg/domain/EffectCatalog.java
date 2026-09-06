package com.murimrpg.domain;

import java.util.Map;
import java.util.Optional;

public final class EffectCatalog {
    private final Map<String, EffectDefinition> definitions;
    public EffectCatalog(Map<String, EffectDefinition> definitions) { this.definitions = Map.copyOf(definitions); }
    public EffectDefinition require(String id) { return Optional.ofNullable(definitions.get(id)).orElseThrow(() -> new IllegalArgumentException("Unknown effect id: " + id)); }
    public Optional<EffectDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
    public Map<String, EffectDefinition> definitions() { return definitions; }
}
