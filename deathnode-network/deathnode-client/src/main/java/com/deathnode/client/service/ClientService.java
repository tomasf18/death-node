package com.deathnode.client.service;

import com.deathnode.client.config.Config;
import com.deathnode.client.grpc.PersistentSyncClient;
import com.deathnode.client.utils.KeyUtils;
import com.deathnode.common.model.*;
import com.deathnode.common.util.HashUtils;
import com.deathnode.tool.SecureDocumentProtocol;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

/**
 * Main client service for report management and synchronization.
 */
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);
    
    private final DatabaseService db;
    private final PersistentSyncClient syncClient;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public ClientService(DatabaseService db) {
        this.db = db;
        this.syncClient = new PersistentSyncClient(db, Config.SERVER_HOST, Config.SERVER_PORT);
        
        // Connect to server immediately (without starting sync)
        syncClient.connect();
    }

    /**
     * Create a report interactively (CLI).
     */
    public String createReportInteractive(Scanner scanner) throws Exception {
        System.out.print("Suspect: ");
        String suspect = scanner.nextLine().trim();

        System.out.print("Description: ");
        String description = scanner.nextLine().trim();

        System.out.print("Location: ");
        String location = scanner.nextLine().trim();

        return createReport(suspect, description, location);
    }

    /**
     * Create a report programmatically.
     * 
     * @return Envelope hash of the created report
     */
    public String createReport(String suspect, String description, String location) throws Exception {
        log.info("Creating report: suspect={}, location={}", suspect, location);

        // 1. Build Report object
        String reportId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        Report.Content content = new Report.Content();
        content.setSuspect(suspect);
        content.setDescription(description);
        content.setLocation(location);

        Report report = new Report();
        report.setReportId(reportId);
        report.setReportCreationTimestamp(timestamp);
        report.setReporterPseudonym(Config.NODE_PSEUDONYM);
        report.setContent(content);
        report.setVersion(1);
        report.setStatus("pending_validation");

        // 2. Prepare metadata
        long lastSeq = db.getLastSequenceNumber(Config.NODE_SELF_ID);
        long nextSeq = lastSeq + 1;
        String prevHash = db.getLastEnvelopeHash(Config.NODE_SELF_ID);
        if (prevHash == null) prevHash = "";

        Metadata metadata = new Metadata();
        metadata.setReportId(reportId);
        metadata.setMetadataTimestamp(Instant.now().toString());
        metadata.setReportCreationTimestamp(timestamp);
        metadata.setNodeSequenceNumber(nextSeq);
        metadata.setPrevEnvelopeHash(prevHash);
        metadata.setSignerNodeId(Config.NODE_SELF_ID);
        metadata.setSignerAlg("Ed25519");

        // 3. Load signing key (Ed25519)
        PrivateKey signerPriv = KeyUtils.loadPrivateKeyFromKeystore(Config.ED_PRIVATE_KEY_ALIAS);
        if (signerPriv == null) {
            throw new IllegalStateException("Signer private key not found");
        }

        // 4. Load all recipient public keys (RSA)
        Map<String, PublicKey> recipients = loadRecipientKeys();
        if (recipients.isEmpty()) {
            throw new IllegalStateException("No recipient public keys found");
        }

        // 5. Protect the report
        Envelope envelope = SecureDocumentProtocol.protect(report, metadata, recipients, signerPriv);

        // 6. Save envelope to disk
        Path outDir = Paths.get(Config.ENVELOPES_DIR);
        Files.createDirectories(outDir);
        Path written = envelope.writeSelf(outDir);
        String envelopeHash = envelope.computeHashHex();

        log.info("Created envelope: {} (seq={})", written.getFileName(), nextSeq);

        // 7. Save to DB
        db.insertReport(envelopeHash, written.toString(), Config.NODE_SELF_ID, nextSeq, prevHash);
        db.upsertNodeState(Config.NODE_SELF_ID, nextSeq, envelopeHash);

        // 8. Add to pending buffer
        syncClient.addPendingEnvelope(written.toString());

        // 9. Auto-sync if threshold reached
        if (syncClient.getPendingCount() >= Config.BUFFER_THRESHOLD_TO_SYNC) {
            log.info("Buffer threshold reached ({}) - triggering sync", Config.BUFFER_THRESHOLD_TO_SYNC);
            syncReports();
        }

        return envelopeHash;
    }

    /**
     * Manually trigger synchronization of buffered reports.
     */
    public void syncReports() throws Exception {
        int pending = syncClient.getPendingCount();
        if (pending == 0) {
            System.out.println("No pending reports to sync");
            return;
        }

        log.info("Triggering sync of {} pending reports", pending);
        System.out.println("Initiating sync round with " + pending + " pending reports...");
        
        syncClient.triggerSync();
    }
    
    public void shutdown() {
        syncClient.shutdown();
    }

    /**
     * List all reports in local DB.
     */
    public void listReports() throws Exception {
        List<DatabaseService.ReportRow> rows = db.listReports();
        if (rows.isEmpty()) {
            System.out.println("No reports found locally");
            return;
        }

        // Load RSA private key for decryption
        PrivateKey rsaPriv = KeyUtils.loadPrivateKeyFromKeystore(Config.RSA_PRIVATE_KEY_ALIAS);

        System.out.printf("%-66s %-15s %-6s %-26s %-66s%n",
                "envelope_hash", "signer", "seq", "meta_timestamp", "prev_hash");
        System.out.println("-".repeat(200));

        for (DatabaseService.ReportRow row : rows) {
            System.out.printf("%-66s %-15s %-6d %-26s %-66s%n",
                    row.envelopeHash,
                    row.signerNodeId,
                    row.nodeSequenceNumber,
                    row.metadataTimestamp,
                    row.prevEnvelopeHash == null ? "" : row.prevEnvelopeHash);

            // Try to read and verify envelope
            Path envPath = Paths.get(row.filePath);
            if (!Files.exists(envPath)) {
                System.out.println("  [ERROR] File not found: " + row.filePath);
                continue;
            }

            try {
                // Read and parse envelope
                String json = Files.readString(envPath, StandardCharsets.UTF_8);
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                Envelope envelope = Envelope.fromJson(jsonObj);

                // Structural check
                Map<String, Object> checkResult = SecureDocumentProtocol.check(envelope);
                boolean valid = (boolean) checkResult.getOrDefault("valid", false);

                if (!valid) {
                    @SuppressWarnings("unchecked")
                    List<String> reasons = (List<String>) checkResult.get("reasons");
                    System.out.println("  [INVALID] " + String.join(", ", reasons));
                    continue;
                }

                // Load sender public key
                String senderId = envelope.getMetadata().getSignerNodeId();
                String senderKeyB64 = db.getSignPubKey(senderId);
                if (senderKeyB64 == null) {
                    System.out.println("  [ERROR] Sender public key not found");
                    continue;
                }

                PublicKey senderPub = KeyUtils.publicKeyFromBase64(senderKeyB64, "Ed25519");

                // Decrypt and verify
                Report decrypted = SecureDocumentProtocol.unprotect(
                        envelope, rsaPriv, Config.NODE_SELF_ID, senderPub);

                System.out.println("  [OK] Report: " + decrypted.getReportId());
                System.out.println("       Pseudonym: " + decrypted.getReporterPseudonym());
                System.out.println("       Suspect: " + decrypted.getContent().getSuspect());
                System.out.println("       Description: " + decrypted.getContent().getDescription());

            } catch (Exception e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }

    // ========== Helper Methods ==========

    private Map<String, PublicKey> loadRecipientKeys() throws Exception {
        Map<String, String> allKeys = db.getAllEncPubKeys();
        Map<String, PublicKey> recipients = new HashMap<>();

        for (Map.Entry<String, String> entry : allKeys.entrySet()) {
            String nodeId = entry.getKey();
            String keyB64 = entry.getValue();

            if (keyB64 == null || keyB64.trim().isEmpty()) {
                continue;
            }

            try {
                PublicKey pk = KeyUtils.publicKeyFromBase64(keyB64, "RSA");
                recipients.put(nodeId, pk);
            } catch (Exception e) {
                log.warn("Failed to parse enc_pub_key for node {}: {}", nodeId, e.getMessage());
            }
        }

        return recipients;
    }

    public int getPendingReportsCount() {
        return syncClient.getPendingCount();
    }
}