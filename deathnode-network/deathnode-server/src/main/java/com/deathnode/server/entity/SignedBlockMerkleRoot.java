package com.deathnode.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import org.hibernate.annotations.Type;
import java.util.Map;

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

    @Type(JsonType.class)
    @Column(name = "per_node_roots_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> perNodeRoots;

    @Column(name = "prev_block_root", length = 64)
    private String prevBlockRoot;

    @Column(name = "server_signature", nullable = false, length = 64)
    private String serverSignature;

    public SignedBlockMerkleRoot() {
        // Default constructor
    }

    // Getters & setters omitted for brevity
}
