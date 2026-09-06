package com.murimrpg.domain;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CurrencyCatalog {
    private final Map<CurrencyType, CurrencyDefinition> definitions;
    public CurrencyCatalog(Map<CurrencyType, CurrencyDefinition> definitions) {
        EnumMap<CurrencyType, CurrencyDefinition> copy = new EnumMap<>(CurrencyType.class);
        copy.putAll(definitions);
        for (CurrencyType type : CurrencyType.values()) if (!copy.containsKey(type)) throw new IllegalArgumentException("Missing currency: " + type);
        this.definitions = Map.copyOf(copy);
    }
    public CurrencyDefinition definition(CurrencyType type) { return definitions.get(type); }
    public Map<CurrencyType, CurrencyDefinition> definitions() { return definitions; }
    public Optional<CurrencyType> fromInput(String input) {
        if (input == null) return Optional.empty();
        String normalized = input.trim();
        return definitions.entrySet().stream().filter(entry -> entry.getValue().displayName().equals(normalized))
                .map(Map.Entry::getKey).findFirst().or(() -> CurrencyType.fromId(normalized.toUpperCase(Locale.ROOT)));
    }
}
