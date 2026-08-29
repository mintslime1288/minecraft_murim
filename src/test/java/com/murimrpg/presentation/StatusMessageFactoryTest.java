package com.murimrpg.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PromotionResult;
import com.murimrpg.domain.RealmPhase;
import com.murimrpg.domain.RealmProgressView;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class StatusMessageFactoryTest {
    @Test
    void maximumStageMessageUsesConfiguredFinalStageDisplayName() {
        var progression = new CultivationConfigLoader().load(Path.of("src/main/resources/realms.yml"));
        var finalStage = progression.finalStage();
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        PlayerProfile profile = new PlayerProfile(
                UUID.fromString("77d9b08d-0bc8-4608-8fd7-a0e5270bb66a"),
                MajorRealm.SAENGSAGYEONG_REALM,
                RealmPhase.COMPLETE,
                finalStage.requiredCultivationExp(),
                Set.of(),
                Set.of("QI_BAR"),
                now,
                now
        );

        Component message = new StatusMessageFactory()
                .promotion(PromotionResult.maximum(profile, finalStage))
                .getFirst();

        assertEquals(
                Component.text("현재 최고 경지인 ", NamedTextColor.GRAY)
                        .append(Component.text("[생사경·원만]입니다.", NamedTextColor.GOLD)),
                message
        );

        var realmMessages = new StatusMessageFactory().realm(new RealmProgressView(
                profile,
                finalStage,
                java.util.Optional.empty(),
                0,
                Set.of(),
                Set.of()
        ));
        assertEquals(
                Component.text("현재 최고 경지: ", NamedTextColor.GRAY)
                        .append(Component.text("[생사경·원만]", NamedTextColor.GOLD)),
                realmMessages.get(2)
        );
    }
}
