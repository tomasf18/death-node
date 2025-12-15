package com.deathnode.server.repository;

import com.deathnode.server.entity.SignedBlockMerkleRoot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignedBlockMerkleRootRepository extends JpaRepository<SignedBlockMerkleRoot, Long> {
}
