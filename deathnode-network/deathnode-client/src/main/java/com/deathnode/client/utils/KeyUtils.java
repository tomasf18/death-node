package com.deathnode.client.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.deathnode.client.config.Config;

public class KeyUtils {
    // -------------------------------
    // Helper: load private key from keystore (JKS) for given alias
    // -------------------------------
    public static PrivateKey loadPrivateKeyFromKeystore(String alias) throws Exception {
        Path p = Paths.get(Config.getKeystorePath());
        if (!Files.exists(p)) {
            throw new IllegalStateException("Keystore not found at: " + Config.getKeystorePath());
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
    public static PublicKey publicKeyFromBase64(String keyText, String algorithm) throws Exception {
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
}
