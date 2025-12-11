package com.deathnode.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;

@Entity
@Table(name = "nodes_sync_state")
public class NodeSyncState {

    @Id
    @Column(name = "node_id")
    private String nodeId; 
    
    @MapsId 
    @OneToOne
    @JoinColumn(name = "node_id", nullable = false) // FK/PK column
    private Node node;

    @Column(name = "last_sequence_number")
    private Long lastSequenceNumber;

    @Column(name = "last_envelope_hash", length = 64)
    private String lastEnvelopeHash;

    public NodeSyncState() {
        // Default constructor
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public Node getNode() { return node; }
    public void setNode(Node node) { this.node = node; }

    public Long getLastSequenceNumber() { return lastSequenceNumber; }
    public void setLastSequenceNumber(Long lastSequenceNumber) { this.lastSequenceNumber = lastSequenceNumber; }

    public String getLastEnvelopeHash() { return lastEnvelopeHash; }
    public void setLastEnvelopeHash(String lastEnvelopeHash) { this.lastEnvelopeHash = lastEnvelopeHash; }
}
