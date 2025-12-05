package com.deathnode.server.dto;

public class SyncRequestDTO {

    private String nodeId;
    private byte[] signedBufferRoot;

    // TODO (SECURITY - LATER)
    // - Include buffer contents
    // - Include metadata proofs

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public byte[] getSignedBufferRoot() { return signedBufferRoot; }
    public void setSignedBufferRoot(byte[] signedBufferRoot) { this.signedBufferRoot = signedBufferRoot; }
}
