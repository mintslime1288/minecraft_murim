package com.murimrpg.infrastructure.paper;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.HuashanSettings;
import com.murimrpg.domain.MonsterDefinition;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper adapter for promotion-test entities. Game completion rules remain in HuashanService. */
public final class HuashanExamEntities {
    private final JavaPlugin plugin;
    private final ContentCatalog catalog;
    private final NamespacedKey contentIdKey;
    private final NamespacedKey ownerKey;

    public HuashanExamEntities(JavaPlugin plugin, ContentCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.contentIdKey = new NamespacedKey(plugin, "content_id");
        this.ownerKey = new NamespacedKey(plugin, "exam_owner");
    }

    public Villager spawnExaminer(Player owner) {
        HuashanSettings settings = catalog.huashanSettings();
        Villager entity = owner.getWorld().spawn(owner.getLocation(), Villager.class);
        configure(entity, settings.examinerId(), settings.examinerDisplayName(), settings.examinerMaxHealth(), owner);
        entity.setAI(false);
        entity.setRemoveWhenFarAway(false);
        return entity;
    }

    public Zombie spawnBanditLeader(Player owner) {
        MonsterDefinition monster = catalog.findMonster("BANDIT_LEADER").orElseThrow();
        Zombie entity = owner.getWorld().spawn(owner.getLocation(), Zombie.class);
        configure(entity, monster.id(), monster.displayName(), monster.maxHealth(), owner);
        var attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) attackDamage.setBaseValue(monster.attackDamage());
        entity.setRemoveWhenFarAway(false);
        return entity;
    }

    public Optional<String> contentId(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(contentIdKey, PersistentDataType.STRING));
    }

    public Optional<UUID> owner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try { return value == null ? Optional.empty() : Optional.of(UUID.fromString(value)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public int removeAll() {
        int count = 0;
        for (var world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (contentId(entity).filter(id -> id.equals(catalog.huashanSettings().examinerId())
                        || id.equals("BANDIT_LEADER")).isPresent()) { entity.remove(); count++; }
            }
        }
        return count;
    }

    private void configure(LivingEntity entity, String id, String name, double maxHealth, Player owner) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) throw new IllegalStateException("Entity has no max-health attribute: " + entity.getType());
        attribute.setBaseValue(maxHealth);
        entity.setHealth(maxHealth);
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(contentIdKey, PersistentDataType.STRING, id);
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
    }
}
