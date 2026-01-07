package com.deathnode.server.grpc;

import java.util.ArrayList;
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
    private String roundId;
    private Set<String> expectedNodes;
    private String initiator;
    private Map<String, List<byte[]>> buffers = new HashMap<>();
    private List<PerNodeSignedBufferRoots> perNodeSignedBufferRoots = new ArrayList<>(); 
    private CompletableFuture<SyncCoordinator.SyncResultObject> completionFuture = new CompletableFuture<>();

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

    public synchronized void putNodeSignedBufferRoot(String nodeId, String bufferRoot, String signedBufferRoot) {
        perNodeSignedBufferRoots.add(new PerNodeSignedBufferRoots(nodeId, bufferRoot, signedBufferRoot));
    }

    public synchronized List<PerNodeSignedBufferRoots> getPerNodeSignedBufferRoots() {
        return new ArrayList<>(perNodeSignedBufferRoots);
    }

    public synchronized boolean isComplete() {
        return buffers.keySet().containsAll(expectedNodes);
    }

    public synchronized boolean removeExpectedNode(String nodeId) {
        boolean removed = expectedNodes.remove(nodeId);
        return removed && isComplete();
    }

    public synchronized Set<String> getUnsubmittedNodes() {
        Set<String> unsubmitted = new HashSet<>(expectedNodes);
        unsubmitted.removeAll(buffers.keySet());
        return unsubmitted;
    }

    public synchronized Map<String, List<byte[]>> getBuffers() {
        return new HashMap<>(buffers);
    }

    public CompletableFuture<SyncCoordinator.SyncResultObject> getCompletionFuture() {
        return completionFuture;
    }

    public void complete(SyncCoordinator.SyncResultObject result) {
        completionFuture.complete(result);
    }

    public class PerNodeSignedBufferRoots {
        private String nodeId;
        private String bufferRoot;
        private String signedBufferRoot;

        public PerNodeSignedBufferRoots(String nodeId, String bufferRoot, String signedBufferRoot) {
            this.nodeId = nodeId;
            this.bufferRoot = bufferRoot;
            this.signedBufferRoot = signedBufferRoot;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getBufferRoot() {
            return bufferRoot;
        }

        public String getSignedBufferRoot() {
            return signedBufferRoot;
        }
    }
}
