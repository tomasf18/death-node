package com.deathnode.client.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public class ContentKeyEncrypted {

    private String encryptionAlgorithm;
    private List<EncryptedKey> keys = new ArrayList<>();

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public List<EncryptedKey> getKeys() {
        return keys;
    }

    public void setKeys(List<EncryptedKey> keys) {
        this.keys = keys;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("encryption_algorithm", encryptionAlgorithm);

        JsonArray arr = new JsonArray();
        for (EncryptedKey k : keys) arr.add(k.toJson());
        o.add("key_per_node", arr);
        return o;
    }

    public static ContentKeyEncrypted fromJson(JsonObject o) {
        ContentKeyEncrypted k = new ContentKeyEncrypted();
        k.setEncryptionAlgorithm(o.get("encryption_algorithm").getAsString());
        o.getAsJsonArray("key_per_node").forEach(e ->
            k.getKeys().add(EncryptedKey.fromJson(e.getAsJsonObject()))
        );
        return k;
    }
}
