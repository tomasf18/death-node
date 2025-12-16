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

    public static String bytesToHex(byte[] bytes) { // only used for hash representation
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b)); // convert byte to hex by formatting each byte as a two-digit
                                                 // hexadecimal number
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}