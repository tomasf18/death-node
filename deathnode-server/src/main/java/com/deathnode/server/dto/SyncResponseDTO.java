package com.deathnode.server.dto;

public class SyncResponseDTO {

    private byte[] blockRoot;
    private byte[] serverSignature;

    // TODO (SECURITY - LATER)
    // - Include per-node signed roots
    // - Include ordered report list

    public byte[] getBlockRoot() { return blockRoot; }
    public void setBlockRoot(byte[] blockRoot) { this.blockRoot = blockRoot; }

    public byte[] getServerSignature() { return serverSignature; }
    public void setServerSignature(byte[] serverSignature) { this.serverSignature = serverSignature; }
}
