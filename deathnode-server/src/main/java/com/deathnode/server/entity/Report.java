package com.deathnode.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reports", uniqueConstraints = @UniqueConstraint(columnNames = {"signer_node_id", "node_sequence_number"}))
public class Report {

    @Id
    @Column(name = "envelope_hash", length = 64)
    private String envelopeHash;

    @ManyToOne(optional = false)
    @JoinColumn(name = "signer_node_id", nullable = false)
    private Node signerNode;

    @Column(name = "node_sequence_number", nullable = false)
    private long nodeSequenceNumber;

    @Column(name = "global_sequence_number")
    private Long globalSequenceNumber;

    @Column(name = "metadata_timestamp", nullable = false)
    private OffsetDateTime metadataTimestamp;

    @Column(name = "prev_report_hash", length = 64)
    private String prevReportHash;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    public Report() {
        // Default constructor
    }

     // ---- Getters & Setters ----

    public String getEnvelopeHash() { return envelopeHash; }
    public void setEnvelopeHash(String envelopeHash) { this.envelopeHash = envelopeHash; }

    public Node getSignerNode() { return signerNode; }
    public void setSignerNode(Node signerNode) { this.signerNode = signerNode; }

    public long getNodeSequenceNumber() { return nodeSequenceNumber; }
    public void setNodeSequenceNumber(long nodeSequenceNumber) { this.nodeSequenceNumber = nodeSequenceNumber; }

    public Long getGlobalSequenceNumber() { return globalSequenceNumber; }
    public void setGlobalSequenceNumber(Long globalSequenceNumber) { this.globalSequenceNumber = globalSequenceNumber; }

    public OffsetDateTime getMetadataTimestamp() { return metadataTimestamp; }
    public void setMetadataTimestamp(OffsetDateTime metadataTimestamp) { this.metadataTimestamp = metadataTimestamp; }

    public String getPrevReportHash() { return prevReportHash; }
    public void setPrevReportHash(String prevReportHash) { this.prevReportHash = prevReportHash; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
