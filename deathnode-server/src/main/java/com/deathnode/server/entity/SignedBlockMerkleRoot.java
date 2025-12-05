package com.deathnode.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "signed_block_merkle_roots")
public class SignedBlockMerkleRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long blockId;

    @Column(nullable = false, unique = true)
    private Long blockNumber;

    @Column(nullable = false, unique = true)
    private byte[] blockRoot;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String perNodeRootsJson;

    private byte[] prevBlockRoot;

    @Column(nullable = false)
    private byte[] serverSignature;

    // Getters & setters omitted for brevity
}
