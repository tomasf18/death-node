package com.deathnode.server.service;

import com.deathnode.server.entity.Node;
import com.deathnode.server.repository.NodeRepository;
import org.springframework.stereotype.Service;

@Service
public class NodeService {

    private final NodeRepository nodeRepository;

    public NodeService(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Node registerNode(Node node) {

        // TODO (SECURITY - LATER)
        // - Verify CA-signed cert
        // - Validate key formats
        // - Enforce key uniqueness

        return nodeRepository.save(node);
    }

    public boolean nodeExists(String nodeId) {
        return nodeRepository.existsById(nodeId);
    }
}
