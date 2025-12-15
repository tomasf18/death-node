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
 * Lightweight round state holder.
 */
public class SyncRound {
    public final String roundId;
    public final Set<String> expectedNodes;
    public final String initiator;
    public final Map<String, List<byte[]>> buffers = new HashMap<>();
    public final CompletableFuture<SyncCoordinator.SyncResult> completionFuture = new CompletableFuture<>();

    public SyncRound(String roundId, Set<String> expectedNodes, String initiator) {
        this.roundId = roundId;
        this.expectedNodes = new HashSet<>(expectedNodes);
        this.initiator = initiator;
    }

    public String getRoundId(){ return roundId; }
    public Set<String> getExpectedNodes(){ return Collections.unmodifiableSet(expectedNodes); }
    public String getInitiator() { return initiator; }

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
}
