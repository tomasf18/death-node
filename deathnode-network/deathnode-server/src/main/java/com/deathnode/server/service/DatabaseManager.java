package com.deathnode.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages database operations including reinitialization.
 */
@Service
public class DatabaseManager {

    private final Path envelopesPath;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseManager(JdbcTemplate jdbcTemplate,
                          @Value("${storage.envelopes-path}") String envelopesDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.envelopesPath = Path.of(envelopesDir);
    }

    /**
     * Reinitialize the database by running the server_schema.sql script:
     * 1. Drop and recreate tables
     * 2. Reinsert initial node data
     * 3. Delete all envelope files
     */
    @Transactional
    public void reinitializeDatabase() throws IOException {
        System.out.println("Starting database reinitialization...");

        try {
            System.out.println("Dropping tables...");
            jdbcTemplate.execute("DROP TABLE IF EXISTS signed_block_merkle_roots");
            jdbcTemplate.execute("DROP TABLE IF EXISTS reports");
            jdbcTemplate.execute("DROP TABLE IF EXISTS nodes_sync_state");

            System.out.println("Creating nodes_sync_state table...");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS nodes_sync_state (\n" +
                    "    node_id                 VARCHAR(255)                PRIMARY KEY,\n" +
                    "    last_sequence_number    BIGINT,\n" +
                    "    last_envelope_hash      VARCHAR(64),\n" +
                    "    FOREIGN KEY (node_id) REFERENCES nodes(node_id)\n" +
                    ")");

            System.out.println("Creating reports table...");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reports (\n" +
                    "    envelope_hash           VARCHAR(64)                 PRIMARY KEY,\n" +
                    "    signer_node_id          VARCHAR(255)                NOT NULL,\n" +
                    "    node_sequence_number    BIGINT                      NOT NULL,\n" +
                    "    global_sequence_number  BIGINT,\n" +
                    "    metadata_timestamp      TIMESTAMP WITH TIME ZONE    NOT NULL,\n" +
                    "    prev_report_hash        VARCHAR(64),\n" +
                    "    file_path               TEXT                        NOT NULL,\n" +
                    "    UNIQUE (signer_node_id, node_sequence_number),\n" +
                    "    FOREIGN KEY (signer_node_id) REFERENCES nodes(node_id)\n" +
                    ")");

            System.out.println("Creating signed_block_merkle_roots table...");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS signed_block_merkle_roots (\n" +
                    "    block_id                    BIGSERIAL                   PRIMARY KEY,\n" +
                    "    block_number                BIGINT                      NOT NULL        UNIQUE,\n" +
                    "    block_root                  VARCHAR(64)                 NOT NULL        UNIQUE,\n" +
                    "    prev_block_root             VARCHAR(64)\n" +
                    ")");

            System.out.println("Inserting initial nodes_sync_state...");
            jdbcTemplate.update("INSERT INTO nodes_sync_state(node_id, last_sequence_number, last_envelope_hash) VALUES (?, ?, ?)",
                    "nodeA", null, null);
            jdbcTemplate.update("INSERT INTO nodes_sync_state(node_id, last_sequence_number, last_envelope_hash) VALUES (?, ?, ?)",
                    "nodeB", null, null);

        } catch (Exception e) {
            System.err.println("Error during database reinitialization: " + e.getMessage());
            throw new RuntimeException("Failed to reinitialize database", e);
        }

        System.out.println("Deleting envelope files from " + envelopesPath + "...");
        deleteAllEnvelopes();

        System.out.println("Database reinitialization completed successfully");
    }

    /**
     * Delete all envelope files from node-specific directories.
     * Removes all directories matching pattern "*_envelopes" under the base path.
     */
    private void deleteAllEnvelopes() throws IOException {
        if (!Files.exists(envelopesPath)) {
            System.out.println("Envelopes directory does not exist: " + envelopesPath);
            return;
        }

        try (var stream = Files.list(envelopesPath)) {
            stream.forEach(path -> {
                try {
                    // Delete directories matching *_envelopes pattern
                    if (Files.isDirectory(path) && path.getFileName().toString().endsWith("_envelopes")) {
                        deleteDirectory(path);
                        System.out.println("Deleted envelope directory: " + path.getFileName());
                    } else if (Files.isRegularFile(path)) {
                        // Also clean up any stray files in base directory
                        Files.delete(path);
                        System.out.println("Deleted envelope file: " + path.getFileName());
                    }
                } catch (IOException e) {
                    System.err.println("Failed to delete " + path.getFileName() + ": " + e.getMessage());
                }
            });
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete " + path + ": " + e.getMessage());
                        }
                    });
        }
    }
}
