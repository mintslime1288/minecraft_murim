package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PromotionResult;
import com.murimrpg.domain.RealmPhase;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import com.murimrpg.repository.PlayerRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgressionServiceTest {
    private static final UUID UUID_VALUE = UUID.fromString("7591e7d4-02fc-4cb1-9264-d050216153e7");
    private static final Instant NOW = Instant.parse("2026-08-22T05:00:00Z");

    private FakeRepository repository;
    private PlayerProfileService profileService;
    private ProgressionService progressionService;

    @BeforeEach
    void setUp() {
        CultivationProgression progression = new CultivationConfigLoader()
                .load(Path.of("src/main/resources/realms.yml"));
        repository = new FakeRepository();
        profileService = new PlayerProfileService(
                repository,
                Executors.newSingleThreadExecutor(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        profileService.initialize().join();
        progressionService = new ProgressionService(profileService, progression);
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.releaseBlockedSave();
        profileService.shutdown(Duration.ofSeconds(2));
    }

    @Test
    void insufficientExpBlocksPromotion() {
        PromotionResult result = progressionService.promote(UUID_VALUE).join();

        assertEquals(PromotionResult.Status.BLOCKED, result.status());
        assertEquals(100, result.missingExp());
        assertEquals("third_rate_initiate", result.currentStage().orElseThrow().stageId());
    }

    @Test
    void enoughExpPromotesExactlyOneStagePerRequest() {
        progressionService.setExp(UUID_VALUE, 4000).join();

        PromotionResult result = progressionService.promote(UUID_VALUE).join();

        assertEquals(PromotionResult.Status.SUCCESS, result.status());
        assertEquals("third_rate_beginner", result.nextStage().orElseThrow().stageId());
        assertEquals(RealmPhase.BEGINNER, result.profile().orElseThrow().phase());
        assertEquals(Set.of("CRIMSON_COMBO"), result.newlyUnlocked());
    }

    @Test
    void realmBoundaryRequiresTrainingTrial() {
        repository.seed(new PlayerProfile(
                UUID_VALUE, MajorRealm.THIRD_RATE, RealmPhase.PEAK, 700,
                Set.of(), Set.of("QI_BAR"), NOW, NOW
        ));

        PromotionResult blocked = progressionService.promote(UUID_VALUE).join();
        progressionService.grantRequirement(UUID_VALUE, "training_trial").join();
        PromotionResult promoted = progressionService.promote(UUID_VALUE).join();

        assertEquals(Set.of("training_trial"), blocked.missingRequirements());
        assertEquals("second_rate_early", promoted.nextStage().orElseThrow().stageId());
    }

    @Test
    void firstRateBoundaryRequiresBlackPineChief() {
        repository.seed(new PlayerProfile(
                UUID_VALUE, MajorRealm.SECOND_RATE, RealmPhase.COMPLETE, 2200,
                Set.of(), Set.of("QI_BAR"), NOW, NOW
        ));

        PromotionResult blocked = progressionService.promote(UUID_VALUE).join();
        progressionService.grantRequirement(UUID_VALUE, "black_pine_chief").join();
        PromotionResult promoted = progressionService.promote(UUID_VALUE).join();

        assertEquals(Set.of("black_pine_chief"), blocked.missingRequirements());
        assertEquals("first_rate_early", promoted.nextStage().orElseThrow().stageId());
    }

    @Test
    void grantsAndRevokesRequirements() {
        var granted = progressionService.grantRequirement(UUID_VALUE, "training_trial").join();
        var revoked = progressionService.revokeRequirement(UUID_VALUE, "training_trial").join();

        assertTrue(granted.changed());
        assertTrue(granted.profile().completedRequirements().contains("training_trial"));
        assertTrue(revoked.changed());
        assertFalse(revoked.profile().completedRequirements().contains("training_trial"));
    }

    @Test
    void rejectsPromotionPastFinalStage() {
        repository.seed(new PlayerProfile(
                UUID_VALUE, MajorRealm.FIRST_RATE, RealmPhase.PEAK, 4000,
                Set.of("training_trial", "black_pine_chief"), Set.of("QI_BAR"), NOW, NOW
        ));

        PromotionResult result = progressionService.promote(UUID_VALUE).join();

        assertEquals(PromotionResult.Status.MAXIMUM_STAGE, result.status());
    }

    @Test
    void concurrentPromotionRequestDoesNotPromoteTwice() throws Exception {
        progressionService.setExp(UUID_VALUE, 4000).join();
        repository.blockNextSave();

        var first = progressionService.promote(UUID_VALUE);
        assertTrue(repository.awaitBlockedSave());
        var duplicate = progressionService.promote(UUID_VALUE);
        repository.releaseBlockedSave();

        assertEquals(PromotionResult.Status.SUCCESS, first.join().status());
        assertEquals(PromotionResult.Status.IN_PROGRESS, duplicate.join().status());
        assertEquals(RealmPhase.BEGINNER, repository.findByUuid(UUID_VALUE).orElseThrow().phase());
    }

    @Test
    void sameTickTokenDoesNotPromoteTwiceAfterFirstRequestCompletes() {
        progressionService.setExp(UUID_VALUE, 4000).join();

        PromotionResult first = progressionService.promote(UUID_VALUE, 42).join();
        PromotionResult duplicate = progressionService.promote(UUID_VALUE, 42).join();

        assertEquals(PromotionResult.Status.SUCCESS, first.status());
        assertEquals(PromotionResult.Status.IN_PROGRESS, duplicate.status());
        assertEquals(RealmPhase.BEGINNER, repository.findByUuid(UUID_VALUE).orElseThrow().phase());
    }

    @Test
    void doesNotDuplicateAlreadyUnlockedContent() {
        repository.seed(new PlayerProfile(
                UUID_VALUE, MajorRealm.THIRD_RATE, RealmPhase.INITIATE, 100,
                Set.of(), Set.of("QI_BAR", "CRIMSON_COMBO"), NOW, NOW
        ));

        PromotionResult result = progressionService.promote(UUID_VALUE).join();

        assertTrue(result.newlyUnlocked().isEmpty());
        assertEquals(2, result.profile().orElseThrow().unlockedContent().size());
    }

    @Test
    void prepareAndClearTestProgressHaveExactState() {
        PlayerProfile prepared = progressionService.prepareTestProgress(UUID_VALUE).join();
        PlayerProfile cleared = progressionService.clearTestProgress(UUID_VALUE).join();

        assertEquals(4000, prepared.cultivationExp());
        assertEquals(Set.of("training_trial", "black_pine_chief"), prepared.completedRequirements());
        assertEquals(Set.of("QI_BAR"), prepared.unlockedContent());
        assertEquals(MajorRealm.THIRD_RATE, prepared.majorRealm());
        assertEquals(RealmPhase.INITIATE, prepared.phase());
        assertEquals(0, cleared.cultivationExp());
        assertTrue(cleared.completedRequirements().isEmpty());
        assertEquals(Set.of("QI_BAR"), cleared.unlockedContent());
    }

    private static final class FakeRepository implements PlayerRepository {
        private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
        private volatile CountDownLatch saveStarted;
        private volatile CountDownLatch allowSave;

        @Override
        public void initialize() {
        }

        @Override
        public Optional<PlayerProfile> findByUuid(UUID uuid) {
            return Optional.ofNullable(profiles.get(uuid));
        }

        @Override
        public PlayerProfile findOrCreate(PlayerProfile defaultProfile) {
            return profiles.computeIfAbsent(defaultProfile.uuid(), ignored -> defaultProfile);
        }

        @Override
        public void save(PlayerProfile profile) {
            CountDownLatch started = saveStarted;
            CountDownLatch release = allowSave;
            if (started != null && release != null) {
                started.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release test save");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    saveStarted = null;
                    allowSave = null;
                }
            }
            profiles.put(profile.uuid(), profile);
        }

        void seed(PlayerProfile profile) {
            profiles.put(profile.uuid(), profile);
        }

        void blockNextSave() {
            saveStarted = new CountDownLatch(1);
            allowSave = new CountDownLatch(1);
        }

        boolean awaitBlockedSave() throws InterruptedException {
            return saveStarted.await(2, TimeUnit.SECONDS);
        }

        void releaseBlockedSave() {
            CountDownLatch release = allowSave;
            if (release != null) {
                release.countDown();
            }
        }
    }
}
