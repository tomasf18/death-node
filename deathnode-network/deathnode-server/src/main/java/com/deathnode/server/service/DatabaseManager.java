package com.deathnode.server.service;

import com.deathnode.server.entity.Node;
import com.deathnode.server.entity.NodeSyncState;
import com.deathnode.server.repository.NodeRepository;
import com.deathnode.server.repository.NodeSyncStateRepository;
import com.deathnode.server.repository.ReportRepository;
import com.deathnode.server.repository.SignedBlockMerkleRootRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages database operations including reinitialization.
 */
@Service
public class DatabaseManager {

    private final NodeRepository nodeRepository;
    private final NodeSyncStateRepository nodeSyncStateRepository;
    private final ReportRepository reportRepository;
    private final SignedBlockMerkleRootRepository signedBlockMerkleRootRepository;
    private final FileStorageService fileStorageService;
    private final Path envelopesPath;

    public DatabaseManager(NodeRepository nodeRepository,
                          NodeSyncStateRepository nodeSyncStateRepository,
                          ReportRepository reportRepository,
                          SignedBlockMerkleRootRepository signedBlockMerkleRootRepository,
                          FileStorageService fileStorageService,
                          @Value("${storage.envelopes-path}") String envelopesDir) {
        this.nodeRepository = nodeRepository;
        this.nodeSyncStateRepository = nodeSyncStateRepository;
        this.reportRepository = reportRepository;
        this.signedBlockMerkleRootRepository = signedBlockMerkleRootRepository;
        this.fileStorageService = fileStorageService;
        this.envelopesPath = Path.of(envelopesDir);
    }

    /**
     * Reinitialize the database:
     * 1. Clear all reports
     * 2. Reset nodes_sync_state (keep node_id but null out sequence/hash)
     * 3. Clear signed_block_merkle_roots
     * 4. Delete all envelope files
     */
    @Transactional
    public void reinitializeDatabase() throws IOException {
        System.out.println("Starting database reinitialization...");

        System.out.println("Clearing reports table...");
        reportRepository.deleteAll();

        System.out.println("Deleting nodes_sync_state...");
        nodeSyncStateRepository.deleteAll();

        System.out.println("Clearing signed_block_merkle_roots table...");
        signedBlockMerkleRootRepository.deleteAll();

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
