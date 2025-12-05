package com.deathnode.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "nodes_sync_state")
public class NodeSyncState {

    @Id
    @Column(name = "node_id")
    private String nodeId;

    @Column(name = "last_sequence_number")
    private Long lastSequenceNumber;

    @Column(name = "last_envelope_hash")
    private byte[] lastEnvelopeHash;

    public NodeSyncState() {}

    public NodeSyncState(String nodeId) {
        this.nodeId = nodeId;
        this.lastSequenceNumber = 0L;
        this.lastEnvelopeHash = new byte[32];
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public Long getLastSequenceNumber() { return lastSequenceNumber; }
    public void setLastSequenceNumber(Long lastSequenceNumber) { this.lastSequenceNumber = lastSequenceNumber; }

    public byte[] getLastEnvelopeHash() { return lastEnvelopeHash; }
    public void setLastEnvelopeHash(byte[] lastEnvelopeHash) { this.lastEnvelopeHash = lastEnvelopeHash; }
}
