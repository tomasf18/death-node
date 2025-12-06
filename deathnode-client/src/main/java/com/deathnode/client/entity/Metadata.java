package com.deathnode.client.entity;

import com.google.gson.JsonObject;

public class Metadata {

    private String reportId;
    private String metadataTimestamp;
    private String reportCreationTimestamp;
    private long nodeSequenceNumber;
    private String prevEnvelopeHash; // base64url (empty if none)

    private String signerNodeId;
    private String signerAlg;

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getMetadataTimestamp() {
        return metadataTimestamp;
    }

    public void setMetadataTimestamp(String metadataTimestamp) {
        this.metadataTimestamp = metadataTimestamp;
    }

    public String getReportCreationTimestamp() {
        return reportCreationTimestamp;
    }

    public void setReportCreationTimestamp(String reportCreationTimestamp) {
        this.reportCreationTimestamp = reportCreationTimestamp;
    }

    public long getNodeSequenceNumber() {
        return nodeSequenceNumber;
    }

    public void setNodeSequenceNumber(long nodeSequenceNumber) {
        this.nodeSequenceNumber = nodeSequenceNumber;
    }

    public String getPrevEnvelopeHash() {
        return prevEnvelopeHash;
    }

    public void setPrevEnvelopeHash(String prevEnvelopeHash) {
        this.prevEnvelopeHash = prevEnvelopeHash;
    }

    public String getSignerNodeId() {
        return signerNodeId;
    }

    public void setSignerNodeId(String signerNodeId) {
        this.signerNodeId = signerNodeId;
    }

    public String getSignerAlg() {
        return signerAlg;
    }

    public void setSignerAlg(String signerAlg) {
        this.signerAlg = signerAlg;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("report_id", reportId);
        o.addProperty("metadata_timestamp", metadataTimestamp);
        o.addProperty("report_creation_timestamp", reportCreationTimestamp);
        o.addProperty("node_sequence_number", nodeSequenceNumber);
        o.addProperty("prev_envelope_hash", prevEnvelopeHash == null ? "" : prevEnvelopeHash);

        JsonObject signer = new JsonObject();
        signer.addProperty("node_id", signerNodeId);
        signer.addProperty("alg", signerAlg);
        o.add("signer", signer);

        return o;
    }

    public static Metadata fromJson(JsonObject o) {
        Metadata m = new Metadata();
        m.setReportId(o.get("report_id").getAsString());
        m.setMetadataTimestamp(o.get("metadata_timestamp").getAsString());
        m.setReportCreationTimestamp(o.get("report_creation_timestamp").getAsString());
        m.setNodeSequenceNumber(o.get("node_sequence_number").getAsLong());
        m.setPrevEnvelopeHash(o.get("prev_envelope_hash").getAsString());

        JsonObject s = o.getAsJsonObject("signer");
        m.setSignerNodeId(s.get("node_id").getAsString());
        m.setSignerAlg(s.get("alg").getAsString());
        return m;
    }
}
