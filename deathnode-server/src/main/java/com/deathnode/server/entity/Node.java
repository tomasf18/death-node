package com.deathnode.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.CascadeType;

import java.util.List;  

@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @Column(name = "node_id", length = 255)
    private String nodeId;

    @Column(name = "pseudonym", nullable = false)
    private String pseudonym;

    @Column(name = "enc_pub_key", length = 900, nullable = false)
    private String encPubKey;

    @Column(name = "sign_pub_key", length = 300, nullable = false)
    private String signPubKey;

    @OneToMany(mappedBy = "signerNode")
    private List<Report> reports;

    @OneToOne(mappedBy = "node", cascade = CascadeType.ALL)
    private NodeSyncState syncState;

    public Node() {
        // Default constructor
    }

    // ---- Getters & Setters ----

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getPseudonym() { return pseudonym; }
    public void setPseudonym(String pseudonym) { this.pseudonym = pseudonym; }

    public String getEncPubKey() { return encPubKey; }
    public void setEncPubKey(String encPubKey) { this.encPubKey = encPubKey; }

    public String getSignPubKey() { return signPubKey; }
    public void setSignPubKey(String signPubKey) { this.signPubKey = signPubKey; }

    public List<Report> getReports() { return reports; }
    public void setReports(List<Report> reports) { this.reports = reports; }

    public NodeSyncState getSyncState() { return syncState; }
    public void setSyncState(NodeSyncState syncState) { this.syncState = syncState; }
}
