package com.murimrpg.service;

import com.murimrpg.domain.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

/** Physical-item operations only; no wallet or database balance is created. */
public final class InventoryCurrencyService {
    private static final String DATA_KEY = "murimrpg_currency";
    private final CurrencyCatalog catalog;
    public InventoryCurrencyService(CurrencyCatalog catalog) { this.catalog = catalog; }
    public CurrencyCatalog catalog() { return catalog; }
    public ItemStack create(CurrencyType type, int amount) {
        CurrencyDefinition d = catalog.definition(type); ItemStack stack = new ItemStack(d.material(), amount);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(d.displayName()));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(d.lore()))));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) d.customModelData()), List.of(), List.of(), List.of()));
        CompoundTag tag = new CompoundTag(); tag.putString(DATA_KEY, type.name()); CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }
    public boolean isCurrency(ItemStack stack, CurrencyType type) {
        if (stack.isEmpty() || stack.getItem() != catalog.definition(type).material()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA); if (data == null) return false;
        return type.name().equals(data.copyTag().getStringOr(DATA_KEY, ""));
    }
    public Map<CurrencyType, Integer> balances(Player player) {
        EnumMap<CurrencyType, Integer> result = new EnumMap<>(CurrencyType.class);
        for (CurrencyType type : CurrencyType.values()) result.put(type, 0);
        Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) for (CurrencyType type : CurrencyType.values()) {
            ItemStack stack = inventory.getItem(index); if (isCurrency(stack, type)) result.merge(type, stack.getCount(), Integer::sum);
        }
        return Map.copyOf(result);
    }
    public long totalModernValueWon(Player player) { return balances(player).entrySet().stream().mapToLong(entry -> (long) entry.getValue() * catalog.definition(entry.getKey()).modernValueWon()).sum(); }
    public TransferResult give(Player player, CurrencyType type, int amount) { if (amount < 1) return TransferResult.INVALID_AMOUNT; return player.getInventory().add(create(type, amount)) ? TransferResult.SUCCESS : TransferResult.NO_SPACE; }
    public TransferResult take(Player player, CurrencyType type, int amount) {
        if (amount < 1) return TransferResult.INVALID_AMOUNT; if (balances(player).get(type) < amount) return TransferResult.INSUFFICIENT;
        int remaining = amount; Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++) { ItemStack stack = inventory.getItem(index); if (!isCurrency(stack, type)) continue; int used = Math.min(remaining, stack.getCount()); stack.shrink(used); remaining -= used; }
        return TransferResult.SUCCESS;
    }
    public enum TransferResult { SUCCESS, NO_SPACE, INSUFFICIENT, INVALID_AMOUNT }
}
