package com.deathnode.server.grpc;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.deathnode.server.service.SyncCoordinator;

/**
 * Lightweight round state holder
 */
public class SyncRound {
    private final String roundId;
    private final Set<String> expectedNodes;
    private final Map<String, List<byte[]>> buffers = new HashMap<>();
    private final Map<String, SyncCoordinator.ClientConnection> connections = new HashMap<>();
    private final CompletableFuture<SyncCoordinator.SyncResult> completionFuture = new CompletableFuture<>();
    private String initiator;

    public SyncRound(String roundId, Set<String> expectedNodes) {
        this.roundId = roundId;
        this.expectedNodes = new HashSet<>(expectedNodes);
    }

    public String getRoundId(){ return roundId; }
    public Set<String> getExpectedNodes(){ return Collections.unmodifiableSet(expectedNodes); }

    public synchronized void registerConnection(String nodeId, SyncCoordinator.ClientConnection conn) {
        connections.put(nodeId, conn);
    }

    public synchronized void putBuffer(String nodeId, List<byte[]> envelopes) {
        buffers.put(nodeId, envelopes);
    }

    public synchronized boolean isComplete() {
        return buffers.keySet().containsAll(expectedNodes);
    }

    public synchronized Map<String, List<byte[]>> getBuffers() {
        return new HashMap<>(buffers);
    }

    public CompletableFuture<SyncCoordinator.SyncResult> getCompletionFuture() {
        return completionFuture;
    }

    public void complete(SyncCoordinator.SyncResult result) {
        completionFuture.complete(result);
    }

    public void setInitiator(String initiator) { this.initiator = initiator; }
    public String getInitiator() { return initiator; }
}
