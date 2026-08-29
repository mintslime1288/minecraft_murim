package com.murimrpg.ui;

import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.PlayerProfile;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

/** Read-only profile inventory; persistent character data remains owned by the service layer. */
public final class GanghoProfileGui implements Listener {
    private static final Component TITLE = Component.text("江湖錄 · 인물정보", NamedTextColor.DARK_PURPLE);
    private static final int CLOSE_SLOT = 22;

    public void open(Player player, PlayerProfile profile) {
        CharacterData data = profile.characterData();
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, TITLE);
        holder.bind(inventory);
        inventory.setItem(4, playerHead(player, profile));
        inventory.setItem(10, item(Material.IRON_INGOT, "힘", NamedTextColor.RED,
                List.of("기초 힘: " + data.baseStrength())));
        inventory.setItem(11, item(Material.SUGAR, "신법", NamedTextColor.GREEN,
                List.of("기초 신법: " + data.baseSpeed())));
        inventory.setItem(13, item(Material.AMETHYST_SHARD, "境界 · 경지", NamedTextColor.AQUA,
                List.of(profile.realmDisplayName(), "수련 EXP: " + profile.cultivationExp())));
        inventory.setItem(15, item(Material.EXPERIENCE_BOTTLE, "재능", NamedTextColor.LIGHT_PURPLE,
                List.of("숙련도 획득 배율: x" + data.talentMultiplier())));
        inventory.setItem(16, item(Material.HEART_OF_THE_SEA, "체질", NamedTextColor.GOLD,
                List.of(data.constitution().displayName(), data.constitution().description())));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "닫기", NamedTextColor.RED, List.of("메뉴를 닫습니다.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == CLOSE_SLOT && event.getWhoClicked() instanceof Player player) player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
        }
    }

    private ItemStack playerHead(Player player, PlayerProfile profile) {
        CharacterData data = profile.characterData();
        ItemStack item = item(Material.PLAYER_HEAD, player.getName(), NamedTextColor.GOLD,
                List.of("가문: " + data.family().displayName(), "경지: " + profile.realmDisplayName()));
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        void bind(Inventory inventory) { this.inventory = inventory; }
        @Override public @NotNull Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("Profile GUI is not initialized");
            return inventory;
        }
    }
}
