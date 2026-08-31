package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.*;

import com.murimrpg.domain.*;
import com.murimrpg.infrastructure.config.ContentConfigLoader;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import com.murimrpg.repository.PlayerRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HuashanServiceTest {
    private final UUID uuid = UUID.fromString("78d1f67a-8b12-4b69-a8d1-81535461bcc8");
    private ExecutorService executor;
    private MemoryRepository repository;
    private PlayerContentService states;
    private HuashanService service;

    @BeforeEach
    void setUp() {
        CultivationProgression progression = new CultivationConfigLoader().load(Path.of("src/main/resources/realms.yml"));
        ContentCatalog catalog = new ContentConfigLoader().load(Path.of("src/main/resources/factions.yml"),
                Path.of("src/main/resources/sects.yml"), Path.of("src/main/resources/ranks.yml"),
                Path.of("src/main/resources/skills.yml"), Path.of("src/main/resources/monsters.yml"),
                Path.of("src/main/resources/quests.yml"), progression);
        executor = Executors.newSingleThreadExecutor();
        repository = new MemoryRepository();
        states = new PlayerContentService(repository, executor);
        service = new HuashanService(states, catalog);
    }

    @AfterEach void tearDown() { executor.shutdownNow(); }

    @Test
    void completesEntranceLiuheAndPromotionFlowExactlyOnce() {
        service.apply(uuid, "HUASHAN_SECT").join();
        assertThrows(Exception.class, () -> service.apply(uuid, "HUASHAN_SECT").join());
        for (int index = 0; index < 10; index++) service.recordDummyHit(uuid, "HUASHAN_TRAINING_DUMMY").join();
        for (int index = 0; index < 10; index++) service.meditateSecond(uuid, 100, 5).join();

        PlayerJourney entrance = states.cached(uuid).orElseThrow();
        assertEquals("HUASHAN_SECT", entrance.content().organizationId().orElseThrow());
        assertEquals(HuashanService.THIRD, entrance.content().organizationRankId().orElseThrow());
        assertEquals("LIUHE_ART_QUEST", entrance.journey().activeActivityId().orElseThrow());

        service.completeForTest(uuid, "LIUHE_ART_QUEST").join();
        PlayerJourney liuhe = states.cached(uuid).orElseThrow();
        assertEquals(Set.of("LIUHE_ART"), liuhe.content().learnedSkillIds());
        assertEquals("LIUHE_ART", liuhe.journey().activeSkillId().orElseThrow());

        service.grantSkill(uuid, "LIUHE_ART").join();
        assertEquals(1, states.cached(uuid).orElseThrow().content().learnedSkillIds().size());
        service.setRankForTest(uuid, "HUASHAN_SECT", HuashanService.SECOND).join();
        service.recordMonsterKill(uuid, "BANDIT_LEADER").join();
        PlayerJourney promoted = states.cached(uuid).orElseThrow();
        assertEquals(HuashanService.FIRST, promoted.content().organizationRankId().orElseThrow());
        assertTrue(promoted.content().learnedSkillIds().contains("PURPLE_MIST_DIVINE_ART"));
    }

    @Test
    void continuousEntranceMeditationResetsButCumulativeQuestDoesNot() {
        service.apply(uuid, "HUASHAN_SECT").join();
        for (int index = 0; index < 4; index++) service.meditateSecond(uuid, 100, 5).join();
        service.cancelContinuousMeditation(uuid).join();
        assertEquals(0, states.cached(uuid).orElseThrow().journey().meditationSeconds());

        service.completeForTest(uuid, "HUASHAN_ENTRANCE_TRIAL").join();
        for (int index = 0; index < 4; index++) service.meditateSecond(uuid, 100, 5).join();
        service.cancelContinuousMeditation(uuid).join();
        assertEquals(4, states.cached(uuid).orElseThrow().journey().meditationSeconds());
    }

    @Test
    void wrongDummyDoesNotProgressAndQiIsCapped() {
        service.apply(uuid, "HUASHAN_SECT").join();
        service.recordDummyHit(uuid, "OTHER_DUMMY").join();
        assertEquals(0, states.cached(uuid).orElseThrow().journey().dummyHits());
        assertTrue(states.cached(uuid).orElseThrow().content().organizationId().isEmpty());

        service.grantSkill(uuid, "LIUHE_ART").join();
        service.equip(uuid, "LIUHE_ART").join();
        service.setQi(uuid, 9, 10).join();
        service.meditateSecond(uuid, 10, 5).join();
        assertEquals(10, states.cached(uuid).orElseThrow().journey().currentQi());
        assertThrows(Exception.class, () -> service.setQi(uuid, -1, 10).join());
    }

    @Test
    void onlyHuashanDisciplesCanPreparePromotionTrial() {
        assertThrows(Exception.class, () -> service.setRankForTest(uuid, "WUDANG_SECT", HuashanService.SECOND).join());
        assertDoesNotThrow(() -> service.setRankForTest(uuid, "HUASHAN_SECT", HuashanService.THIRD).join());
    }

    @Test
    void secondDiscipleTrialCompletesAtExactlyFiftyPercent() {
        service.setRankForTest(uuid, "HUASHAN_SECT", HuashanService.THIRD).join();
        service.recordExaminerHealth(uuid, "HUASHAN_FIRST_DISCIPLE_EXAMINER", 50.1).join();
        assertEquals(HuashanService.THIRD, states.cached(uuid).orElseThrow().content().organizationRankId().orElseThrow());
        service.recordExaminerHealth(uuid, "HUASHAN_FIRST_DISCIPLE_EXAMINER", 50.0).join();
        assertEquals(HuashanService.SECOND, states.cached(uuid).orElseThrow().content().organizationRankId().orElseThrow());
    }

    @Test
    void firstDiscipleTrialRequiresBanditLeaderKill() {
        service.setRankForTest(uuid, "HUASHAN_SECT", HuashanService.SECOND).join();
        service.recordMonsterKill(uuid, "BANDIT").join();
        assertEquals(HuashanService.SECOND, states.cached(uuid).orElseThrow().content().organizationRankId().orElseThrow());
        service.recordMonsterKill(uuid, "BANDIT_LEADER").join();
        assertEquals(HuashanService.FIRST, states.cached(uuid).orElseThrow().content().organizationRankId().orElseThrow());
    }

    private static final class MemoryRepository implements PlayerRepository {
        private final Map<UUID, PlayerContentState> content = new HashMap<>();
        private final Map<UUID, PlayerJourneyState> journey = new HashMap<>();
        @Override public void initialize() {}
        @Override public Optional<PlayerProfile> findByUuid(UUID uuid) { return Optional.empty(); }
        @Override public PlayerProfile findOrCreate(PlayerProfile value) { return value; }
        @Override public void save(PlayerProfile profile) {}
        @Override public Optional<PlayerContentState> findContentState(UUID uuid) { return Optional.ofNullable(content.get(uuid)); }
        @Override public void saveContentState(PlayerContentState state) { content.put(state.uuid(), state); }
        @Override public Optional<PlayerJourneyState> findJourneyState(UUID uuid) { return Optional.ofNullable(journey.get(uuid)); }
        @Override public void saveJourneyState(PlayerJourneyState state) { journey.put(state.uuid(), state); }
    }
}
