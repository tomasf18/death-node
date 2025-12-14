package com.deathnode.common.model;

import com.deathnode.common.util.HashUtils;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;

public class Envelope {

    private Metadata metadata;
    private ContentKeyEncrypted keyEnc;
    private ReportEncrypted reportEnc;

    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public ContentKeyEncrypted getKeyEnc() {
        return keyEnc;
    }

    public void setKeyEnc(ContentKeyEncrypted keyEnc) {
        this.keyEnc = keyEnc;
    }

    public ReportEncrypted getReportEnc() {
        return reportEnc;
    }

    public void setReportEnc(ReportEncrypted reportEnc) {
        this.reportEnc = reportEnc;
    }

    // ---------- Serialization ----------

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("metadata", metadata.toJson());
        o.add("key_encrypted", keyEnc.toJson());
        o.add("report_encrypted", reportEnc.toJson());
        return o;
    }

    public byte[] toBytes() {
        return gson.toJson(toJson()).getBytes(StandardCharsets.UTF_8);
    }

    public static Envelope fromJson(JsonObject o) {
        Envelope e = new Envelope();
        e.setMetadata(Metadata.fromJson(o.getAsJsonObject("metadata")));
        e.setKeyEnc(ContentKeyEncrypted.fromJson(o.getAsJsonObject("key_encrypted")));
        e.setReportEnc(ReportEncrypted.fromJson(o.getAsJsonObject("report_encrypted")));
        return e;
    }

    public static Envelope read(Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        JsonObject o = JsonParser.parseString(new String(data)).getAsJsonObject();
        return fromJson(o);
    }

    // ---------- File + Hash ----------

    public String computeHashHex() {
        return HashUtils.sha256Hex(toBytes());
    }

    public Path writeSelf(Path dir) throws Exception {
        Files.createDirectories(dir);
        String hash = computeHashHex();
        Path out = dir.resolve(hash + ".json");
        Files.write(out, toBytes(), StandardOpenOption.CREATE_NEW);
        return out;
    }

    // ---------- Helper for placeholder encryption ----------

    public static Envelope buildDummy(InnerPayload payload, Metadata metadata, String signerNode) {
        Envelope e = new Envelope();

        e.setMetadata(metadata);

        ContentKeyEncrypted k = new ContentKeyEncrypted();
        k.setEncryptionAlgorithm("RSA-OAEP-SHA256");

        EncryptedKey ek = new EncryptedKey();
        ek.setNode(signerNode);
        ek.setEncryptedKey(b64("dummy-cek-" + metadata.getReportId()));
        k.getKeys().add(ek);

        e.setKeyEnc(k);

        ReportEncrypted r = new ReportEncrypted();
        r.setEncryptionAlgorithm("AES-256-GCM");
        r.setNonce(b64("nonce-" + metadata.getReportId()));
        r.setCiphertext(b64(payload.toJson().toString()));
        r.setTag(b64("tag-" + metadata.getReportId()));

        e.setReportEnc(r);

        return e;
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                s.getBytes(StandardCharsets.UTF_8)
        );
    }
}
