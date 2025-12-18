package com.deathnode.client.service;

import com.deathnode.client.config.Config;
import com.deathnode.client.grpc.PersistentSyncClient;
import com.deathnode.tool.util.KeyLoader;
import com.deathnode.common.model.*;
import com.deathnode.tool.SecureDocumentProtocol;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main client service for report management and synchronization.
 */
public class ClientService {
    
    private final DatabaseService db;
    private final PersistentSyncClient syncClient;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final AtomicLong lastNodeSequenceNumber;

    public ClientService(DatabaseService db) {
        this.db = db;
        this.syncClient = new PersistentSyncClient(db, Config.SERVER_HOST, Config.SERVER_PORT);
        try {
            this.lastNodeSequenceNumber = new AtomicLong(db.getLastSequenceNumber(Config.getNodeSelfId()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize lastNodeSequenceNumber", e);
        }
        
        // connect to server immediately (without starting sync)
        syncClient.connect();
        startPendingReportsMonitor();
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
     * Create a report using an already existing list of templates.
     */
    public void createRandomReport() throws Exception {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.print("Reports to create: ");
        int n = sc.nextInt();

        record Report(String suspect, String description, String location) {};
        Type listType = new TypeToken<List<Report>>(){}.getType();

        List<Report> reports;
        try (FileReader reader = new FileReader("reports.json")) {
            reports = gson.fromJson(reader, listType);
        }

        Report r;
        Random rd = new Random();
        for (int i = 0; i < n; i++) {
            r = reports.get(rd.nextInt(reports.size()));
            createReport(r.suspect, r.description, r.location);
        }

    }

    /**
     * Create a report programmatically.
     * 
     * @return Envelope hash of the created report
     */
    public String createReport(String suspect, String description, String location) throws Exception {
        System.out.println("Creating report: suspect=" + suspect + ", location=" + location);

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
        report.setReporterPseudonym(Config.getNodePseudonym());
        report.setContent(content);
        report.setVersion(1);
        report.setStatus("pending_validation");

        // 2. Prepare metadata
        long nextSeq = this.lastNodeSequenceNumber.incrementAndGet();

        String prevHash = db.getLastEnvelopeHash(Config.getNodeSelfId());
        if (prevHash == null) prevHash = "";

        Metadata metadata = new Metadata();
        metadata.setReportId(reportId);
        metadata.setMetadataTimestamp(Instant.now().toString());
        metadata.setReportCreationTimestamp(timestamp);
        metadata.setNodeSequenceNumber(nextSeq);
        metadata.setPrevEnvelopeHash(prevHash);
        metadata.setSignerNodeId(Config.getNodeSelfId());
        metadata.setSignerAlg(Config.SIGNING_KEYS_ALG);

        // 3. Load signing key (Ed25519)
        PrivateKey signerPriv = KeyLoader.loadPrivateKeyFromKeystore(Config.ED_PRIVATE_KEY_ALIAS, Config.getKeystorePath(), Config.KEYSTORE_PASSWORD);
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
        Path outDir = Paths.get(Config.getEnvelopesDir());
        Files.createDirectories(outDir);
        Path written = envelope.writeSelf(outDir);
        String envelopeHash = envelope.computeHashHex();

        System.out.println("Created envelope: " + written.getFileName() + " (seq=" + nextSeq + ")");

        // 7. Save to DB
        db.insertReport(envelopeHash, written.toString(), Config.getNodeSelfId(), nextSeq, null, metadata.getMetadataTimestamp(), prevHash);
        db.upsertNodeState(Config.getNodeSelfId(), nextSeq, envelopeHash);

        // 8. Add to pending buffer
        syncClient.addPendingEnvelope(written.toString());

        // 9. Auto-sync if threshold reached
        if (syncClient.getPendingCount() >= Config.BUFFER_THRESHOLD_TO_SYNC) {
            System.out.println("Buffer threshold reached (" + Config.BUFFER_THRESHOLD_TO_SYNC + ") - triggering sync");
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

        System.out.println("Triggering sync of " + pending + " pending reports");
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
        PrivateKey rsaPriv = KeyLoader.loadPrivateKeyFromKeystore(Config.RSA_PRIVATE_KEY_ALIAS, Config.getKeystorePath(), Config.KEYSTORE_PASSWORD);

        System.out.printf("%-66s %-13s %-13s %-13s %-32s %-66s%n",
                "envelope_hash", "signer", "local_seq", "global_seq", "meta_timestamp", "prev_hash");
        System.out.println("-".repeat(200));

        for (DatabaseService.ReportRow row : rows) {
            System.out.printf("%-66s %-13s %-13d %-13s %-32s %-66s%n",
                    row.envelopeHash,
                    row.signerNodeId,
                    row.nodeSequenceNumber,
                    row.globalSequenceNumber == 0 ? "unsynced" : row.globalSequenceNumber,
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

                // Verify envelope hash
                String envelopeHash = envelope.computeHashHex();
                if (!envelopeHash.equals(row.envelopeHash)) {
                    System.out.println("  [ERROR] Envelope hash mismatch: computed=" + envelopeHash + ", expected=" + row.envelopeHash);
                    continue;
                }

                // Load sender public key
                String senderId = envelope.getMetadata().getSignerNodeId();
                String senderKeyB64 = db.getSignPubKey(senderId);
                if (senderKeyB64 == null) {
                    System.out.println("  [ERROR] Sender public key not found");
                    continue;
                }

                PublicKey senderPub = KeyLoader.pemStringToPublicKey(senderKeyB64, Config.SIGNING_KEYS_ALG);

                // Decrypt and verify
                Report decrypted = SecureDocumentProtocol.unprotect(envelope, rsaPriv, Config.getNodeSelfId(), senderPub);

                System.out.println("  [OK] Report: " + decrypted.getReportId());
                System.out.println("       Pseudonym: " + decrypted.getReporterPseudonym());
                System.out.println("       Suspect: " + decrypted.getContent().getSuspect());
                System.out.println("       Location: " + decrypted.getContent().getLocation());
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
                PublicKey pk = KeyLoader.pemStringToPublicKey(keyB64, Config.ENCRYPTION_KEYS_ALG);
                recipients.put(nodeId, pk);
            } catch (Exception e) {
                System.err.println("Failed to parse enc_pub_key for node " + nodeId + ": " + e.getMessage());
            }
        }

        return recipients;
    }

    public int getPendingReportsCount() {
        return syncClient.getPendingCount();
    }

    public void startPendingReportsMonitor() {
        syncClient.startPendingReportsMonitor();
    }

    public void stopPendingReportsMonitor() {
        syncClient.stopPendingReportsMonitor();
    }

    public void resetDatabase() throws Exception {
        db.resetDatabase();
        System.out.println("Local database reset complete.");
    }
}