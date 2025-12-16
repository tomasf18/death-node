package com.deathnode.server.repository;

import com.deathnode.server.entity.SignedBlockMerkleRoot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SignedBlockMerkleRootRepository extends JpaRepository<SignedBlockMerkleRoot, Long> {
    @Query("SELECT s FROM SignedBlockMerkleRoot s WHERE s.blockNumber = (SELECT MAX(sb.blockNumber) FROM SignedBlockMerkleRoot sb)")
    Optional<SignedBlockMerkleRoot> findByHighestBlockNumber();
}
