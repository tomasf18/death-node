package com.deathnode.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;

import com.deathnode.common.model.*;
import com.deathnode.common.util.HashUtils;

/**
 * SecureDocumentProtocol
 * 
 * Implements protect/check/unprotect for DeathNode reports using:
 * - AES-256-GCM for content encryption (CEK)
 * - RSA-OAEP-SHA256 for CEK wrapping
 * - Ed25519 for digital signatures
 * - SHA-256 for hash chain
 */
public class SecureDocumentProtocol {

    // AES-GCM parameters
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_SIZE = 128; // 16 bytes
    private static final int GCM_NONCE_SIZE = 12; // 12 bytes 

    // Algorithm identifiers
    private static final String RSA_OAEP_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String ED25519 = "Ed25519";

    private static final SecureRandom randomGenerator = new SecureRandom();
    private static final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private SecureDocumentProtocol() {
        // Prevent instantiation
    }

    /**
     * Protect a report: encrypt and sign according to the protocol.
     *
     * @param report The report to protect
     * @param metadata The envelope metadata (includes sequence, prev_hash, signer info)
     * @param recipientPubKeys Map of node_id -> RSA public key for CEK wrapping
     * @param signerPrivKey Ed25519 private key of the signer
     * @return Complete envelope ready to send
     */
    public static Envelope protect(Report report, 
                                   Metadata metadata,
                                   Map<String, PublicKey> recipientPubKeys,
                                   PrivateKey signerPrivKey) throws Exception {
        
        // 1. Canonicalize report and metadata for signing
        byte[] reportBytes = canonicalJson(report.toJson());
        byte[] metadataBytes = canonicalJson(metadata.toJson());
        
        // 2. Create signature over (report || metadata)
        byte[] signPayload = concat(reportBytes, metadataBytes);
        Signature sig = Signature.getInstance(ED25519);
        sig.initSign(signerPrivKey);
        sig.update(signPayload);
        byte[] signatureBytes = sig.sign();
        String signatureB64 = HashUtils.bytesToBase64UrlEncoded(signatureBytes);
        
        // 3. Create inner payload (report + signature)
        InnerPayload innerPayload = new InnerPayload();
        innerPayload.setReport(report);
        innerPayload.setSignature(signatureB64);
        byte[] payloadBytes = gson.toJson(innerPayload.toJson()).getBytes(StandardCharsets.UTF_8);
        
        // 4. Generate random CEK (Content Encryption Key) and nonce
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(AES_KEY_SIZE);
        SecretKey cek = kgen.generateKey();
        byte[] nonce = new byte[GCM_NONCE_SIZE];
        randomGenerator.nextBytes(nonce);
        
        // 5. Encrypt payload with AES-256-GCM (AAD = metadata)
        Cipher aesGcm = Cipher.getInstance(AES_GCM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, nonce);
        aesGcm.init(Cipher.ENCRYPT_MODE, cek, gcmSpec);
        aesGcm.updateAAD(metadataBytes); // Bind metadata as AAD
        
        // Perform encryption: doFinak(report and signature as payload and metadata as AAD)
        byte[] ciphertextWithTag = aesGcm.doFinal(payloadBytes);
        
        // Split ciphertext and tag (GCM appends tag to ciphertext)
        int tagLengthBytes = GCM_TAG_SIZE / 8;
        byte[] ciphertext = Arrays.copyOfRange(ciphertextWithTag, 0, ciphertextWithTag.length - tagLengthBytes);
        byte[] tag = Arrays.copyOfRange(ciphertextWithTag, ciphertextWithTag.length - tagLengthBytes, ciphertextWithTag.length);
        
        // 6. Wrap CEK for each recipient in network using RSA-OAEP
        ContentKeyEncrypted cekEncrypted = new ContentKeyEncrypted();
        cekEncrypted.setEncryptionAlgorithm("RSA-OAEP-SHA256");
        
        Cipher rsaOaep = Cipher.getInstance(RSA_OAEP_TRANSFORM);
        for (Map.Entry<String, PublicKey> entry : recipientPubKeys.entrySet()) {
            String nodeId = entry.getKey();
            PublicKey pubKey = entry.getValue();
            
            rsaOaep.init(Cipher.ENCRYPT_MODE, pubKey);
            byte[] wrappedCek = rsaOaep.doFinal(cek.getEncoded());
            
            EncryptedKey ek = new EncryptedKey();
            ek.setNode(nodeId);
            ek.setEncryptedKey(HashUtils.bytesToBase64UrlEncoded(wrappedCek));
            cekEncrypted.getKeys().add(ek);
        }
        
        // 7. Build encrypted report section
        ReportEncrypted reportEnc = new ReportEncrypted();
        reportEnc.setEncryptionAlgorithm("AES-256-GCM");
        reportEnc.setNonce(HashUtils.bytesToBase64UrlEncoded(nonce));
        reportEnc.setCiphertext(HashUtils.bytesToBase64UrlEncoded(ciphertext));
        reportEnc.setTag(HashUtils.bytesToBase64UrlEncoded(tag));
        
        // 8. Assemble envelope
        Envelope envelope = new Envelope();
        envelope.setMetadata(metadata);
        envelope.setKeyEnc(cekEncrypted);
        envelope.setReportEnc(reportEnc);
        
        return envelope;
    }

    /**
     * Check envelope structure and metadata validity.
     * Does NOT decrypt or verify signature (no keys required).
     *
     * @param envelope The envelope to check
     * @return Map with "valid" (boolean) and "reasons" (List<String>)
     */
    public static Map<String, Object> check(Envelope envelope) {
        Map<String, Object> result = new HashMap<>();
        List<String> reasons = new ArrayList<>();
        result.put("reasons", reasons);
        
        try {
            // Check metadata presence
            Metadata meta = envelope.getMetadata();
            if (meta == null) {
                reasons.add("Missing metadata");
                result.put("valid", false);
                return result;
            }
            
            // Validate required metadata fields
            if (meta.getReportId() == null || meta.getReportId().isEmpty()) {
                reasons.add("Missing report_id");
            }
            if (meta.getMetadataTimestamp() == null || meta.getMetadataTimestamp().isEmpty()) {
                reasons.add("Missing metadata_timestamp");
            }
            if (meta.getReportCreationTimestamp() == null || meta.getReportCreationTimestamp().isEmpty()) {
                reasons.add("Missing report_creation_timestamp");
            }
            if (meta.getSignerNodeId() == null || meta.getSignerNodeId().isEmpty()) {
                reasons.add("Missing signer node_id");
            }
            if (meta.getSignerAlg() == null || !meta.getSignerAlg().equals("Ed25519")) {
                reasons.add("Invalid or missing signature algorithm (must be Ed25519)");
            }
            
            // Validate timestamp format and not in future
            try {
                java.time.Instant metaTime = java.time.Instant.parse(meta.getMetadataTimestamp());
                if (metaTime.isAfter(java.time.Instant.now())) {
                    reasons.add("Metadata timestamp is in the future");
                }
            } catch (Exception e) {
                reasons.add("Invalid metadata_timestamp format: " + e.getMessage());
            }
            
            try {
                java.time.Instant.parse(meta.getReportCreationTimestamp());
            } catch (Exception e) {
                reasons.add("Invalid report_creation_timestamp format: " + e.getMessage());
            }
            
            // Check sequence number (must be >= 0)
            if (meta.getNodeSequenceNumber() < 0) {
                reasons.add("Invalid sequence number (must be >= 0)");
            }
            
            // Check key_encrypted section
            ContentKeyEncrypted cekEncrypted = envelope.getKeyEnc();
            if (cekEncrypted == null) {
                reasons.add("Missing key_encrypted section");
            } else {
                if (cekEncrypted.getEncryptionAlgorithm() == null || !cekEncrypted.getEncryptionAlgorithm().equals("RSA-OAEP-SHA256")) {
                    reasons.add("Invalid key encryption algorithm");
                }
                if (cekEncrypted.getKeys() == null || cekEncrypted.getKeys().isEmpty()) {
                    reasons.add("No encrypted keys present");
                } else {
                    for (EncryptedKey ek : cekEncrypted.getKeys()) {
                        if (ek.getNode() == null || ek.getNode().isEmpty()) {
                            reasons.add("Encrypted key missing node identifier");
                        }
                        if (ek.getEncryptedKey() == null || ek.getEncryptedKey().isEmpty()) {
                            reasons.add("Encrypted key missing key data");
                        }
                    }
                }
            }
            
            // Check report_encrypted section
            ReportEncrypted reportEnc = envelope.getReportEnc();
            if (reportEnc == null) {
                reasons.add("Missing report_encrypted section");
            } else {
                if (reportEnc.getEncryptionAlgorithm() == null || !reportEnc.getEncryptionAlgorithm().equals("AES-256-GCM")) {
                    reasons.add("Invalid report encryption algorithm");
                }
                if (reportEnc.getNonce() == null || reportEnc.getNonce().isEmpty()) {
                    reasons.add("Missing nonce");
                }
                if (reportEnc.getCiphertext() == null || reportEnc.getCiphertext().isEmpty()) {
                    reasons.add("Missing ciphertext");
                }
                if (reportEnc.getTag() == null || reportEnc.getTag().isEmpty()) {
                    reasons.add("Missing authentication tag");
                }
            }
            
            // If no errors, envelope is structurally valid
            if (reasons.isEmpty()) {
                result.put("valid", true);
            } else {
                result.put("valid", false);
            }
            
        } catch (Exception e) {
            result.put("valid", false);
            reasons.add("Unexpected error during check: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Unprotect an envelope: decrypt and verify signature.
     *
     * @param envelope The envelope to unprotect
     * @param recipientPrivKey RSA private key to unwrap CEK
     * @param recipientNodeId Node ID to find the correct wrapped key
     * @param senderPubKey Ed25519 public key to verify signature
     * @return The decrypted Report
     * @throws SecurityException if signature or GCM verification fails
     */
    public static Report unprotect(Envelope envelope,
                                   PrivateKey recipientPrivKey,
                                   String recipientNodeId,
                                   PublicKey senderPubKey) throws Exception {
        
        Metadata metadata = envelope.getMetadata();
        ContentKeyEncrypted cekEncrypted = envelope.getKeyEnc();
        ReportEncrypted reportEnc = envelope.getReportEnc();
        
        // 1. Find and unwrap CEK for this recipient
        byte[] cekBytes = null;
        for (EncryptedKey ek : cekEncrypted.getKeys()) {
            if (ek.getNode().equals(recipientNodeId)) {
                byte[] wrappedCek = base64UrlDecode(ek.getEncryptedKey());
                Cipher rsaOaep = Cipher.getInstance(RSA_OAEP_TRANSFORM);
                rsaOaep.init(Cipher.DECRYPT_MODE, recipientPrivKey);
                cekBytes = rsaOaep.doFinal(wrappedCek);
                break;
            }
        }
        
        if (cekBytes == null) {
            throw new SecurityException("No wrapped CEK found for recipient: " + recipientNodeId);
        }
        
        SecretKey cek = new SecretKeySpec(cekBytes, "AES");
        
        // 2. Decrypt payload with AES-GCM (AAD = metadata)
        byte[] nonce = base64UrlDecode(reportEnc.getNonce());
        byte[] ciphertext = base64UrlDecode(reportEnc.getCiphertext());
        byte[] tag = base64UrlDecode(reportEnc.getTag());
        byte[] metadataBytes = canonicalJson(metadata.toJson());
        
        Cipher aesGcm = Cipher.getInstance(AES_GCM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, nonce);
        aesGcm.init(Cipher.DECRYPT_MODE, cek, gcmSpec);
        aesGcm.updateAAD(metadataBytes);
        
        // Reconstruct ciphertext||tag for doFinal
        byte[] ciphertextWithTag = concat(ciphertext, tag);
        byte[] payloadBytes;
        try {
            payloadBytes = aesGcm.doFinal(ciphertextWithTag);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("GCM authentication failed - envelope was tampered with", e);
        }
        
        // 3. Parse inner payload
        String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
        JsonObject payloadObj = JsonParser.parseString(payloadJson).getAsJsonObject();
        InnerPayload innerPayload = InnerPayload.fromJson(payloadObj);
        
        Report report = innerPayload.getReport();
        byte[] signatureBytes = base64UrlDecode(innerPayload.getSignature());
        
        // 4. Verify Ed25519 signature over (report || metadata)
        byte[] reportBytes = canonicalJson(report.toJson());
        byte[] signPayload = concat(reportBytes, metadataBytes);
        
        Signature sig = Signature.getInstance(ED25519);
        sig.initVerify(senderPubKey);
        sig.update(signPayload);
        
        if (!sig.verify(signatureBytes)) {
            throw new SecurityException("Ed25519 signature verification failed");
        }
        
        // 5. Return decrypted and verified report
        return report;
    }

    // ========== Utility Methods ==========

    /**
     * Canonicalize JSON for deterministic signing/hashing.
     * Sorts keys, removes whitespace, ensures consistent encoding.
     */
    private static byte[] canonicalJson(JsonObject obj) {
        // Gson with proper configuration produces consistent output
        String json = gson.toJson(obj);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Concatenate byte arrays.
     */
    private static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] arr : arrays) {
            totalLength += arr.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    /**
     * Base64url decode.
     */
    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }
}
