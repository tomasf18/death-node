package com.deathnode.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Scanner;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.deathnode.client.entity.*;
import com.deathnode.client.Config;

public class ClientService {
    private static final Logger logger = LogManager.getLogger(Main.class);
    private final LocalDb db;
    private final Gson gson;
    private final SecureRandom rnd = new SecureRandom();

    public ClientService(LocalDb db) {
        this.db = db;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    /**
     * Interactive report creation prompt, builds envelope with placeholders (before adding security features),
     * stores envelope file, and persists local DB entries and node state.
     *
     * signerId is the local node id (e.g. "1.1.1.1")
     */
    public String createReportInteractive(String signerId) throws Exception {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.print("Suspect: ");
        String suspect = sc.nextLine().trim();

        System.out.print("Description: ");
        String description = sc.nextLine().trim();

        System.out.print("Location: ");
        String location = sc.nextLine().trim();

        // -------------------------------
        // 1. Build Report Object
        // -------------------------------
        String reportId = UUID.randomUUID().toString();
        String reportCreationTimestamp = Instant.now().toString();
        String reporterPseudonym = loadSelfPseudonymFromConfigOrDb();

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
        // 2. Build Inner Payload
        // -------------------------------
        InnerPayload payload = new InnerPayload();
        payload.setReport(report);
        payload.setSignature("PLACEHOLDER_SIGNATURE_BASE64");

        // -------------------------------
        // 3. Prepare Metadata
        // -------------------------------
        long lastSeq = db.getLastSequenceNumber(signerId);
        long nextSeq = lastSeq + 1;

        String prevHash = db.getLastEnvelopeHash(signerId);
        String prevHashB64 = (prevHash == null || prevHash.isEmpty()) ? "" : prevHash;

        Metadata metadata = new Metadata();
        metadata.setReportId(reportId);
        metadata.setMetadataTimestamp(Instant.now().toString());
        metadata.setReportCreationTimestamp(reportCreationTimestamp);
        metadata.setNodeSequenceNumber(nextSeq);
        metadata.setPrevEnvelopeHash(prevHashB64);
        metadata.setSignerNodeId(signerId);
        metadata.setSignerAlg("Ed25519");

        // -------------------------------
        // 4. Build Dummy Content Key Encryption
        // -------------------------------
        ContentKeyEncrypted keyEnc = new ContentKeyEncrypted();
        keyEnc.setEncryptionAlgorithm("RSA-OAEP-SHA256");

        EncryptedKey ek = new EncryptedKey();
        ek.setNode(signerId);
        ek.setEncryptedKey(HashUtils.bytesToBase64Url(("dummy-cek-" + reportId).getBytes(StandardCharsets.UTF_8)));

        keyEnc.getKeys().add(ek);

        // -------------------------------
        // 5. Build Dummy Report Encryption
        // -------------------------------
        byte[] innerBytes = gson.toJson(payload.toJson()).getBytes(StandardCharsets.UTF_8);

        byte[] nonce = new byte[12];
        rnd.nextBytes(nonce);

        ReportEncrypted reportEnc = new ReportEncrypted();
        reportEnc.setEncryptionAlgorithm("AES-256-GCM");
        reportEnc.setNonce(HashUtils.bytesToBase64Url(nonce));
        reportEnc.setCiphertext(HashUtils.bytesToBase64Url(innerBytes));
        reportEnc.setTag(HashUtils.bytesToBase64Url(("tag-" + reportId).getBytes(StandardCharsets.UTF_8)));

        // -------------------------------
        // 6. Build Envelope Object
        // -------------------------------
        Envelope envelope = new Envelope();
        envelope.setMetadata(metadata);
        envelope.setKeyEnc(keyEnc);
        envelope.setReportEnc(reportEnc);

        // -------------------------------
        // 7. Persist Envelope File
        // -------------------------------
        Path fullPath = envelope.writeSelf(Paths.get(Config.ENVELOPES_DIR));
        String envelopeHashHex = envelope.computeHashHex();

        // -------------------------------
        // 8. Persist in Local Database
        // -------------------------------
        db.insertReport(envelopeHashHex, fullPath.toString(), signerId, nextSeq, prevHash);
        db.upsertNodeState(signerId, nextSeq, envelopeHashHex);

        logger.info("Created envelope: " + fullPath.getFileName());
        return envelopeHashHex;
    }


    private String loadSelfPseudonymFromConfigOrDb() {
        return Config.NODE_PSEUDONYM;
    }
}
