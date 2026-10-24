package com.murimrpg.ui;

import java.util.function.IntConsumer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Read-only Vanilla chest menu with server-side button dispatch. */
final class GanghoMenu extends ChestMenu {
    private final IntConsumer topClick;
    GanghoMenu(int syncId, Inventory inventory, Container container, IntConsumer topClick) {
        this(syncId, inventory, container, 3, topClick);
    }
    GanghoMenu(int syncId, Inventory inventory, Container container, int rows, IntConsumer topClick) {
        super(menuType(rows), syncId, inventory, container, rows);
        this.topClick = topClick;
    }
    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < getContainer().getContainerSize()) { topClick.accept(slot); return; }
        super.clicked(slot, button, input, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }

    private static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 3 -> MenuType.GENERIC_9x3;
            case 5 -> MenuType.GENERIC_9x5;
            default -> throw new IllegalArgumentException("Unsupported chest row count: " + rows);
        };
    }
}
