package com.deathnode.common.model;

import com.google.gson.JsonObject;

public class InnerPayload {

    private Report report;
    private String signature; // base64url

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("report", report.toJson());
        o.addProperty("signature", signature);
        return o;
    }

    public static InnerPayload fromJson(JsonObject o) {
        InnerPayload p = new InnerPayload();
        p.setReport(Report.fromJson(o.getAsJsonObject("report")));
        p.setSignature(o.get("signature").getAsString());
        return p;
    }
}
