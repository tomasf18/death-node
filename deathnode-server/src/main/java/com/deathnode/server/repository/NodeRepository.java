package com.deathnode.server.repository;

import com.deathnode.server.model.Node;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<Node, String> {
}
