package com.deathnode.tool.entity;

import com.google.gson.JsonObject;

public class EncryptedKey {

    private String node;
    private String encryptedKey; // base64url

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("node", node);
        o.addProperty("encrypted_key", encryptedKey);
        return o;
    }

    public static EncryptedKey fromJson(JsonObject o) {
        EncryptedKey k = new EncryptedKey();
        k.setNode(o.get("node").getAsString());
        k.setEncryptedKey(o.get("encrypted_key").getAsString());
        return k;
    }
}
