package secure_documents;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.spec.SecretKeySpec;

import jakarta.json.*;

public class KeyLoader {
    private static byte[] readFile(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        byte[] content = new byte[fis.available()];
        fis.read(content);
        fis.close();
        return content;
    }

    public static String readFileAsString(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] content = fis.readAllBytes();
            return new String(content, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static void writeStringToFile(String path, String data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static JsonObject readJsonObject(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path);
             JsonReader reader = Json.createReader(fis)) {
            return reader.readObject();
        }
    }

    public static void writeJsonObject(String path, JsonObject json) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path);
             JsonWriter writer = Json.createWriter(fos)) {
            writer.writeObject(json);
        }
    }


    public static Key readSecretKey(String secretKeyPath) throws Exception {
        byte[] encoded = readFile(secretKeyPath);
        return new SecretKeySpec(encoded, "AES");
    }

    public static PublicKey readPublicKey(String publicKeyPath) throws Exception {
        System.out.println("Reading public key from file " + publicKeyPath + " ...");
        byte[] pubEncoded = readFile(publicKeyPath);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");
        return keyFacPub.generatePublic(pubSpec);
    }

    public static PrivateKey readPrivateKey(String privateKeyPath) throws Exception {
        System.out.println("Reading private key from file " + privateKeyPath + " ...");
        byte[] privEncoded = readFile(privateKeyPath);
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");
        return keyFacPriv.generatePrivate(privSpec);
    }
}

