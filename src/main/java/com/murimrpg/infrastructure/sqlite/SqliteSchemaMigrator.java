package com.murimrpg.infrastructure.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteSchemaMigrator {
    public static final int LATEST_VERSION = 1;

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
