package com.murimrpg.infrastructure.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.RealmPhase;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePlayerRepositoryTest {
    private static final UUID UUID_VALUE = UUID.fromString("23c44ab5-66cc-42df-9307-416de3144f11");
    private static final Instant CREATED_AT = Instant.parse("2026-08-22T01:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void savesAndReloadsEveryProfileField() {
        SqlitePlayerRepository repository = repository();
        repository.initialize();
        repository.findOrCreate(PlayerProfile.createDefault(UUID_VALUE, CREATED_AT));
        PlayerProfile changed = new PlayerProfile(
                UUID_VALUE,
                MajorRealm.SECOND_RATE,
                RealmPhase.LATE,
                1234,
                Set.of("TRIAL_ONE", "TRIAL_TWO"),
                Set.of("QI_BAR", "BLACK_PINE_ENTRY"),
                CREATED_AT,
                CREATED_AT.plusSeconds(60)
        );

        repository.save(changed);
        PlayerProfile loaded = repository.findByUuid(UUID_VALUE).orElseThrow();

        assertEquals(changed, loaded);
        assertEquals(Set.of("TRIAL_ONE", "TRIAL_TWO"), loaded.completedRequirements());
        assertEquals(Set.of("QI_BAR", "BLACK_PINE_ENTRY"), loaded.unlockedContent());
    }

    @Test
    void dataSurvivesRepositoryRecreationLikeServerRestart() {
        SqlitePlayerRepository firstRepository = repository();
        firstRepository.initialize();
        PlayerProfile saved = PlayerProfile.createDefault(UUID_VALUE, CREATED_AT)
                .withProgress(MajorRealm.THIRD_RATE, RealmPhase.BEGINNER, 100, CREATED_AT.plusSeconds(1));
        firstRepository.findOrCreate(PlayerProfile.createDefault(UUID_VALUE, CREATED_AT));
        firstRepository.save(saved);

        SqlitePlayerRepository restartedRepository = repository();
        restartedRepository.initialize();

        assertEquals(saved, restartedRepository.findByUuid(UUID_VALUE).orElseThrow());
    }

    @Test
    void findOrCreateDoesNotDuplicateOrReplaceExistingUuid() {
        SqlitePlayerRepository repository = repository();
        repository.initialize();
        PlayerProfile first = repository.findOrCreate(PlayerProfile.createDefault(UUID_VALUE, CREATED_AT));
        PlayerProfile differentDefault = PlayerProfile.createDefault(UUID_VALUE, CREATED_AT.plusSeconds(10));

        PlayerProfile second = repository.findOrCreate(differentDefault);

        assertEquals(first, second);
        assertEquals(CREATED_AT, second.createdAt());
    }

    @Test
    void migratesEmptyDatabaseToLatestVersion() {
        SqlitePlayerRepository repository = repository();

        repository.initialize();

        assertEquals(SqliteSchemaMigrator.LATEST_VERSION, repository.schemaVersion());
        assertFalse(repository.findByUuid(UUID_VALUE).isPresent());
        assertTrue(tempDirectory.resolve("profiles.db").toFile().isFile());
    }

    @Test
    void rejectsInvalidStoredRealmCombinationInsteadOfReplacingItWithDefaults() throws Exception {
        SqlitePlayerRepository repository = repository();
        repository.initialize();
        String connectionUrl = "jdbc:sqlite:" + tempDirectory.resolve("profiles.db").toAbsolutePath();
        try (var connection = DriverManager.getConnection(connectionUrl);
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO player_profiles(
                            uuid, major_realm_id, phase_id, cultivation_exp,
                            completed_requirements, unlocked_content, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setString(1, UUID_VALUE.toString());
            statement.setString(2, "THIRD_RATE");
            statement.setString(3, "EARLY");
            statement.setLong(4, 100);
            statement.setString(5, "");
            statement.setString(6, "QI_BAR");
            statement.setLong(7, CREATED_AT.toEpochMilli());
            statement.setLong(8, CREATED_AT.toEpochMilli());
            statement.executeUpdate();
        }

        assertThrows(ProfileDataException.class, () -> repository.findByUuid(UUID_VALUE));
    }

    private SqlitePlayerRepository repository() {
        return new SqlitePlayerRepository(
                tempDirectory.resolve("profiles.db"),
                Logger.getLogger("SqlitePlayerRepositoryTest")
        );
    }
}
