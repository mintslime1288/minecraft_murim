package com.murimrpg.domain;

import net.minecraft.world.item.Item;

public record CurrencyDefinition(CurrencyType type, String displayName, Item material, int customModelData,
                                 long modernValueWon, String lore) {
    public CurrencyDefinition {
        if (displayName == null || displayName.isBlank() || material == null || customModelData < 1 || modernValueWon < 0
                || lore == null) throw new IllegalArgumentException("Invalid currency definition: " + type);
    }
}
