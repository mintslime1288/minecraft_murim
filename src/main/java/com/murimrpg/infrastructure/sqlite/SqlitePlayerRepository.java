package com.murimrpg.infrastructure.sqlite;

import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.CharacterDraft;
import com.murimrpg.domain.Constitution;
import com.murimrpg.domain.Family;
import com.murimrpg.domain.CharacterTrait;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PlayerContentState;
import com.murimrpg.domain.PlayerJourneyState;
import com.murimrpg.domain.RealmPhase;
import com.murimrpg.repository.PlayerRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqlitePlayerRepository implements PlayerRepository {
    private static final String SELECT_PROFILE = """
            SELECT uuid, major_realm_id, phase_id, cultivation_exp,
                   completed_requirements, unlocked_content, created_at, updated_at,
                   character_completed, rerolls_used, family_id, base_strength, base_speed, talent_multiplier,
                   constitution_id, draft_families, draft_strength, draft_speed, draft_talent, draft_constitution_id,
                   draft_selected_family_id, draft_locked_traits
            FROM player_profiles
            WHERE uuid = ?
            """;

    private final Path databasePath;
    private final String connectionUrl;
    private final Logger logger;
    private final SqliteSchemaMigrator migrator;

    public SqlitePlayerRepository(Path databasePath, Logger logger) {
        this(databasePath, logger, new SqliteSchemaMigrator());
    }

    SqlitePlayerRepository(Path databasePath, Logger logger, SqliteSchemaMigrator migrator) {
        this.databasePath = databasePath.toAbsolutePath();
        this.connectionUrl = "jdbc:sqlite:" + this.databasePath;
        this.logger = logger;
        this.migrator = migrator;
    }

    @Override
    public void initialize() {
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = openConnection()) {
                migrator.migrate(connection);
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite database at " + databasePath, exception);
        }
    }

    @Override
    public Optional<PlayerProfile> findByUuid(UUID uuid) {
        try (Connection connection = openConnection()) {
            return findByUuid(connection, uuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load profile for " + uuid, exception);
        }
    }

    @Override
    public PlayerProfile findOrCreate(PlayerProfile defaultProfile) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PlayerProfile> existing = findByUuid(connection, defaultProfile.uuid());
                if (existing.isPresent()) {
                    connection.commit();
                    return existing.orElseThrow();
                }

                insertIfAbsent(connection, defaultProfile);
                PlayerProfile stored = findByUuid(connection, defaultProfile.uuid())
                        .orElseThrow(() -> new SQLException(
                                "Profile was not available after insert for " + defaultProfile.uuid()
                        ));
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find or create profile for " + defaultProfile.uuid(), exception);
        }
    }

    @Override
    public void save(PlayerProfile profile) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                UPDATE player_profiles
                SET major_realm_id = ?, phase_id = ?, cultivation_exp = ?,
                    completed_requirements = ?, unlocked_content = ?,
                    created_at = ?, updated_at = ?, character_completed = ?, rerolls_used = ?, family_id = ?,
                    base_strength = ?, base_speed = ?, talent_multiplier = ?, constitution_id = ?, draft_families = ?,
                    draft_strength = ?, draft_speed = ?, draft_talent = ?, draft_constitution_id = ?,
                    draft_selected_family_id = ?, draft_locked_traits = ?
                WHERE uuid = ?
                """)) {
            bindMutableProfile(statement, profile);
            statement.setString(22, profile.uuid().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Profile does not exist for update: " + profile.uuid());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save profile for " + profile.uuid(), exception);
        }
    }

    public int schemaVersion() {
        try (Connection connection = openConnection()) {
            return migrator.schemaVersion(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read database schema version", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(connectionUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private Optional<PlayerProfile> findByUuid(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PROFILE)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(readProfile(result));
            }
        }
    }

    private void insertIfAbsent(Connection connection, PlayerProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_profiles(
                    uuid, major_realm_id, phase_id, cultivation_exp,
                    completed_requirements, unlocked_content, created_at, updated_at, character_completed, rerolls_used,
                    family_id, base_strength, base_speed, talent_multiplier, constitution_id, draft_families,
                    draft_strength, draft_speed, draft_talent, draft_constitution_id, draft_selected_family_id, draft_locked_traits
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO NOTHING
                """)) {
            statement.setString(1, profile.uuid().toString());
            statement.setString(2, profile.majorRealm().id());
            statement.setString(3, profile.phase().id());
            statement.setLong(4, profile.cultivationExp());
            statement.setString(5, ProfileSetCodec.encode(profile.completedRequirements()));
            statement.setString(6, ProfileSetCodec.encode(profile.unlockedContent()));
            statement.setLong(7, profile.createdAt().toEpochMilli());
            statement.setLong(8, profile.updatedAt().toEpochMilli());
            bindCharacter(statement, 9, profile.characterData());
            statement.executeUpdate();
        }
    }

    private void bindMutableProfile(PreparedStatement statement, PlayerProfile profile) throws SQLException {
        statement.setString(1, profile.majorRealm().id());
        statement.setString(2, profile.phase().id());
        statement.setLong(3, profile.cultivationExp());
        statement.setString(4, ProfileSetCodec.encode(profile.completedRequirements()));
        statement.setString(5, ProfileSetCodec.encode(profile.unlockedContent()));
        statement.setLong(6, profile.createdAt().toEpochMilli());
        statement.setLong(7, profile.updatedAt().toEpochMilli());
        bindCharacter(statement, 8, profile.characterData());
    }

    private PlayerProfile readProfile(ResultSet result) {
        String uuidValue = "unknown";
        String majorRealmId = "unknown";
        String phaseId = "unknown";
        try {
            uuidValue = result.getString("uuid");
            String storedMajorRealmId = result.getString("major_realm_id");
            String storedPhaseId = result.getString("phase_id");
            majorRealmId = storedMajorRealmId;
            phaseId = storedPhaseId;
            MajorRealm majorRealm = MajorRealm.fromId(storedMajorRealmId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown major realm ID: " + storedMajorRealmId
                    ));
            RealmPhase phase = RealmPhase.fromId(storedPhaseId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown phase ID: " + storedPhaseId));
            return new PlayerProfile(
                    UUID.fromString(uuidValue),
                    majorRealm,
                    phase,
                    result.getLong("cultivation_exp"),
                    ProfileSetCodec.decode(result.getString("completed_requirements")),
                    ProfileSetCodec.decode(result.getString("unlocked_content")),
                    Instant.ofEpochMilli(result.getLong("created_at")),
                    Instant.ofEpochMilli(result.getLong("updated_at")),
                    readCharacter(result)
            );
        } catch (SQLException | IllegalArgumentException exception) {
            String message = "Invalid stored player profile: uuid=" + uuidValue
                    + ", major_realm_id=" + majorRealmId + ", phase_id=" + phaseId;
            logger.log(Level.SEVERE, message, exception);
            throw new ProfileDataException(message, exception);
        }
    }

    @Override
    public Optional<PlayerContentState> findContentState(UUID uuid) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT p.uuid, c.organization_id, c.organization_rank_id, COALESCE(c.sect_contribution, 0) AS sect_contribution
                FROM player_profiles p LEFT JOIN player_content_states c ON c.uuid = p.uuid WHERE p.uuid = ?
                """)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new PlayerContentState(uuid,
                        Optional.ofNullable(result.getString("organization_id")),
                        Optional.ofNullable(result.getString("organization_rank_id")),
                        result.getLong("sect_contribution"), learnedSkillIds(connection, uuid)));
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load content state for " + uuid, exception);
        }
    }

    @Override
    public void saveContentState(PlayerContentState state) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO player_content_states(uuid, organization_id, organization_rank_id, sect_contribution)
                        VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET organization_id=excluded.organization_id,
                        organization_rank_id=excluded.organization_rank_id, sect_contribution=excluded.sect_contribution
                        """)) {
                    statement.setString(1, state.uuid().toString()); statement.setString(2, state.organizationId().orElse(null));
                    statement.setString(3, state.organizationRankId().orElse(null)); statement.setLong(4, state.organizationContribution());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_learned_skills WHERE uuid=?")) {
                    statement.setString(1, state.uuid().toString()); statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO player_learned_skills(uuid, skill_id) VALUES (?, ?)")) {
                    for (String id : state.learnedSkillIds()) { statement.setString(1, state.uuid().toString()); statement.setString(2, id); statement.addBatch(); }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Failed to save content state for " + state.uuid(), exception); }
    }

    @Override
    public Optional<PlayerJourneyState> findJourneyState(UUID uuid) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT active_activity_id, dummy_hits, meditation_seconds, active_skill_id, current_qi, completed_activities
                FROM player_journey_states WHERE uuid=?
                """)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new PlayerJourneyState(uuid, Optional.ofNullable(result.getString("active_activity_id")),
                        result.getInt("dummy_hits"), result.getInt("meditation_seconds"),
                        Optional.ofNullable(result.getString("active_skill_id")), Math.max(0, result.getLong("current_qi")),
                        ProfileSetCodec.decode(result.getString("completed_activities"))));
            }
        } catch (SQLException | IllegalArgumentException exception) { throw new IllegalStateException("Failed to load journey state for " + uuid, exception); }
    }

    @Override
    public void saveJourneyState(PlayerJourneyState state) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_journey_states(uuid, active_activity_id, dummy_hits, meditation_seconds, active_skill_id, current_qi, completed_activities)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET active_activity_id=excluded.active_activity_id,
                dummy_hits=excluded.dummy_hits, meditation_seconds=excluded.meditation_seconds, active_skill_id=excluded.active_skill_id,
                current_qi=excluded.current_qi, completed_activities=excluded.completed_activities
                """)) {
            statement.setString(1, state.uuid().toString()); statement.setString(2, state.activeActivityId().orElse(null));
            statement.setInt(3, state.dummyHits()); statement.setInt(4, state.meditationSeconds());
            statement.setString(5, state.activeSkillId().orElse(null)); statement.setLong(6, state.currentQi());
            statement.setString(7, ProfileSetCodec.encode(state.completedActivityIds())); statement.executeUpdate();
        } catch (SQLException exception) { throw new IllegalStateException("Failed to save journey state for " + state.uuid(), exception); }
    }

    private Set<String> learnedSkillIds(Connection connection, UUID uuid) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT skill_id FROM player_learned_skills WHERE uuid=? ORDER BY skill_id")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
        }
        return Set.copyOf(result);
    }

    @Override public int countProfiles() { return count("SELECT COUNT(*) FROM player_profiles"); }
    @Override public int countCompletedWithConstitution(String constitutionId) { return count("SELECT COUNT(*) FROM player_profiles WHERE character_completed = 1 AND constitution_id = ?", constitutionId); }
    private int count(String sql, String... values) {
        try (Connection c = openConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i=0;i<values.length;i++) s.setString(i+1, values[i]);
            try (ResultSet r=s.executeQuery()) { return r.next() ? r.getInt(1) : 0; }
        } catch(SQLException e) { throw new IllegalStateException("Failed to count profiles", e); }
    }

    private void bindCharacter(PreparedStatement statement, int start, CharacterData data) throws SQLException {
        statement.setInt(start, data.completed() ? 1 : 0); statement.setInt(start + 1, data.rerollsUsed());
        statement.setString(start + 2, data.family() == null ? null : data.family().id()); statement.setInt(start + 3, data.baseStrength());
        statement.setInt(start + 4, data.baseSpeed()); statement.setDouble(start + 5, data.talentMultiplier());
        statement.setString(start + 6, data.constitution() == null ? null : data.constitution().id());
        CharacterDraft draft = data.draft(); statement.setString(start + 7, draft == null ? "" : ProfileSetCodec.encode(draft.offeredFamilies().stream().map(Family::id).collect(java.util.stream.Collectors.toSet())));
        statement.setInt(start + 8, draft == null ? 0 : draft.baseStrength()); statement.setInt(start + 9, draft == null ? 0 : draft.baseSpeed());
        statement.setDouble(start + 10, draft == null ? 0 : draft.talentMultiplier()); statement.setString(start + 11, draft == null ? null : draft.constitution().id());
        statement.setString(start + 12, draft == null ? null : draft.selectedFamily().id());
        statement.setString(start + 13, draft == null ? "" : ProfileSetCodec.encode(draft.lockedTraits().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet())));
    }
    private CharacterData readCharacter(ResultSet r) throws SQLException {
        boolean completed = r.getInt("character_completed") != 0; int rerolls = r.getInt("rerolls_used");
        if (completed) return new CharacterData(true, rerolls, Family.fromId(r.getString("family_id")), r.getInt("base_strength"), r.getInt("base_speed"), r.getDouble("talent_multiplier"), Constitution.fromId(r.getString("constitution_id")), null);
        String constitution = r.getString("draft_constitution_id");
        if (constitution == null) return new CharacterData(false, rerolls, null, 0, 0, 0, null, null);
        java.util.Set<Family> offers = ProfileSetCodec.decode(r.getString("draft_families")).stream().map(Family::fromId).collect(java.util.stream.Collectors.toSet());
        Family selected = r.getString("draft_selected_family_id") == null ? Family.STREET : Family.fromId(r.getString("draft_selected_family_id"));
        java.util.Set<CharacterTrait> locks = ProfileSetCodec.decode(r.getString("draft_locked_traits")).stream().map(CharacterTrait::valueOf).collect(java.util.stream.Collectors.toSet());
        return new CharacterData(false, rerolls, null, 0, 0, 0, null, new CharacterDraft(offers, selected, r.getInt("draft_strength"), r.getInt("draft_speed"), r.getDouble("draft_talent"), Constitution.fromId(constitution), locks));
    }
}
