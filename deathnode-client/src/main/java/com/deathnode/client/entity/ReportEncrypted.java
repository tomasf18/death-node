package com.deathnode.client.entity;

import com.google.gson.JsonObject;

public class ReportEncrypted {

    private String encryptionAlgorithm;
    private String nonce;      // base64url
    private String ciphertext; // base64url
    private String tag;        // base64url

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("encryption_algorithm", encryptionAlgorithm);
        o.addProperty("nonce", nonce);
        o.addProperty("ciphertext", ciphertext);
        o.addProperty("tag", tag);
        return o;
    }

    public static ReportEncrypted fromJson(JsonObject o) {
        ReportEncrypted r = new ReportEncrypted();
        r.setEncryptionAlgorithm(o.get("encryption_algorithm").getAsString());
        r.setNonce(o.get("nonce").getAsString());
        r.setCiphertext(o.get("ciphertext").getAsString());
        r.setTag(o.get("tag").getAsString());
        return r;
    }
}
