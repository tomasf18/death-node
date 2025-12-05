package com.deathnode.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @Column(name = "node_id", length = 255)
    private String nodeId;

    @Column(nullable = false)
    private String pseudonym;

    @Column(name = "password_hash", nullable = false)
    private byte[] passwordHash;

    @Column(name = "enc_pub_key", nullable = false)
    private byte[] encPubKey;

    @Column(name = "sign_pub_key", nullable = false)
    private byte[] signPubKey;

    // ---- Getters & Setters ----

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getPseudonym() { return pseudonym; }
    public void setPseudonym(String pseudonym) { this.pseudonym = pseudonym; }

    public byte[] getPasswordHash() { return passwordHash; }
    public void setPasswordHash(byte[] passwordHash) { this.passwordHash = passwordHash; }

    public byte[] getEncPubKey() { return encPubKey; }
    public void setEncPubKey(byte[] encPubKey) { this.encPubKey = encPubKey; }

    public byte[] getSignPubKey() { return signPubKey; }
    public void setSignPubKey(byte[] signPubKey) { this.signPubKey = signPubKey; }
}
