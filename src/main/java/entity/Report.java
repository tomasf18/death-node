package entity;

import java.time.Instant;
import java.util.UUID;

public class Report {

    public static class ReportContent {
        public String suspect;
        public String description;
        public String location;

        public ReportContent() {}

        public ReportContent(String suspect, String description, String location) {
            this.suspect = suspect;
            this.description = description;
            this.location = location;
        }
    }

    private final String id;
    private final String timestamp;
    private final String author;
    private final ReportContent content;
    private final int version;
    private final String status;


    public Report(String id, String timestamp, String author, ReportContent content, int version, String status) {
        this.id = id;
        this.timestamp = timestamp;
        this.author = author;
        this.content = content;
        this.version = version;
        this.status = status;
    }

    public Report(String author, ReportContent content, int version, String status) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.content = content;
        this.timestamp = Instant.now().toString();
        this.version = version;
        this.status = status;
    }

    public String getId() { return id; }

    public String getTimestamp() {
        return timestamp;
    }

    public String getAuthor() {
        return author;
    }

    public ReportContent getContent() {
        return content;
    }

    public String getStatus() { return status; }

    public int getVersion() { return version; }

}
