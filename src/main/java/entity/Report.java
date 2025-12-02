package entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class Report {

    private String id;
    private String timestamp;
    private String author;
    private String content;
    private int sn;


    public Report(String id, String timestamp, String author, String content) {
        this.id = id;
        this.timestamp = timestamp;
        this.author = author;
        this.content = content;
    }

    public Report(String id, String author, String content) {
        this.id = id;
        this.author = author;
        this.content = content;
        this.timestamp = Instant.now().toString();
    }

    public Report(String author, String content) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.content = content;
        this.timestamp = Instant.now().toString();
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public void setSequenceNumber(int n) {
        this.sn = n;
    }

    public int getSequenceNumber() {
        return sn;
    }

}
