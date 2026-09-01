package com.murimrpg.ui;

import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

final class GuiItems {
    private GuiItems() { }

    static ItemStack button(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, styled(name, ChatFormatting.LIGHT_PURPLE));
        List<Component> lines = Arrays.stream(lore).map(line -> styled(line, ChatFormatting.GRAY)).toList();
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    static ItemStack item(Item item, String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, styled(name, ChatFormatting.LIGHT_PURPLE));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(line -> styled(line, ChatFormatting.GRAY)).toList()));
        return stack;
    }

    private static Component styled(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
    }
}
