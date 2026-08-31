package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.ProgressUpdate;
import com.murimrpg.domain.RealmPhase;
import com.murimrpg.repository.PlayerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerProfileServiceTest {
    private static final UUID UUID_VALUE = UUID.fromString("c77cb123-c440-4ee3-a337-a892f71213e1");
    private static final Instant NOW = Instant.parse("2026-08-22T02:00:00Z");

    private FailingRepository repository;
    private PlayerProfileService service;

    @BeforeEach
    void setUp() {
        repository = new FailingRepository();
        service = new PlayerProfileService(
                repository,
                Executors.newSingleThreadExecutor(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service.initialize().join();
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.failSaves = false;
        repository.releaseBlockedSave();
        service.shutdown(Duration.ofSeconds(2));
    }

    @Test
    void failedSaveKeepsPreviousCachedProfile() {
        PlayerProfile original = service.loadOrCreate(UUID_VALUE).join();
        repository.failSaves = true;

        assertThrows(CompletionException.class, () -> service.setProgress(
                UUID_VALUE,
                new ProgressUpdate(MajorRealm.THIRD_RATE, RealmPhase.BEGINNER, 100)
        ).join());

        assertEquals(original, service.cachedProfile(UUID_VALUE).orElseThrow());
    }

    @Test
    void resetProgressPersistsRequiredDefaults() {
        service.loadOrCreate(UUID_VALUE).join();
        service.setProgress(
                UUID_VALUE,
                new ProgressUpdate(MajorRealm.SECOND_RATE, RealmPhase.COMPLETE, 400)
        ).join();

        PlayerProfile reset = service.resetProgress(UUID_VALUE).join();

        assertEquals(PlayerProfile.createDefault(UUID_VALUE, NOW), reset);
        assertEquals(reset, repository.findByUuid(UUID_VALUE).orElseThrow());
    }

    @Test
    void resetAllAlsoClearsCharacterCreation() {
        service.loadOrCreate(UUID_VALUE).join();
        service.saveCharacter(UUID_VALUE, new com.murimrpg.domain.CharacterData(true, 2,
                com.murimrpg.domain.Family.NAMGUNG, 14, 11, 1.5,
                com.murimrpg.domain.Constitution.MUGOL, null)).join();

        PlayerProfile reset = service.resetAll(UUID_VALUE).join();

        assertEquals(PlayerProfile.createDefault(UUID_VALUE, NOW), reset);
        assertFalse(reset.characterData().completed());
    }

    @Test
    void shutdownFlushesCachedProfilesBeforeExecutorStops() throws Exception {
        service.loadOrCreate(UUID_VALUE).join();

        service.shutdown(Duration.ofSeconds(2));

        assertEquals(1, repository.saveCalls);
    }

    @Test
    void saveAndUnloadRemovesSuccessfullySavedProfileFromCache() {
        service.loadOrCreate(UUID_VALUE).join();

        service.saveAndUnload(UUID_VALUE).join();

        assertFalse(service.cachedProfile(UUID_VALUE).isPresent());
        assertEquals(ProfileLoadState.NOT_LOADED, service.loadState(UUID_VALUE));
        assertTrue(repository.findByUuid(UUID_VALUE).isPresent());
    }

    @Test
    void failedLogoutSaveKeepsProfileCachedForLaterRecovery() {
        PlayerProfile original = service.loadOrCreate(UUID_VALUE).join();
        repository.failSaves = true;

        assertThrows(CompletionException.class, () -> service.saveAndUnload(UUID_VALUE).join());

        assertEquals(original, service.cachedProfile(UUID_VALUE).orElseThrow());
        assertEquals(ProfileLoadState.ERROR, service.loadState(UUID_VALUE));
    }

    @Test
    void oldQuitSaveDoesNotUnloadCacheUsedByNewSession() throws Exception {
        service.beginSession(UUID_VALUE);
        service.loadOrCreate(UUID_VALUE).join();
        repository.blockNextSave();

        var oldQuitSave = service.saveAndUnload(UUID_VALUE);
        assertTrue(repository.awaitBlockedSave());
        service.beginSession(UUID_VALUE);
        repository.releaseBlockedSave();
        oldQuitSave.join();

        assertTrue(service.cachedProfile(UUID_VALUE).isPresent());
        assertEquals(ProfileLoadState.LOADED, service.loadState(UUID_VALUE));
    }

    @Test
    void successfulManualSaveRecoversErrorLoadState() {
        service.loadOrCreate(UUID_VALUE).join();
        repository.failSaves = true;
        assertThrows(CompletionException.class, () -> service.saveAndUnload(UUID_VALUE).join());
        repository.failSaves = false;

        CachedProfilesSaveResult result = service.saveCachedProfiles().join();

        assertEquals(1, result.successCount());
        assertEquals(ProfileLoadState.LOADED, service.loadState(UUID_VALUE));
    }

    @Test
    void manualSaveReportsFailureAndKeepsCachedProfile() {
        PlayerProfile original = service.loadOrCreate(UUID_VALUE).join();
        repository.failSaves = true;

        CachedProfilesSaveResult result = service.saveCachedProfiles().join();

        assertEquals(0, result.successCount());
        assertEquals(1, result.failureCount());
        assertTrue(result.failures().containsKey(UUID_VALUE));
        assertEquals(original, service.cachedProfile(UUID_VALUE).orElseThrow());
    }

    @Test
    void manualSaveReportsSuccessfulCachedProfiles() {
        service.loadOrCreate(UUID_VALUE).join();

        CachedProfilesSaveResult result = service.saveCachedProfiles().join();

        assertEquals(1, result.successCount());
        assertEquals(0, result.failureCount());
        assertEquals(1, repository.saveCalls);
    }

    private static final class FailingRepository implements PlayerRepository {
        private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
        private boolean failSaves;
        private int saveCalls;
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
            if (failSaves) {
                throw new IllegalStateException("simulated save failure");
            }
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
            saveCalls++;
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
