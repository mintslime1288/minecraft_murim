package com.murimrpg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.PlayerContentState;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerJourneyState;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.infrastructure.config.ContentConfigLoader;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UiViewServiceTest {
    private final CultivationProgression progression = new CultivationConfigLoader()
            .load(resource("realms.yml"));
    private final ContentCatalog catalog = new ContentConfigLoader().load(resource("factions.yml"),
            resource("sects.yml"), resource("ranks.yml"), resource("skills.yml"),
            resource("monsters.yml"), resource("quests.yml"), progression);
    private final UiViewService views = new UiViewService(catalog, progression);

    @Test
    void buildsReadyHuashanSnapshotFromDomainState() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = PlayerProfile.createDefault(uuid, Instant.EPOCH);
        PlayerJourney journey = new PlayerJourney(
                new PlayerContentState(uuid, Optional.of("HUASHAN_SECT"),
                        Optional.of("THIRD_GENERATION_DISCIPLE"), 0, Set.of("LIUHE_ART")),
                new PlayerJourneyState(uuid, Optional.empty(), 0, 0,
                        Optional.of("LIUHE_ART"), 100, Set.of()));

        PlayerUiSnapshot snapshot = views.snapshot(profile, journey, 0);

        assertEquals("화산파", snapshot.organization().orElseThrow().displayName());
        assertEquals("삼대제자", snapshot.rankDisplayName());
        assertEquals(progression.stage(profile).maxQi(), snapshot.maxQi());
        assertEquals(MartialArtStatus.READY, snapshot.martialArtStatus());
    }

    @Test
    void rendersActiveTrainingQuestProgress() {
        UUID uuid = UUID.randomUUID();
        PlayerJourney journey = new PlayerJourney(PlayerContentState.empty(uuid),
                new PlayerJourneyState(uuid, Optional.of("HUASHAN_ENTRANCE_TRIAL"), 4, 6,
                        Optional.empty(), 0, Set.of()));

        QuestSidebarView sidebar = views.activeQuest(journey).orElseThrow();

        assertEquals("화산파 입문 시험", sidebar.title());
        assertEquals(50, sidebar.progressPercent());
        assertTrue(sidebar.lines().contains("표적 4/10"));
        assertTrue(sidebar.lines().contains("운기 6/10초"));
    }

    private static Path resource(String name) { return Path.of("src/main/resources", name); }
}
