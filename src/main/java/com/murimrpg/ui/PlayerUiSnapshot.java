package com.murimrpg.ui;

import com.murimrpg.domain.OrganizationDefinition;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.SkillDefinition;
import java.util.Objects;
import java.util.Optional;

public record PlayerUiSnapshot(PlayerProfile profile, PlayerJourney playerJourney, long maxQi,
        Optional<OrganizationDefinition> organization, String rankDisplayName,
        Optional<SkillDefinition> selectedSkill, MartialArtStatus martialArtStatus, long cooldownTicks) {
    public PlayerUiSnapshot {
        Objects.requireNonNull(profile); Objects.requireNonNull(playerJourney); Objects.requireNonNull(organization);
        Objects.requireNonNull(rankDisplayName); Objects.requireNonNull(selectedSkill); Objects.requireNonNull(martialArtStatus);
        if (maxQi < 0 || cooldownTicks < 0) throw new IllegalArgumentException("Negative UI snapshot value");
    }
    public long currentQi() { return playerJourney.journey().currentQi(); }
}
