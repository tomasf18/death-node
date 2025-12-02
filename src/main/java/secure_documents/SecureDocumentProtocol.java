package secure_documents;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * SecureDocumentProtocol
 * <p>
 * Protect / Check / Unprotect for JSON documents using:
 * - AES-256-GCM for payload encryption (DEK)
 * - RSA-OAEP(SHA-256) to encrypt DEK for recipient
 * - RSA-PSS(SHA-256) to sign envelope (sender)
 * <p>
 * Requires Jackson (com.fasterxml.jackson.core:jackson-databind) for JSON
 * canonicalization.
 */
public class SecureDocumentProtocol {

    private static final ObjectMapper canonicalMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    // AES-GCM parameters
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_SIZE = 128; // 16 bytes tag
    private static final int GCM_IV_SIZE = 12; // recommended 12 bytes nonce

    // RSA parameters for OAEP and PSS
    private static final String RSA_OAEP_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String RSA_PSS_ALG = "SHA256withRSA";
    private static final String AES_GCM = "AES/GCM/NoPadding";

    private static final SecureRandom rng = new SecureRandom();

    // ---------- Helper: canonicalize JSON (string) ----------
    private static String canonicalizeJson(String json) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = canonicalMapper.readValue(json, LinkedHashMap.class);
        return canonicalMapper.writeValueAsString(map);
    }

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

    // ---------- protect ----------
    /**
     * Protect a JSON document.
     *
     * @param plaintextJson   canonical or raw JSON string of the document
     * @param recipientRsaPub recipient RSA public key (to encrypt DEK)
     * @param senderRsaPriv   sender RSA private key (to sign envelope)
     * @return envelope JSON string with fields: key_enc, nonce, metadata,
     *         ciphertext, tag, signature
     */
    public static String protect(String plaintextJson,
                                 PublicKey recipientRsaPub,
                                 PrivateKey senderRsaPriv) throws Exception {

        // 1) canonicalize plaintext
        String canonicalPlaintext = canonicalizeJson(plaintextJson);
        byte[] plaintextBytes = canonicalPlaintext.getBytes(StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(plaintextJson);

        // 2) canonicalize AAD
        Map<String, Object> aadMap = new HashMap<>();
        aadMap.put("timestamp", root.get("timestamp").asText());
        aadMap.put("version", root.get("version").asInt());
        byte[] aadBytes = canonicalMapper.writeValueAsBytes(aadMap);

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
        byte[] ciphertextWithTag = aesGcm.doFinal(plaintextBytes);

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
        sig.initSign(senderRsaPriv, rng);
        sig.update(toSign);
        byte[] signature = sig.sign();

        // 8) Build envelope (JSON-friendly, use Base64 for binary fields)
        Map<String, Object> reportEnc = new LinkedHashMap<>();
        reportEnc.put("nonce", Base64.getEncoder().encodeToString(iv));
        reportEnc.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
        reportEnc.put("tag", Base64.getEncoder().encodeToString(tag));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("key_enc", Base64.getEncoder().encodeToString(encryptedDek));
        envelope.put("metadata", aadMap);
        envelope.put("report_enc", reportEnc);
        envelope.put("signature", Base64.getEncoder().encodeToString(signature));

        return canonicalMapper.writeValueAsString(envelope);
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
    public static Map<String, Object> check(String envelopeJson) {
        Map<String, Object> resp = new HashMap<>();
        List<String> reasons = new ArrayList<>();
        resp.put("reasons", reasons);

        try {
            // Parse envelope
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = canonicalMapper.readValue(envelopeJson, LinkedHashMap.class);

            // Verifies the existing of the fields
            List<String> requiredEnvelopeFields = List.of("report_enc", "metadata", "signature");
            for (String f : requiredEnvelopeFields) {
                if (!envelope.containsKey(f) || envelope.get(f) == null) {
                    reasons.add("Missing required envelope field: " + f);
                    resp.put("valid", false);
                    return resp;
                }
            }

            // Validate metadata
            Object metadataObj = envelope.get("metadata");
            if (!(metadataObj instanceof Map)) {
                reasons.add("Invalid metadata");
                resp.put("valid", false);
                return resp;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) metadataObj;

            List<String> requiredMetadataFields = List.of("timestamp", "version");
            for (String f : requiredMetadataFields) {
                if (!metadata.containsKey(f) || metadata.get(f) == null) {
                    reasons.add("Missing metadata field: " + f);
                    resp.put("valid", false);
                    return resp;
                }
            }

            // Validate timestamp
            String timestampStr = metadata.get("timestamp").toString();
            try {
                Instant timestamp = Instant.parse(timestampStr); // ISO-8601
                // exemplo: verificar se não é no futuro
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

            // Validate version
            Object versionObj = metadata.get("version");
            int version;
            try {
                version = Integer.parseInt(versionObj.toString());
            } catch (Exception e) {
                reasons.add("Version must be a valid integer");
                resp.put("valid", false);
                return resp;
            }

            if (version <= 0) {
                reasons.add("Version must be > 0");
                resp.put("valid", false);
                return resp;
            }

            if (!(envelope.get("report_enc") instanceof Map)) {
                reasons.add("Field 'report_enc' must be a JSON object");
                resp.put("valid", false);
                return resp;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> reportEnc = (Map<String, Object>) envelope.get("report_enc");

            List<String> requiredReportEncFields = List.of("ciphertext", "nonce", "tag");
            for (String f : requiredReportEncFields) {
                if (!reportEnc.containsKey(f) || reportEnc.get(f) == null) {
                    reasons.add("Missing required report_enc field: " + f);
                    resp.put("valid", false);
                    return resp;
                }
            }
            // If all checks passed
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
    public static String unprotect(String envelopeJson,
                                   PrivateKey recipientRsaPriv,
                                   PublicKey senderRsaPub) throws Exception {

        // Parse envelope
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = canonicalMapper.readValue(envelopeJson, LinkedHashMap.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> reportEnc = (Map<String, Object>) envelope.get("report_enc");

        byte[] encryptedDek = Base64.getDecoder().decode((String) envelope.get("key_enc"));
        byte[] nonce = Base64.getDecoder().decode((String) reportEnc.get("nonce"));

        @SuppressWarnings("unchecked")
        Map<String, Object> aadMap = (Map<String, Object>) envelope.get("metadata");
        byte[] aadBytes = canonicalMapper.writeValueAsBytes(aadMap);

        byte[] ciphertext = Base64.getDecoder().decode((String) reportEnc.get("ciphertext"));
        byte[] tag = Base64.getDecoder().decode((String) reportEnc.get("tag"));
        byte[] signature = Base64.getDecoder().decode((String) envelope.get("signature"));

        // 1) verify signature first (optional but recommended)
        byte[] signed = concat(aadBytes, ciphertext);
        Signature sig = Signature.getInstance(RSA_PSS_ALG);
        sig.initVerify(senderRsaPub);
        sig.update(signed);
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

        try {
            byte[] plaintextBytes = aesGcm.doFinal(ciphertextAndTag);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("GCM tag verification failed - ciphertext or AAD was tampered with", e);
        }
    }
}
