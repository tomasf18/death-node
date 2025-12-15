package com.deathnode.client.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.deathnode.client.config.Config;

/**
 * Initializes the SQLite database for a node using the schema from client_schema.sql
 * Handles both initial setup and complete reset/reinitialize operations.
 */
public class DatabaseInitializer {

    public static void initializeDatabase() throws IOException, SQLException {
        String dbPath = Config.getSqliteDb();
        Path dbFile = Paths.get(dbPath);

        // Create parent directories if needed
        Files.createDirectories(dbFile.getParent());

        // If database file exists, delete it for a clean slate
        if (Files.exists(dbFile)) {
            Files.delete(dbFile);
        }

        // Read the schema SQL
        String schema = readSchema();

        // Create connection and initialize
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON");

            // Execute schema statements
            // Split by semicolon to handle multiple statements
            String[] statements = schema.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }

            conn.commit();
        }
    }

    public static void resetDatabase() throws IOException, SQLException {
        String dbPath = Config.getSqliteDb();
        Path dbFile = Paths.get(dbPath);

        // Create connection and drop all tables
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Disable foreign keys temporarily
            stmt.execute("PRAGMA foreign_keys = OFF");

            // Drop all tables
            stmt.execute("DROP TABLE IF EXISTS reports");
            stmt.execute("DROP TABLE IF EXISTS nodes_state");
            stmt.execute("DROP TABLE IF EXISTS nodes");
            stmt.execute("DROP TABLE IF EXISTS block_state");

            // Re-enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON");

            conn.commit();
        }

        // Now reinitialize with schema
        String schema = readSchema();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            String[] statements = schema.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("DROP")) {
                    stmt.execute(trimmed);
                }
            }

            conn.commit();
        }
    }

    /**
     * Read the schema SQL from the schema file.
     */
    private static String readSchema() throws IOException {
        Path schemaPath = Paths.get(Config.SCHEMA_SQL);

        if (!Files.exists(schemaPath)) {
            throw new IOException("Schema file not found at: " + schemaPath.toAbsolutePath());
        }

        return Files.readString(schemaPath, StandardCharsets.UTF_8);
    }
}
