package com.murimrpg.service;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.EquipmentSlot;

/** Custom training items are real ItemStacks, never inferred from vanilla materials alone. */
public final class TrainingItemService {
    public static final String WOODEN_SWORD = "WOODEN_SWORD";
    public static final String TRAINING_DUMMY = "TRAINING_DUMMY";
    private static final String KEY = "murimrpg_training_item";
    public ItemStack create(String id, int count) {
        Item item = material(id); int cmd = WOODEN_SWORD.equals(id) ? 32001 : 32002;
        String name = WOODEN_SWORD.equals(id) ? "목검" : "훈련 허수아비";
        String lore = WOODEN_SWORD.equals(id) ? "나무로 만든 기본 수련용 검입니다." : "설치 후 목검으로 수련할 수 있는 통나무 수련대입니다.";
        ItemStack stack = new ItemStack(item, count); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore))));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) cmd), List.of(), List.of(), List.of()));
        CompoundTag tag = new CompoundTag(); tag.putString(KEY, id); CustomData.set(DataComponents.CUSTOM_DATA, stack, tag); return stack;
    }
    public boolean is(ItemStack stack, String id) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); return data != null && material(id) == stack.getItem() && id.equals(data.copyTag().getStringOr(KEY, "")); }
    public boolean give(net.minecraft.server.level.ServerPlayer player, String id, int count) { return count > 0 && player.getInventory().add(create(id, count)); }
    public ItemStack displayItem() {
        ItemStack stack = new ItemStack(Items.CARVED_PUMPKIN); stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(32003f), List.of(), List.of(), List.of())); return stack;
    }
    private Item material(String id) { return switch (id) { case WOODEN_SWORD -> Items.WOODEN_SWORD; case TRAINING_DUMMY -> Items.ARMOR_STAND; default -> throw new IllegalArgumentException("Unknown training item: " + id); }; }
}
