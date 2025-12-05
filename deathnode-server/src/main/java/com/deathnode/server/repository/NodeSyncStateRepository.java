package com.deathnode.server.repository;

import com.deathnode.server.entity.NodeSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeSyncStateRepository extends JpaRepository<NodeSyncState, String> {
}
