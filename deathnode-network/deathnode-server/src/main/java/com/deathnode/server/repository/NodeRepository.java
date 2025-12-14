package com.deathnode.server.repository;

import com.deathnode.server.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<Node, String> {
    Node findByNodeId(String nodeId);
}
