package com.deathnode.common.util;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * HashUtils - Utility class for computing SHA-256 hashes.
 * Used mainly for hash chain (prev_envelope_hash).
 */
public class HashUtils {

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return bytesToHex(sha256(data));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    public static String bytesToBase64UrlEncoded(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b)); // convert byte to hex by formatting each byte as a two-digit
                                                 // hexadecimal number
        return sb.toString();
    }
}