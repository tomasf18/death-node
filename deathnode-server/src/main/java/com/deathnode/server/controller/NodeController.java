package com.deathnode.server.controller;

import com.deathnode.server.entity.Node;
import com.deathnode.server.repository.NodeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    private final NodeRepository nodeRepository;

    public NodeController(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @PostMapping
    public Node createNode(@RequestBody Node node) {
        return nodeRepository.save(node);
    }

    @GetMapping
    public List<Node> getAllNodes() {
        return nodeRepository.findAll();
    }
} 
