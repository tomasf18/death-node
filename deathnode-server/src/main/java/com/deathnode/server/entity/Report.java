package com.deathnode.server.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reports",
       uniqueConstraints = @UniqueConstraint(columnNames = {"signer_node_id", "sequence_number"}))
public class Report {

    @Id
    @Column(name = "envelope_hash")
    private String envelopeHash;

    @Column(name = "signer_node_id", nullable = false)
    private String signerNodeId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "metadata_timestamp", nullable = false)
    private OffsetDateTime metadataTimestamp;

    @Column(name = "prev_report_hash", nullable = false)
    private byte[] prevReportHash;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    public String getEnvelopeHash() { return envelopeHash; }
    public void setEnvelopeHash(String envelopeHash) { this.envelopeHash = envelopeHash; }

    public String getSignerNodeId() { return signerNodeId; }
    public void setSignerNodeId(String signerNodeId) { this.signerNodeId = signerNodeId; }

    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public OffsetDateTime getMetadataTimestamp() { return metadataTimestamp; }
    public void setMetadataTimestamp(OffsetDateTime metadataTimestamp) { this.metadataTimestamp = metadataTimestamp; }

    public byte[] getPrevReportHash() { return prevReportHash; }
    public void setPrevReportHash(byte[] prevReportHash) { this.prevReportHash = prevReportHash; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
