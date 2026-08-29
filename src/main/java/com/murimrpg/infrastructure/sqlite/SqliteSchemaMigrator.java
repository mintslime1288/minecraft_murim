package com.murimrpg.infrastructure.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteSchemaMigrator {
    /** Version 5 adds initial-character columns after the deployed content/journey schema (v2-v4). */
    public static final int LATEST_VERSION = 6;

    public void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            createVersionTable(connection);
            int currentVersion = schemaVersion(connection);
            if (currentVersion > LATEST_VERSION) {
                throw new SQLException(
                        "Database schema version " + currentVersion
                                + " is newer than supported version " + LATEST_VERSION
                );
            }
            if (currentVersion < 1) {
                migrateToVersionOne(connection);
                updateVersion(connection, 1);
            }
            if (currentVersion < 2) { migrateToVersionTwo(connection); updateVersion(connection, 2); }
            if (currentVersion < 3) { migrateToVersionThree(connection); updateVersion(connection, 3); }
            if (currentVersion < 4) { migrateToVersionFour(connection); updateVersion(connection, 4); }
            if (currentVersion < 5) { migrateToVersionFive(connection); updateVersion(connection, 5); }
            if (currentVersion < 6) { migrateToVersionSix(connection); updateVersion(connection, 6); }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public int schemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM schema_version WHERE singleton = 1"
        ); ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("schema_version row is missing");
            }
            return result.getInt("version");
        }
    }

    private void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                        version INTEGER NOT NULL
                    )
                    """);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_version(singleton, version)
                VALUES (1, 0)
                ON CONFLICT(singleton) DO NOTHING
                """)) {
            statement.executeUpdate();
        }
    }

    private void migrateToVersionOne(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_profiles (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        major_realm_id TEXT NOT NULL,
                        phase_id TEXT NOT NULL,
                        cultivation_exp INTEGER NOT NULL CHECK (cultivation_exp >= 0),
                        completed_requirements TEXT NOT NULL,
                        unlocked_content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private void migrateToVersionTwo(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_content_states (
                    uuid TEXT PRIMARY KEY NOT NULL, sect_id TEXT,
                    sect_contribution INTEGER NOT NULL DEFAULT 0 CHECK (sect_contribution >= 0),
                    FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)
                    """);
            statement.execute("""
                    CREATE TABLE player_learned_skills (
                    uuid TEXT NOT NULL, skill_id TEXT NOT NULL, PRIMARY KEY (uuid, skill_id),
                    FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)
                    """);
        }
    }

    private void migrateToVersionThree(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE player_content_states ADD COLUMN organization_id TEXT");
            statement.execute("ALTER TABLE player_content_states ADD COLUMN organization_rank_id TEXT");
        }
    }

    private void migrateToVersionFour(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_journey_states (
                    uuid TEXT PRIMARY KEY NOT NULL, active_activity_id TEXT,
                    dummy_hits INTEGER NOT NULL DEFAULT 0 CHECK (dummy_hits >= 0),
                    meditation_seconds INTEGER NOT NULL DEFAULT 0 CHECK (meditation_seconds >= 0),
                    active_skill_id TEXT, current_qi INTEGER NOT NULL DEFAULT 0 CHECK (current_qi >= 0),
                    completed_activities TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)
                    """);
        }
    }

    private void migrateToVersionFive(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE player_profiles ADD COLUMN character_completed INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN rerolls_used INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN family_id TEXT");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN base_strength INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN base_speed INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN talent_multiplier REAL NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN constitution_id TEXT");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_families TEXT NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_strength INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_speed INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_talent REAL NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_constitution_id TEXT");
        }
    }

    private void migrateToVersionSix(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_selected_family_id TEXT");
            statement.execute("ALTER TABLE player_profiles ADD COLUMN draft_locked_traits TEXT NOT NULL DEFAULT ''");
        }
    }

    private void updateVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE schema_version SET version = ? WHERE singleton = 1"
        )) {
            statement.setInt(1, version);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Failed to update schema version to " + version);
            }
        }
    }
}
