package com.murimrpg.infrastructure.paper;

import com.murimrpg.domain.HuashanSettings;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class HuashanTrainingDummy {
    private final NamespacedKey entityIdKey;
    private final HuashanSettings settings;

    public HuashanTrainingDummy(JavaPlugin plugin, HuashanSettings settings) {
        this.entityIdKey = new NamespacedKey(plugin, "custom_entity_id");
        this.settings = settings;
    }

    public ArmorStand spawn(Player player) {
        ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, value -> {
            value.setGravity(false);
            value.setInvulnerable(true);
            value.setCustomNameVisible(true);
            value.customName(net.kyori.adventure.text.Component.text(settings.trainingDummyDisplayName()));
            value.getPersistentDataContainer().set(entityIdKey, PersistentDataType.STRING,
                    settings.trainingDummyId());
        });
        return stand;
    }

    public int removeAll() {
        AtomicInteger removed = new AtomicInteger();
        Bukkit.getWorlds().forEach(world -> world.getEntities().stream()
                .filter(this::isTrainingDummy)
                .forEach(entity -> {
                    entity.remove();
                    removed.incrementAndGet();
                }));
        return removed.get();
    }

    public boolean isTrainingDummy(Entity entity) {
        return settings.trainingDummyId().equals(entityId(entity.getPersistentDataContainer()));
    }

    private String entityId(PersistentDataContainer container) {
        return container.getOrDefault(entityIdKey, PersistentDataType.STRING, "");
    }
}
