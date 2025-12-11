package com.deathnode.client;

import com.deathnode.tool.SecureDocumentProtocol;
import com.deathnode.tool.entity.Envelope;
import com.deathnode.tool.entity.Metadata;
import com.deathnode.tool.entity.Report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import java.time.Instant;

import java.util.*;

public class ClientService {

    private final LocalDb db;
    private final Gson gson;
    private final List<String> pendingReports;
    private static final int BUFFER_SIZE = 4;

    public ClientService(LocalDb db) {
        this.db = db;
        this.gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        this.pendingReports = new ArrayList<>(BUFFER_SIZE);
    }

    /**
     * Create a report interactively, protect it with the SecureDocumentProtocol, persist file and DB.
     */
    public String createReportInteractive() throws Exception {
        if (pendingReports.size() >= BUFFER_SIZE ) {
            System.out.println("Cannot create report, because the buffer is full");
            System.out.println("Synchronize with the server now...");
            syncReports();
        }
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.print("Suspect: ");
        String suspect = sc.nextLine().trim();

        System.out.print("Description: ");
        String description = sc.nextLine().trim();

        System.out.print("Location: ");
        String location = sc.nextLine().trim();

        // -------------------------------
        // 1) Build Report object
        // -------------------------------
        String reportId = UUID.randomUUID().toString();
        String reportCreationTimestamp = Instant.now().toString();
        String reporterPseudonym = Config.NODE_PSEUDONYM;

        Report.Content content = new Report.Content();
        content.setSuspect(suspect);
        content.setDescription(description);
        content.setLocation(location);

        Report report = new Report();
        report.setReportId(reportId);
        report.setReportCreationTimestamp(reportCreationTimestamp);
        report.setReporterPseudonym(reporterPseudonym);
        report.setContent(content);
        report.setVersion(1);
        report.setStatus("pending_validation");

        // -------------------------------
        // 2) Prepare metadata (sequence, prev hash)
        // -------------------------------
        long lastSeq = db.getLastSequenceNumber(Config.NODE_SELF_ID);
        long nextSeq = lastSeq + 1;

        // prev hash stored as hex string envelopeHash (or empty string)
        String prevHashHex = db.getLastEnvelopeHash(Config.NODE_SELF_ID);
        if (prevHashHex == null) prevHashHex = "";

        Metadata metadata = new Metadata();
        metadata.setReportId(reportId);
        metadata.setMetadataTimestamp(Instant.now().toString());
        metadata.setReportCreationTimestamp(reportCreationTimestamp);
        metadata.setNodeSequenceNumber(nextSeq);
        metadata.setPrevEnvelopeHash(prevHashHex);
        metadata.setSignerNodeId(Config.NODE_SELF_ID);
        metadata.setSignerAlg("Ed25519");

        // -------------------------------
        // 3) Load signer private key from keystore (Ed25519)
        // -------------------------------
        PrivateKey signerPriv = loadPrivateKeyFromKeystore(Config.ED_PRIVATE_KEY_ALIAS);
        if (signerPriv == null) {
            throw new IllegalStateException("Signer private key not found in keystore with alias: " + Config.ED_PRIVATE_KEY_ALIAS);
        }

        // -------------------------------
        // 4) Build recipients map (nodeId -> RSA public key)
        // -------------------------------
        Map<String, String> allB64 = db.getAllEncPubKeys(); // Map<nodeId, base64Der>
        if (allB64 == null || allB64.isEmpty()) {
            throw new IllegalStateException("No nodes found in local DB 'nodes' table (enc_pub_key).");
        }

        Map<String, PublicKey> recipients = new HashMap<>();
        for (Map.Entry<String, String> e : allB64.entrySet()) {
            String nodeId = e.getKey();
            String encB64 = e.getValue();
            if (encB64 == null || encB64.trim().isEmpty()) continue;
            PublicKey pk;
            try {
                pk = publicKeyFromBase64(encB64, "RSA");
            } catch (Exception ex) {
                System.err.println("Warning: failed to parse enc_pub_key for node " + nodeId + ": " + ex.getMessage());
                continue;
            }
            recipients.put(nodeId, pk);
        }

        if (recipients.isEmpty()) {
            throw new IllegalStateException("No valid recipient public keys found in DB.");
        }

        // -------------------------------
        // 5) Call tool protect
        // -------------------------------
        Envelope envelope;
        try {
            envelope = SecureDocumentProtocol.protect(report, metadata, recipients, signerPriv);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to protect report: " + ex.getMessage(), ex);
        }

        // -------------------------------
        // 6) Persist envelope file (deterministic hash filename)
        // -------------------------------
        Path outDir = Paths.get(Config.ENVELOPES_DIR);
        Files.createDirectories(outDir);
        Path written = envelope.writeSelf(outDir);
        String envelopeHashHex = envelope.computeHashHex();

        // -------------------------------
        // 7) Persist DB entries (reports and node_state)
        // -------------------------------
        db.insertReport(envelopeHashHex, written.toString(), Config.NODE_SELF_ID, nextSeq, prevHashHex);
        db.upsertNodeState(Config.NODE_SELF_ID, nextSeq, envelopeHashHex);

        // -------------------------------
        // 7) Buffering the Report
        // -------------------------------
        pendingReports.add(written.toString());
        if (pendingReports.size() == BUFFER_SIZE) {
            syncReports();
        }

        System.out.println("Created envelope: " + written.getFileName().toString());
        return envelopeHashHex;
    }

    /**
     * Synchronize all buffered reports with the server via POST /sync endpoint.
     * */
    public void syncReports() throws Exception {
        if (pendingReports.isEmpty()) {
            System.out.println("Buffer is empty, don't have reports to sync");
            return;
        }
        System.out.println("Synchronization " + pendingReports.size() + " reports...");

        List<JsonObject> toSend = new ArrayList<>();
        for (String path : pendingReports) {
            try {
                Path p = Paths.get(path);
                if (!Files.exists(p)) {
                    System.err.println(" [ERROR] Envelope não encontrado: " + path);
                    return; // FIXME: deixar o return ou subsituir para o continue caso um report falhe, manter consistência?
                }

                String raw = Files.readString(p, StandardCharsets.UTF_8);
                JsonObject envJson = JsonParser.parseString(raw).getAsJsonObject();
                toSend.add(envJson);

            } catch (Exception ex) {
                System.err.println("  [ERROR] Falha to proccess " + path + ": " + ex.getMessage());
            }
        }

        if (toSend.isEmpty()) {
            System.err.println("✗ No valid envelopes to send.");
            return;
        }

        // Send the envelopes to the server
        if (sendEnvelopesToServer(toSend)) {
            pendingReports.clear();
            System.out.println("✓ Synchronization conclude! " + toSend.size() + " reports sent.");
        } else {
            System.err.println("✗ Fail to synchronize");
        }
    }

    /**
     * Send array of envelopes to rhe server via POST '/sync'
     * @param envelopes array of the envelopes to send
     * @return truth If the operation was successful, false otherwise
     */
    private boolean sendEnvelopesToServer(List<JsonObject> envelopes) {
        try {
            JsonArray jsonArray = new JsonArray();
            for (JsonObject env : envelopes) {
                jsonArray.add(env);
            }

            String jsonPayload = gson.toJson(jsonArray);

            // TODO: send to the server endpoint, POST?
            return true;

        } catch (Exception ex) {
            System.err.println("  [ERROR] in sending the reports to the server: " + ex.getMessage());
            return false;
        }
    }

    /**
     * List reports stored in the client's DB.
     */
    public void listReports() throws Exception {
        List<ReportDatabaseRow> rows = db.listReports(); // see LocalDb addition below
        if (rows == null || rows.isEmpty()) {
            System.out.println("No reports found locally.");
            return;
        }

        // Load RSA private key once
        PrivateKey rsaPriv = loadPrivateKeyFromKeystore(Config.RSA_PRIVATE_KEY_ALIAS);
        if (rsaPriv == null) {
            System.err.println("Warning: RSA private key not found in keystore (alias: " + Config.RSA_PRIVATE_KEY_ALIAS + "). Decryption will fail.");
        }

        System.out.printf("%-66s %-10s %-6s %-40s %-66s %s%n",
                "envelope_hash", "signer", "seq", "meta_ts", "prev_hash", "file_path");

        for (ReportDatabaseRow r : rows) {
            System.out.printf("%-66s %-10s %-6d %-40s %-66s %s%n",
                    r.envelopeHash,
                    r.signerNodeId,
                    r.nodeSequenceNumber,
                    r.metadataTimestamp,
                    r.prevEnvelopeHash == null ? "" : r.prevEnvelopeHash,
                    r.filePath);

            // Attempt to open the envelope file
            Path envPath = Paths.get(r.filePath);
            if (!Files.exists(envPath)) {
                System.out.println("  [ERROR] envelope file missing: " + r.filePath);
                continue;
            }

            try {
                String raw = Files.readString(envPath, StandardCharsets.UTF_8);
                JsonObject envJson = JsonParser.parseString(raw).getAsJsonObject();
                Envelope envelope = Envelope.fromJson(envJson);

                // 1) Structural check (no keys required)
                Map<String, Object> checkRes = SecureDocumentProtocol.check(envelope);
                boolean structValid = (boolean) checkRes.getOrDefault("valid", false);
                @SuppressWarnings("unchecked")
                List<String> structReasons = (List<String>) checkRes.getOrDefault("reasons", Collections.emptyList());

                if (!structValid) {
                    System.out.println("  [INVALID STRUCTURE] reasons:");
                    for (String reason : structReasons) {
                        System.out.println("    - " + reason);
                    }
                    continue;
                } else {
                    System.out.println("  [STRUCTURE OK]");
                }

                // 2) Determine sender public key (from DB)
                String senderNodeId = envelope.getMetadata().getSignerNodeId();
                String senderSignB64 = db.getSignPubKey(senderNodeId);
                if (senderSignB64 == null || senderSignB64.trim().isEmpty()) {
                    System.out.println("  [ERROR] sender public key missing for node: " + senderNodeId);
                    continue;
                }
                PublicKey senderPub;
                try {
                    senderPub = publicKeyFromBase64(senderSignB64, "Ed25519");
                } catch (Exception ex) {
                    System.out.println("  [ERROR] failed to parse sender public key: " + ex.getMessage());
                    continue;
                }

                // 3) Attempt unprotect (decrypt + verify)
                try {
                    Report decrypted = SecureDocumentProtocol.unprotect(envelope, rsaPriv, Config.NODE_SELF_ID, senderPub);
                    System.out.println("  [DECRYPT+VERIFY OK] Decrypted report:");
                    // Pretty-print report JSON
                    JsonObject reportObj = decrypted.toJson();
                    String pretty = gson.toJson(reportObj);
                    // Indent lines for readability
                    Arrays.stream(pretty.split("\n")).forEach(line -> System.out.println("    " + line));
                } catch (SecurityException se) {
                    System.out.println("  [SECURITY ERROR] " + se.getMessage());
                } catch (Exception ex) {
                    System.out.println("  [ERROR] decryption/verification failed: " + ex.getMessage());
                }

            } catch (Exception ex) {
                System.out.println("  [ERROR] Failed to process envelope: " + ex.getMessage());
            }
        }
    }

    // -------------------------------
    // Helper: load private key from keystore (JKS) for given alias
    // -------------------------------
    private PrivateKey loadPrivateKeyFromKeystore(String alias) throws Exception {
        Path p = Paths.get(Config.KEYSTORE_PATH);
        if (!Files.exists(p)) {
            throw new IllegalStateException("Keystore not found at: " + Config.KEYSTORE_PATH);
        }

        String ksType = "JKS";
        KeyStore ks = KeyStore.getInstance(ksType);
        try (InputStream is = Files.newInputStream(p)) {
            ks.load(is, Config.KEYSTORE_PASSWORD.toCharArray());
        }

        Key key = ks.getKey(alias, Config.KEYSTORE_PASSWORD.toCharArray());
        if (key instanceof PrivateKey) {
            return (PrivateKey) key;
        } else {
            return null;
        }
    }

    // -------------------------------
    // Helper: convert a base64-encoded DER public key to PublicKey
    // algorithm can be "RSA" or "Ed25519" depending on key type
    // -------------------------------
    private PublicKey publicKeyFromBase64(String keyText, String algorithm) throws Exception {
        String cleaned = keyText.trim();

        if (cleaned.contains("BEGIN")) {
            cleaned = cleaned
                .replaceAll("-----BEGIN ([A-Z ]+)-----", "")
                .replaceAll("-----END ([A-Z ]+)-----", "")
                .replaceAll("\\s+", "");
        }

        byte[] der = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance(algorithm).generatePublic(spec);
    }


    // -------------------------------
    // Local helper record used to display reports
    // -------------------------------
    public static class ReportDatabaseRow {
        public String envelopeHash;
        public String signerNodeId;
        public long nodeSequenceNumber;
        public String metadataTimestamp;
        public String prevEnvelopeHash;
        public String filePath;
    }
}
