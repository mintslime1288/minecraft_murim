package com.murimrpg.ui;

import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.service.PlayerContentService;
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
    private final PlayerContentService content;
    private final CultivationProgression progression;
    private final ContentCatalog catalog;
    private final Map<UUID, RenderedHud> rendered = new HashMap<>();

    GanghoHud(PlayerProfileService profiles, PlayerContentService content, CultivationProgression progression,
            ContentCatalog catalog) {
        this.profiles = profiles;
        this.content = content;
        this.progression = progression;
        this.catalog = catalog;
    }

    void tick(Player player) {
        Optional<PlayerProfile> optional = profiles.cachedProfile(player.getUniqueId());
        if (optional.isEmpty()) return;
        PlayerProfile profile = optional.orElseThrow();
        CharacterData character = profile.characterData();
        if (!character.completed()) return;
        PlayerJourney journey = content.cached(player.getUniqueId()).orElse(null);
        String skill = journey == null ? "무공 없음" : journey.journey().activeSkillId()
                .flatMap(catalog::findSkill).map(value -> "심법 " + value.displayName()).orElse("무공 없음");
        long qi = journey == null ? 0 : journey.journey().currentQi();
        long maxQi = progression.stage(profile).maxQi();
        String value = profile.realmDisplayName() + "|" + skill + "|" + qi + "|" + maxQi;
        RenderedHud previous = rendered.get(player.getUniqueId());
        Component component;
        if (previous != null && previous.value().equals(value)) {
            component = previous.component();
        } else {
            component = Component.text("☯ 내공 ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(qi + "/" + maxQi, NamedTextColor.AQUA))
                    .append(Component.text(" | ⚔ ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(skill, NamedTextColor.GOLD))
                    .append(Component.text(" | 준비", NamedTextColor.GREEN));
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
