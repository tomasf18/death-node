package com.deathnode.client;

import java.security.MessageDigest;

public class HashUtils {

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b)); // convert byte to hex by formatting each byte as a two-digit hexadecimal number
        return sb.toString();
    }
}