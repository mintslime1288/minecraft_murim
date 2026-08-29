package com.murimrpg.ui;

import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.service.PlayerProfileService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Cache-only persistent HUD. It intentionally does not invent unimplemented qi or combat values. */
final class GanghoHud {
    private final PlayerProfileService profiles;
    private final Map<UUID, RenderedHud> rendered = new HashMap<>();

    GanghoHud(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    void tick(Player player) {
        Optional<PlayerProfile> optional = profiles.cachedProfile(player.getUniqueId());
        if (optional.isEmpty()) return;
        PlayerProfile profile = optional.orElseThrow();
        CharacterData character = profile.characterData();
        if (!character.completed()) return;
        String value = profile.realmDisplayName() + "|" + character.family().id() + "|"
                + character.baseStrength() + "|" + character.baseSpeed();
        RenderedHud previous = rendered.get(player.getUniqueId());
        Component component;
        if (previous != null && previous.value().equals(value)) {
            component = previous.component();
        } else {
            component = Component.text("境界 ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(profile.realmDisplayName(), NamedTextColor.AQUA))
                    .append(Component.text(" | 家門 ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(character.family().displayName(), NamedTextColor.GOLD))
                    .append(Component.text(" | 힘 ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(character.baseStrength(), NamedTextColor.RED))
                    .append(Component.text(" | 신법 ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(character.baseSpeed(), NamedTextColor.GREEN));
            rendered.put(player.getUniqueId(), new RenderedHud(value, component));
        }
        player.sendActionBar(component);
    }

    void forget(UUID uuid) {
        rendered.remove(uuid);
    }

    void clear() {
        rendered.clear();
    }

    private record RenderedHud(String value, Component component) {}
}
