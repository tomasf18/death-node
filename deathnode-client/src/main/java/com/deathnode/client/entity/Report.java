package com.deathnode.client.entity;

import com.google.gson.JsonObject;

public class Report {

    private String reportId;
    private String reportCreationTimestamp;
    private String reporterPseudonym;
    private Content content;
    private int version;
    private String status;

    public static class Content {
        private String suspect;
        private String description;
        private String location;

        public String getSuspect() {
            return suspect;
        }

        public void setSuspect(String suspect) {
            this.suspect = suspect;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getReportCreationTimestamp() {
        return reportCreationTimestamp;
    }

    public void setReportCreationTimestamp(String reportCreationTimestamp) {
        this.reportCreationTimestamp = reportCreationTimestamp;
    }

    public String getReporterPseudonym() {
        return reporterPseudonym;
    }

    public void setReporterPseudonym(String reporterPseudonym) {
        this.reporterPseudonym = reporterPseudonym;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("report_id", reportId);
        o.addProperty("report_creation_timestamp", reportCreationTimestamp);
        o.addProperty("reporter_pseudonym", reporterPseudonym);

        JsonObject c = new JsonObject();
        c.addProperty("suspect", content.getSuspect());
        c.addProperty("description", content.getDescription());
        c.addProperty("location", content.getLocation());

        o.add("content", c);
        o.addProperty("version", version);
        o.addProperty("status", status);
        return o;
    }

    public static Report fromJson(JsonObject o) {
        Report r = new Report();
        r.setReportId(o.get("report_id").getAsString());
        r.setReportCreationTimestamp(o.get("report_creation_timestamp").getAsString());
        r.setReporterPseudonym(o.get("reporter_pseudonym").getAsString());

        JsonObject c = o.getAsJsonObject("content");
        Content content = new Content();
        content.setSuspect(c.get("suspect").getAsString());
        content.setDescription(c.get("description").getAsString());
        content.setLocation(c.get("location").getAsString());
        r.setContent(content);

        r.setVersion(o.get("version").getAsInt());
        r.setStatus(o.get("status").getAsString());
        return r;
    }
}
