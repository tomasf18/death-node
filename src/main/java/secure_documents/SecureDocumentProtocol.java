package secure_documents;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import entity.Report;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * SecureDocumentProtocol
 * <p>
 * Protect / Check / Unprotect for JSON documents using:
 * - AES-256-GCM for payload encryption (DEK)
 * - RSA-OAEP(SHA-256) to encrypt DEK for recipient
 * - RSA-PSS(SHA-256) to sign envelope (sender)
 * <p>
 * Requires Jakarta for JSON canonicalization.
 */
public class SecureDocumentProtocol {

    // AES-GCM parameters
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_SIZE = 128; // 16 bytes tag
    private static final int GCM_IV_SIZE = 12; // recommended 12 bytes nonce

    // RSA parameters for OAEP and PSS
    private static final String RSA_OAEP_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String RSA_PSS_ALG = "RSASSA-PSS";
    private static final String AES_GCM = "AES/GCM/NoPadding";

    private static final SecureRandom rng = new SecureRandom();

    // ---------- utils ----------
    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts)
            total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    public static JsonObject reportToJson(Report report) {
        return Json.createObjectBuilder()
                .add("report_id", report.getId())
                .add("timestamp", report.getTimestamp())
                .add("reporter_pseudonym", report.getAuthor())
                .add("version", report.getVersion())
                .add("status", report.getStatus())
                .add("content", Json.createObjectBuilder()
                        .add("suspect", report.getContent().suspect)
                        .add("description", report.getContent().description)
                        .add("location", report.getContent().location)
                        .build()
                )
                .build();
    }

    public static Report JSONToReport(JsonObject json) {
        JsonObject contentJson = json.getJsonObject("content");

        Report.ReportContent content = new Report.ReportContent(
                contentJson.getString("suspect"),
                contentJson.getString("description"),
                contentJson.getString("location")
        );

        return new Report(
                json.getString("report_id"),
                json.getString("timestamp"),
                json.getString("reporter_pseudonym"),
                content,
                json.getInt("version"),
                json.getString("status")
        );
    }

    // ---------- protect ----------
    /**
     * Protect a JSON document.
     *
     * @param report Report object with the content of the report
     * @param recipientRsaPub recipient RSA public key (to encrypt DEK)
     * @param senderRsaPriv   sender RSA private key (to sign envelope)
     * @return envelope JSON string with fields: key_enc, nonce, metadata,
     *         ciphertext, tag, signature
     */
    public static JsonObject protect(Report report,
                                     PublicKey recipientRsaPub,
                                     PrivateKey senderRsaPriv) throws Exception {

        JsonObject reportJson = reportToJson(report);

        // 2) canonicalize AAD
        JsonObject aadJson = Json.createObjectBuilder()
                .add("timestamp", report.getTimestamp())
                .add("report_id", report.getId())
                .build();
        byte[] aadBytes = aadJson.toString().getBytes(StandardCharsets.UTF_8);


        // 3) generate ephemeral DEK (AES-256)
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(AES_KEY_SIZE);
        SecretKey dek = kgen.generateKey();

        // 4) generate nonce (IV) 12 bytes
        byte[] iv = new byte[GCM_IV_SIZE];
        rng.nextBytes(iv);

        // 5) AES-GCM encrypt plaintext
        Cipher aesGcm = Cipher.getInstance(AES_GCM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
        aesGcm.init(Cipher.ENCRYPT_MODE, dek, gcmSpec);
        aesGcm.updateAAD(aadBytes);
        byte[] ciphertextWithTag = aesGcm.doFinal(reportJson.toString().getBytes(StandardCharsets.UTF_8));

        // Split ciphertext and tag
        int tagLengthBytes = GCM_TAG_SIZE / 8;
        int ciphertextLen = ciphertextWithTag.length - tagLengthBytes;
        byte[] ciphertext = Arrays.copyOfRange(ciphertextWithTag, 0, ciphertextLen);
        byte[] tag = Arrays.copyOfRange(ciphertextWithTag, ciphertextLen, ciphertextWithTag.length);

        // 6) Encrypt DEK with recipient RSA-OAEP (RSA public key)
        Cipher rsaOaep = Cipher.getInstance(RSA_OAEP_TRANSFORM);
        rsaOaep.init(Cipher.ENCRYPT_MODE, recipientRsaPub);
        byte[] encryptedDek = rsaOaep.doFinal(dek.getEncoded());

        // 7) Compute signature (RSA-PSS) over (aad || ciphertext)
        byte[] toSign = concat(aadBytes, ciphertext);
        Signature sig = Signature.getInstance(RSA_PSS_ALG);
        PSSParameterSpec pssSpec = new PSSParameterSpec(
                "SHA-256",
                "MGF1",
                new MGF1ParameterSpec("SHA-256"),
                32,
                1
        );
        sig.setParameter(pssSpec);
        sig.initSign(senderRsaPriv, rng);
        sig.update(toSign);
        byte[] signature = sig.sign();

        // 8) Build envelope (JSON-friendly, use Base64 for binary fields)
        JsonObject reportEnc = Json.createObjectBuilder()
                .add("nonce",      Base64.getEncoder().encodeToString(iv))
                .add("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
                .add("tag",        Base64.getEncoder().encodeToString(tag))
                .build();

        return Json.createObjectBuilder()
                .add("key_enc", Base64.getEncoder().encodeToString(encryptedDek))
                .add("metadata", aadJson)
                .add("report_enc", reportEnc)
                .add("signature", Base64.getEncoder().encodeToString(signature))
                .build();
    }

    // ---------- check ----------
    /**
     * Check envelope authenticity verifies the structure of the envelope.
     * <p>
     * Does NOT require recipient private key (does not decrypt).
     *
     * @param envelopeJson JSON returned by protect()
     * @return map with keys: valid (boolean) and reasons (list)
     */
    public static Map<String, Object> check(JsonObject envelopeJson) {
        Map<String, Object> resp = new HashMap<>();
        List<String> reasons = new ArrayList<>();
        resp.put("reasons", reasons);

        try {
            // Verifies the existence of these fields : "report_enc"; "metadata"; "signature"; "key_enc"
            List<String> requiredEnvelopeFields = List.of("report_enc", "metadata", "signature", "key_enc");
            for (String f : requiredEnvelopeFields) {
                if (!envelopeJson.containsKey(f) || envelopeJson.isNull(f)) {
                    reasons.add("Missing required envelope field: " + f);
                    resp.put("valid", false);
                    return resp;
                }
            }

            // Validate Metadata
            JsonObject metadata = envelopeJson.getJsonObject("metadata");
            if (metadata == null) {
                reasons.add("Invalid metadata");
                resp.put("valid", false);
                return resp;
            }

            List<String> requiredMetadataFields = List.of("timestamp", "report_id");
            for (String f : requiredMetadataFields) {
                if (!metadata.containsKey(f) || metadata.isNull(f)) {
                    reasons.add("Missing metadata field: " + f);
                    resp.put("valid", false);
                    return resp;
                }
            }

            // Validate Timestamp
            String timestampStr = metadata.getString("timestamp");
            try {
                Instant timestamp = Instant.parse(timestampStr);
                if (timestamp.isAfter(Instant.now())) {
                    reasons.add("Timestamp is in the future");
                    resp.put("valid", false);
                    return resp;
                }
            } catch (Exception e) {
                reasons.add("Invalid timestamp format");
                resp.put("valid", false);
                return resp;
            }

            // Validate "report_enc"
            JsonObject reportEnc = envelopeJson.getJsonObject("report_enc");
            if (reportEnc == null) {
                reasons.add("Field 'report_enc' must be a JSON object");
                resp.put("valid", false);
                return resp;
            }

            List<String> requiredReportEncFields = List.of("ciphertext", "nonce", "tag");
            for (String f : requiredReportEncFields) {
                if (!reportEnc.containsKey(f) || reportEnc.isNull(f)) {
                    reasons.add("Missing required report_enc field: " + f);
                    resp.put("valid", false);
                    return resp;
                }
            }

            // Se todas as verificações passaram
            resp.put("valid", true);

        } catch (Exception e) {
            resp.put("valid", false);
            reasons.add("Error during verification: " + e.getMessage());
        }

        return resp;
    }

    // ---------- unprotect ----------
    /**
     * Unprotect (decrypt) the envelope.
     *
     * @param envelopeJson the JSON returned by protect()
     * @param recipientRsaPriv the recipient's private key
     * @param  senderRsaPub the sender's public key
     * @return plaintext JSON string
     */
    public static Report unprotect(JsonObject envelopeJson,
                                   PrivateKey recipientRsaPriv,
                                   PublicKey senderRsaPub) throws Exception {

        // Parse envelope
        JsonObject reportEnc = envelopeJson.getJsonObject("report_enc");
        JsonObject metadata = envelopeJson.getJsonObject("metadata");

        byte[] encryptedDek = Base64.getDecoder().decode(envelopeJson.getString("key_enc"));
        byte[] nonce = Base64.getDecoder().decode(reportEnc.getString("nonce"));
        byte[] ciphertext = Base64.getDecoder().decode(reportEnc.getString("ciphertext"));
        byte[] tag = Base64.getDecoder().decode(reportEnc.getString("tag"));
        byte[] signature = Base64.getDecoder().decode(envelopeJson.getString("signature"));
        byte[] aadBytes = metadata.toString().getBytes(StandardCharsets.UTF_8);


        // 1) verify signature first (optional but recommended)
        byte[] toSign = concat(aadBytes, ciphertext);
        Signature sig = Signature.getInstance(RSA_PSS_ALG);
        PSSParameterSpec pssSpec = new PSSParameterSpec(
                "SHA-256",
                "MGF1",
                new MGF1ParameterSpec("SHA-256"),
                32,
                1
        );
        sig.setParameter(pssSpec);
        sig.initVerify(senderRsaPub);
        sig.update(toSign);
        if (!sig.verify(signature)) {
            throw new SecurityException("Signature verification failed");
        }

        // 2) decrypt DEK with recipient RSA private key (OAEP)
        Cipher rsaOaep = Cipher.getInstance(RSA_OAEP_TRANSFORM);
        rsaOaep.init(Cipher.DECRYPT_MODE, recipientRsaPriv);
        byte[] dekBytes = rsaOaep.doFinal(encryptedDek);
        SecretKey dek = new javax.crypto.spec.SecretKeySpec(dekBytes, "AES");

        // 3) AES-GCM decrypt
        Cipher aesGcm = Cipher.getInstance(AES_GCM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, nonce);
        aesGcm.init(Cipher.DECRYPT_MODE, dek, gcmSpec);
        aesGcm.updateAAD(aadBytes);

        // reconstruct ciphertext||tag for doFinal
        byte[] ciphertextAndTag = concat(ciphertext, tag);
        byte[] plaintextBytes;
        try {
            plaintextBytes = aesGcm.doFinal(ciphertextAndTag);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("GCM tag verification failed - ciphertext or AAD was tampered with", e);
        }

        String plaintextJson = new String(plaintextBytes, StandardCharsets.UTF_8);
        JsonReader reader = Json.createReader(new java.io.StringReader(plaintextJson));
        JsonObject reportJson = reader.readObject();
        reader.close();

        System.out.println(plaintextJson);
        return JSONToReport(reportJson);
    }
}
