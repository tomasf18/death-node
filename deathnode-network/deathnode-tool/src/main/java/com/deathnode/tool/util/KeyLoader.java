package com.deathnode.tool.util;

import com.google.gson.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * KeyLoader - Utility class for reading/writing keys and JSON files.
 * Supports RSA (for encryption) and Ed25519 (for signatures).
 */
public class KeyLoader {

    /**
     * Read raw file bytes.
     */
    private static byte[] readFile(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            return fis.readAllBytes();
        }
    }

    /**
     * Read file as UTF-8 string.
     */
    public static String readFileAsString(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] content = fis.readAllBytes();
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    /**
     * Write string to file.
     */
    public static void writeStringToFile(String path, String data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Read JSON object from file.
     */
    public static JsonObject readJsonObject(String path) throws IOException {
        String content = readFileAsString(path);
        return JsonParser.parseString(content).getAsJsonObject();
    }

    /**
     * Write JSON object to file.
     */
    public static void writeJsonObject(String path, JsonObject json) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String jsonStr = gson.toJson(json);
        writeStringToFile(path, jsonStr);
    }

    /**
     * Read AES secret key (raw bytes).
     */
    public static SecretKey readSecretKey(String secretKeyPath) throws Exception {
        byte[] encoded = readFile(secretKeyPath);
        return new SecretKeySpec(encoded, "AES");
    }

    /**
     * Read RSA public key (X.509 format, DER-encoded).
     */
    public static PublicKey readRsaPublicKey(String publicKeyPath) throws Exception {
        // System.out.println("Reading RSA public key from: " + publicKeyPath);
        byte[] pubEncoded = readFile(publicKeyPath);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFac = KeyFactory.getInstance("RSA");
        return keyFac.generatePublic(pubSpec);
    }

    /**
     * Read RSA private key (PKCS#8 format, DER-encoded).
     */
    public static PrivateKey readRsaPrivateKey(String privateKeyPath) throws Exception {
        // System.out.println("Reading RSA private key from: " + privateKeyPath);
        byte[] privEncoded = readFile(privateKeyPath);
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFac = KeyFactory.getInstance("RSA");
        return keyFac.generatePrivate(privSpec);
    }

    /**
     * Read Ed25519 public key (X.509 format, DER-encoded).
     */
    public static PublicKey readEd25519PublicKey(String publicKeyPath) throws Exception {
        // System.out.println("Reading Ed25519 public key from: " + publicKeyPath);
        byte[] pubEncoded = readFile(publicKeyPath);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFac = KeyFactory.getInstance("Ed25519");
        return keyFac.generatePublic(pubSpec);
    }

    /**
     * Read Ed25519 private key (PKCS#8 format, DER-encoded).
     */
    public static PrivateKey readEd25519PrivateKey(String privateKeyPath) throws Exception {
        // System.out.println("Reading Ed25519 private key from: " + privateKeyPath);
        byte[] privEncoded = readFile(privateKeyPath);
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFac = KeyFactory.getInstance("Ed25519");
        return keyFac.generatePrivate(privSpec);
    }

    public static PrivateKey loadPrivateKeyFromKeystore(String alias, String path, String password) throws Exception {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            throw new IllegalStateException("Keystore not found at: " + path);
        }

        String ksType = "JKS";
        KeyStore ks = KeyStore.getInstance(ksType);
        try (InputStream is = Files.newInputStream(p)) {
            ks.load(is, password.toCharArray());
        }

        Key key = ks.getKey(alias, password.toCharArray());
        if (key instanceof PrivateKey) {
            return (PrivateKey) key;
        } else {
            return null;
        }
    }

    /* 
    * Helper: convert a base64-encoded DER public key to PublicKey
    * algorithm can be "RSA" or "Ed25519" depending on key type
    */
    public static PublicKey pemStringToPublicKey(String keyText, String algorithm) throws Exception {
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

    /**
     * Generate a new RSA key pair (2048 bits).
     * For testing purposes.
     */
    public static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /**
     * Generate a new Ed25519 key pair.
     * For testing purposes. Requires Java 15+ or BouncyCastle.
     */
    public static KeyPair generateEd25519KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        return kpg.generateKeyPair();
    }

    /**
     * Write RSA public key to file (X.509 DER format).
     */
    public static void writeRsaPublicKey(String path, PublicKey key) throws IOException {
        byte[] encoded = key.getEncoded();
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(encoded);
        }
        System.out.println("RSA public key written to: " + path);
    }

    /**
     * Write RSA private key to file (PKCS#8 DER format).
     */
    public static void writeRsaPrivateKey(String path, PrivateKey key) throws IOException {
        byte[] encoded = key.getEncoded();
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(encoded);
        }
        System.out.println("RSA private key written to: " + path);
    }

    /**
     * Write Ed25519 public key to file (X.509 DER format).
     */
    public static void writeEd25519PublicKey(String path, PublicKey key) throws IOException {
        byte[] encoded = key.getEncoded();
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(encoded);
        }
        System.out.println("Ed25519 public key written to: " + path);
    }

    /**
     * Write Ed25519 private key to file (PKCS#8 DER format).
     */
    public static void writeEd25519PrivateKey(String path, PrivateKey key) throws IOException {
        byte[] encoded = key.getEncoded();
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(encoded);
        }
        System.out.println("Ed25519 private key written to: " + path);
    }
}
