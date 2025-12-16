package com.deathnode.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

@Entity
@Table(name = "signed_block_merkle_roots")
public class SignedBlockMerkleRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long blockId;

    @Column(name = "block_number", nullable = false, unique = true)
    private long blockNumber;

    @Column(name = "block_root", nullable = false, unique = true, length = 64)
    private String blockRoot;

    @Column(name = "prev_block_root", length = 64)
    private String prevBlockRoot;

    public SignedBlockMerkleRoot() {
        // Default constructor
    }

    public SignedBlockMerkleRoot(long blockNumber, String blockRoot, String prevBlockRoot) {
        this.blockNumber = blockNumber;
        this.blockRoot = blockRoot;
        this.prevBlockRoot = prevBlockRoot;
    }

    public long getBlockId() {
        return blockId;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public String getBlockRoot() {
        return blockRoot;
    }
}
