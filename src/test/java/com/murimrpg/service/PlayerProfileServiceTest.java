package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.concurrent.Executors;
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
    void shutdownFlushesCachedProfilesBeforeExecutorStops() throws Exception {
        service.loadOrCreate(UUID_VALUE).join();

        service.shutdown(Duration.ofSeconds(2));

        assertEquals(1, repository.saveCalls);
    }

    private static final class FailingRepository implements PlayerRepository {
        private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
        private boolean failSaves;
        private int saveCalls;

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
            saveCalls++;
            profiles.put(profile.uuid(), profile);
        }
    }
}
