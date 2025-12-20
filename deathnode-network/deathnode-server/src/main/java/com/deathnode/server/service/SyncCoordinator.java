package com.deathnode.server.service;

import com.deathnode.tool.SecureDocumentProtocol;
import com.deathnode.tool.util.KeyLoader;
import com.deathnode.common.grpc.*;
import com.deathnode.common.grpc.RequestBuffer;
import com.deathnode.common.grpc.SyncResult;
import com.deathnode.common.grpc.Commit;
import com.deathnode.common.grpc.ServerMessage;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.common.util.HashUtils;
import com.deathnode.common.util.MerkleUtils;
import com.deathnode.server.entity.Node;
import com.deathnode.server.entity.NodeSyncState;
import com.deathnode.server.entity.ReportEntity;
import com.deathnode.server.entity.SignedBlockMerkleRoot;
import com.deathnode.server.grpc.SyncRound;
import com.deathnode.server.repository.NodeRepository;
import com.deathnode.server.repository.NodeSyncStateRepository;
import com.deathnode.server.repository.ReportRepository;
import com.deathnode.server.repository.SignedBlockMerkleRootRepository;
import com.google.gson.*;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Coordinates synchronization rounds between clients.
 */
@Service
public class SyncCoordinator {

    private final NodeRepository nodeRepository;
    private final ReportRepository reportRepository;
    private final NodeSyncStateRepository nodeSyncStateRepository;
    private final SignedBlockMerkleRootRepository signedBlockMerkleRootRepository;
    private final FileStorageService fileStorageService;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<String, ClientConnection> allConnections = new HashMap<>();
    private final Object roundLock = new Object();
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);
    // configuration injected from application.yaml
    @Value("${keystore_password}")
    private String keystorePassword;
    @Value("${ed_private_key_alias}")
    private String edPrivateKeyAlias;
    @Value("${rsa_private_key_alias}")
    private String rsaPrivateKeyAlias;
    @Value("${keystore_path}")
    private String keystorePath;
    @Value("${sync.timeout-ms:5000}")
    private long syncTimeoutMs;
    private SyncRound activeRound = null;
    private PendingRound pendingRound = null;

    public SyncCoordinator(NodeRepository nodeRepository,
                           ReportRepository reportRepository,
                           FileStorageService fileStorageService,
                           NodeSyncStateRepository nodeSyncStateRepository,
                           SignedBlockMerkleRootRepository signedBlockMerkleRootRepository) {
        this.nodeRepository = nodeRepository;
        this.reportRepository = reportRepository;
        this.fileStorageService = fileStorageService;
        this.nodeSyncStateRepository = nodeSyncStateRepository;
        this.signedBlockMerkleRootRepository = signedBlockMerkleRootRepository;
    }

    /**
     * Start a sync round if none exists, or return existing round ID.
     */
    public String startRoundIfAbsent(String initiatorNodeId) {
        synchronized (roundLock) {
            if (activeRound != null) {
                System.out.println("Reusing existing round: " + activeRound.getRoundId());
                return activeRound.getRoundId();
            }

            // Create new round with all known nodes as participants -> only the ones
            // currently connected
            Set<String> expectedNodes = allConnections.keySet().stream().collect(Collectors.toSet());

            String roundId = UUID.randomUUID().toString();
            activeRound = new SyncRound(roundId, expectedNodes, initiatorNodeId);

            System.out.println("Started new sync round: " + roundId + " (initiator: " + initiatorNodeId
                    + ", expected nodes: " + expectedNodes + ")");

            broadcastRequestBuffer(roundId);
            scheduleRoundTimeout(roundId, activeRound);

            return roundId;
        }
    }

    /**
     * Schedule a timeout task for the current round.
     * If not all nodes submit within the timeout period, remove unresponsive nodes
     * and finalize the round with remaining nodes.
     */
    private void scheduleRoundTimeout(String roundId, SyncRound round) {
        timeoutExecutor.schedule(() -> {
            synchronized (roundLock) {
                if (activeRound != round) {
                    System.out.println("Timeout for round " + roundId + " - but a newer round is active, ignoring");
                    return;
                }

                Set<String> unsubmitted = round.getUnsubmittedNodes();
                if (unsubmitted.isEmpty()) {
                    System.out.println("Timeout for round " + roundId + " - but all nodes have submitted, ignoring");
                    return;
                }

                System.out.println("TIMEOUT: Round " + roundId + " did not receive buffers from nodes: " + unsubmitted);

                for (String nodeId : unsubmitted) {
                    System.out.println("  Removing node " + nodeId + " from round " + roundId);
                    round.removeExpectedNode(nodeId);
                }

                if (round.getExpectedNodes().isEmpty()) {
                    System.out.println("Round " + roundId + " has no remaining nodes, aborting");
                    activeRound = null;
                    round.getCompletionFuture().completeExceptionally(
                            new RuntimeException("Round aborted: no nodes submitted buffer"));
                } else if (round.isComplete()) {
                    System.out.println("Round " + roundId + " is now complete after timeout - finalizing");
                    SyncRound completedRound = activeRound;
                    activeRound = null;
                    CompletableFuture.supplyAsync(() -> finalizeRound(completedRound));
                } else {
                    System.out.println("Round " + roundId + " still waiting for nodes after timeout: " + round.getUnsubmittedNodes());
                }
            }
        }, syncTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Broadcast RequestBuffer to all currently registered connections.
     */
    private void broadcastRequestBuffer(String roundId) {
        if (activeRound == null)
            return;

        RequestBuffer request = RequestBuffer.newBuilder()
                .setRoundId(roundId)
                .setMessage("Sync round started - please send your buffer")
                .build();

        ServerMessage msg = ServerMessage.newBuilder()
                .setRequestBuffer(request)
                .build();

        // Broadcast to all registered connections
        broadcast(msg);
    }

    /**
     * Register a client connection.
     */
    public synchronized void registerClient(String nodeId, ClientConnection connection) {
        allConnections.put(nodeId, connection);
        System.out.println("Registered client connection: " + nodeId);
    }

    /**
     * Unregister a client connection.
     */
    public synchronized void unregisterClient(String nodeId) {
        allConnections.remove(nodeId);
        System.out.println("Unregistered client connection: " + nodeId);
    }

    /**
     * Submit a node's buffer to the active round.
     * Returns a future that completes when the round is finalized.
     */
    public CompletableFuture<SyncResult> submitBufferAndRoot(String nodeId, List<byte[]> envelopes, byte[] bufferRoot, byte[] signedBufferRoot) {
        synchronized (roundLock) {
            if (activeRound == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No active round"));
            }

            System.out.println("Node " + nodeId + " submitted " + envelopes.size() + " envelopes to round "
                    + activeRound.getRoundId());

            activeRound.putBuffer(nodeId, envelopes);
            activeRound.putNodeSignedBufferRoot(nodeId, HashUtils.bytesToHex(bufferRoot), HashUtils.bytesToHex(signedBufferRoot));

            // Check if round is complete (all expected nodes submitted)
            if (activeRound.isComplete()) {
                System.out.println("Round " + activeRound.getRoundId() + " is complete - finalizing");

                // Finalize asynchronously
                SyncRound round = activeRound;
                activeRound = null; // Clear active round

                return CompletableFuture.supplyAsync(() -> finalizeRound(round));
            } else {
                // Return the round's completion future
                return activeRound.getCompletionFuture();
            }
        }
    }

    /**
     * Finalize a round: order envelopes and return result.
     * <p>
     * For now: Simple timestamp-based ordering, no security checks.
     */
    @Transactional
    protected SyncResult finalizeRound(SyncRound round) {
        System.out.println("Finalizing round: " + round.getRoundId());

        // 1. Collect all envelopes with metadata
        List<EnvelopeWithMeta> allEnvelopes = new ArrayList<>();
        Map<String, List<byte[]>> buffers = round.getBuffers();

        if (areEmpty(buffers.values())) return null;

        for (Map.Entry<String, List<byte[]>> entry : buffers.entrySet()) {
            String nodeId = entry.getKey();
            Node signerNode = nodeRepository.findByNodeId(nodeId);

            if (signerNode == null) {
                System.out.println("Unknown node " + nodeId + " in round " + round.getRoundId() + " - skipping");
                continue;
            }

            for (byte[] envelopeBytes : entry.getValue()) {
                try {
                    // Parse envelope
                    String json = new String(envelopeBytes, StandardCharsets.UTF_8);
                    JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                    Envelope envelope = Envelope.fromJson(jsonObj);

                    // Extract timestamp for ordering
                    Metadata metadata = envelope.getMetadata();
                    String tsStr = metadata.getMetadataTimestamp();
                    Instant timestamp = Instant.parse(tsStr);

                    String hash = HashUtils.sha256Hex(envelopeBytes);

                    allEnvelopes.add(new EnvelopeWithMeta(signerNode, envelopeBytes, hash, envelope, timestamp));

                } catch (Exception e) {
                    System.out.println("Failed to parse envelope from " + nodeId + ": " + e.getMessage());
                }
            }
        }

        // 2. Sort by timestamp (tie-breakers: node sequence number, node ID)
        allEnvelopes.sort(Comparator
                .comparing((EnvelopeWithMeta e) -> e.timestamp)
                .thenComparingLong(e -> e.envelope.getMetadata().getNodeSequenceNumber())
                .thenComparing(e -> e.signerNode.getNodeId()));

        System.out.println("Ordered " + allEnvelopes.size() + " envelopes for round " + round.getRoundId());

        List<byte[]> orderedBytes = new ArrayList<>();
        for (EnvelopeWithMeta envelope : allEnvelopes) {
            orderedBytes.add(envelope.envelopeBytes);
        }

        // 3. Build result
        SyncResult result = new SyncResult();
        result.setRoundId(round.getRoundId());
        result.setOrderedEnvelopes(orderedBytes);
        byte[] blockRoot = MerkleUtils.computeMerkleRoot(orderedBytes);
        try {
            PrivateKey serverSigningKey = KeyLoader.loadPrivateKeyFromKeystore(edPrivateKeyAlias, keystorePath, keystorePassword);
            byte[] signedBlockRoot = SecureDocumentProtocol.signData(blockRoot, serverSigningKey);
            result.setBlockRoot(blockRoot);
            result.setSignedBlockRoot(signedBlockRoot);
        } catch (Exception e) {
            System.out.println("Failed to load server signing key: " + e.getMessage());
            throw new RuntimeException("Server signing key load failure", e);
        }

        SignedBlockMerkleRoot lastSignedBlockMerkleRoot = signedBlockMerkleRootRepository.findByHighestBlockNumber().orElse(null);
        long prevBlockNumber = 0;
        byte[] prevBlockRoot = null;
        if (lastSignedBlockMerkleRoot != null) {
            prevBlockNumber = lastSignedBlockMerkleRoot.getBlockNumber();
            prevBlockRoot = HashUtils.hexToBytes(lastSignedBlockMerkleRoot.getBlockRoot());
        }

        result.setBlockNumber(prevBlockNumber + 1);
        result.setPrevBlockRoot(prevBlockRoot);
        result.setPerNodeSignedBufferRoots(round.getPerNodeSignedBufferRoots());

        SignedBlockMerkleRoot newSignedBlockMerkleRoot = new SignedBlockMerkleRoot(
                prevBlockNumber + 1,
                HashUtils.bytesToHex(blockRoot),
                (prevBlockRoot != null) ? HashUtils.bytesToHex(prevBlockRoot) : null
        );
        pendingRound = new PendingRound(allEnvelopes, newSignedBlockMerkleRoot, allConnections.size());

        // 4. Complete the round's future
        round.getCompletionFuture().complete(result);
        return result;
    }

    /**
     * Collect acks from clients
     */
    public void receivePeerAck(boolean accepted) {
        if (pendingRound == null)
            return;
        if (!accepted) {
            System.out.println("[!] Block nacked. Clearing pending round...");
            pendingRound = null;
            Ack commit = Ack.newBuilder()
                    .setMessage("commit " + pendingRound.getRoot().getBlockRoot())
                    .setSuccess(false)
                    .build();

            ServerMessage msg = ServerMessage.newBuilder()
                    .setAck(commit)
                    .build();

            broadcast(msg);
            return;
        }

        if (pendingRound.isAcked()) {
            persistAll();

            Ack commit = Ack.newBuilder()
                    .setMessage("commit " + pendingRound.getRoot().getBlockRoot())
                    .setSuccess(true)
                    .build();

            ServerMessage msg = ServerMessage.newBuilder()
                    .setAck(commit)
                    .build();

            broadcast(msg);
        }
    }

    private void broadcast(ServerMessage msg) {
        synchronized (roundLock) {
            for (ClientConnection conn : allConnections.values()) {
                try {
                    conn.observer.onNext(msg);
                    System.out.println("Sent message to node: " + conn.nodeId);
                } catch (Exception e) {
                    System.out.println("Failed to send message to " + conn.nodeId + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Persist pending round objects.
     */
    private void persistAll() {
        long nextGlobalSeq = computeNextGlobalSequence();

        List<EnvelopeWithMeta> envelopes = pendingRound.getEnvelopes();
        SignedBlockMerkleRoot root = pendingRound.getRoot();

        for (EnvelopeWithMeta meta : envelopes) {
            try {
                // Store file in node-specific directory
                String filename = meta.hash + ".json";
                Path filePath = fileStorageService.store(meta.envelopeBytes, filename, meta.signerNode.getNodeId());

                // Update or create NodeSyncState (upsert)
                NodeSyncState syncState = nodeSyncStateRepository.findByNodeId(meta.signerNode.getNodeId());
                if (syncState == null) {
                    syncState = new NodeSyncState();
                    syncState.setNode(meta.signerNode);
                    syncState.setNodeId(meta.signerNode.getNodeId());
                }
                syncState.setLastSequenceNumber(meta.envelope.getMetadata().getNodeSequenceNumber());
                syncState.setLastEnvelopeHash(meta.hash);

                // Create DB entity
                ReportEntity entity = new ReportEntity();
                entity.setEnvelopeHash(meta.hash);
                entity.setSignerNode(meta.signerNode);
                entity.setNodeSequenceNumber(meta.envelope.getMetadata().getNodeSequenceNumber());
                entity.setGlobalSequenceNumber(nextGlobalSeq++);
                entity.setMetadataTimestamp(OffsetDateTime.ofInstant(meta.timestamp, ZoneOffset.UTC));
                entity.setPrevReportHash(meta.envelope.getMetadata().getPrevEnvelopeHash());
                entity.setFilePath(filePath.toString());

                reportRepository.save(entity);
                nodeSyncStateRepository.save(syncState);

                System.out.println(
                        "Persisted envelope: " + meta.hash + " (global_seq=" + entity.getGlobalSequenceNumber() + ")");

                signedBlockMerkleRootRepository.save(root);

            } catch (Exception e) {
                System.out.println("Failed to persist envelope " + meta.hash + ": " + e.getMessage());
            }
        }
    }

    public boolean verifyEnvelopeChain(Node node, List<Envelope> envelopes) {
        NodeSyncState syncState = nodeSyncStateRepository.findByNodeId(node.getNodeId());
        String lastHash = (syncState != null) ? syncState.getLastEnvelopeHash() : null;
        Long lastSeqObj = (syncState != null) ? syncState.getLastSequenceNumber() : null;
        long expectedSeq = (lastSeqObj != null) ? lastSeqObj + 1 : 1L;

        for (Envelope env : envelopes) {
            Metadata meta = env.getMetadata();

            // Check sequence envelope chain
            if (meta.getNodeSequenceNumber() != expectedSeq) {
                System.out.println(" -> Envelope chain check failed for node " + node.getNodeId() +
                        ": expected seq " + expectedSeq + ", got " + meta.getNodeSequenceNumber());
                return false;
            }

            // Check previous hash envelope chain
            if (lastHash != null && !lastHash.equals(meta.getPrevEnvelopeHash())) {
                System.out.println(" -> Envelope chain check failed for node " + node.getNodeId() +
                        ": expected prev hash " + lastHash + ", got " + meta.getPrevEnvelopeHash());
                return false;
            }

            // Update for next envelope
            lastHash = env.computeHashHex();
            expectedSeq++;
        }

        return true;
    }

    public VerificationsResult performAllVerifications(String bufferNodeId, String expectedNodeId, List<byte[]> envelopes, byte[] bufferRoot, byte[] signedBufferRoot) {
        if (!bufferNodeId.equals(expectedNodeId)) {
            System.out.println("Node ID mismatch: stream=" + expectedNodeId + ", upload=" + bufferNodeId);
            return new VerificationsResult(false, "NODE_ID_MISMATCH", "Node ID in upload doesn't match connection");
        }

        Node node = nodeRepository.findByNodeId(bufferNodeId);

        try {
            PublicKey signPubKey = KeyLoader.pemStringToPublicKey(node.getSignPubKey(), "Ed25519");

            if (!SecureDocumentProtocol.verifySignature(bufferRoot, signedBufferRoot, signPubKey)) {
                System.out.println("Invalid buffer signature from node " + bufferNodeId);
                return new VerificationsResult(false, "INVALID_SIGNATURE", "Buffer signature verification failed");
            }

            System.out.println(" -> Buffer signature verified for node " + bufferNodeId);

            if (!MerkleUtils.verifyMerkleRoot(envelopes, bufferRoot)) {
                System.out.println("Buffer Merkle root mismatch from node " + bufferNodeId);
                return new VerificationsResult(false, "INVALID_MERKLE_ROOT", "Buffer Merkle root verification failed");
            }

            System.out.println(" -> Buffer Merkle root verified for node " + bufferNodeId);

            List<Envelope> envelopesObjs = new ArrayList<>();
            for (byte[] envBytes : envelopes) {
                envelopesObjs.add(Envelope.fromBytes(envBytes));
            }

            if (!verifyEnvelopeChain(node, envelopesObjs)) {
                System.out.println("Envelope chain verification failed for node " + bufferNodeId);
                return new VerificationsResult(false, "INVALID_ENVELOPE_CHAIN", "Envelope chain verification failed");
            }

            System.out.println(" -> Envelope chain verified for node " + bufferNodeId);

        } catch (Exception e) {
            System.out.println("Error during verifications for node " + bufferNodeId + ": " + e.getMessage());
            return new VerificationsResult(false, "VERIFICATION_ERROR", "Error during verifications: " + e.getMessage());
        }

        System.out.println(" -> All verifications passed for node " + bufferNodeId);
        return new VerificationsResult(true, null, null);
    }

    private long computeNextGlobalSequence() {
        Long max = reportRepository.findMaxGlobalSequenceNumber();
        return (max == null) ? 1L : max + 1L;
    }

    private boolean areEmpty(Collection<List<byte[]>> collection) {
        for (List<byte[]> list : collection) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    // ========== Helper Classes ==========

    private static class PendingRound {
        private final List<EnvelopeWithMeta> envelopes;
        private final SignedBlockMerkleRoot root;
        private int acksLeft;

        public PendingRound(List<EnvelopeWithMeta> envelopes, SignedBlockMerkleRoot root, int acksNeeded) {
            this.envelopes = envelopes;
            this.root = root;
            this.acksLeft = acksNeeded;
        }

        public List<EnvelopeWithMeta> getEnvelopes() {
            return envelopes;
        }

        public SignedBlockMerkleRoot getRoot() {
            return root;
        }

        public boolean isAcked() {
            return --acksLeft == 0;
        }

    }

    /**
     * Result of a completed sync round.
     */
    public static class SyncResult { // this message will be protected using gRPC with TLS
        private String roundId;
        private List<byte[]> orderedEnvelopes;
        private long blockNumber;
        private byte[] blockRoot;
        private byte[] signedBlockRoot;
        private List<SyncRound.PerNodeSignedBufferRoots> perNodeSignedBufferRoots = new ArrayList<>();
        private byte[] prevBlockRoot;

        public String getRoundId() {
            return roundId;
        }

        public void setRoundId(String roundId) {
            this.roundId = roundId;
        }

        public List<byte[]> getOrderedEnvelopes() {
            return orderedEnvelopes;
        }

        public void setOrderedEnvelopes(List<byte[]> orderedEnvelopes) {
            this.orderedEnvelopes = orderedEnvelopes;
        }

        public long getBlockNumber() {
            return blockNumber;
        }

        public void setBlockNumber(long blockNumber) {
            this.blockNumber = blockNumber;
        }

        public byte[] getBlockRoot() {
            return blockRoot;
        }

        public void setBlockRoot(byte[] blockRoot) {
            this.blockRoot = blockRoot;
        }

        public byte[] getSignedBlockRoot() {
            return signedBlockRoot;
        }

        public void setSignedBlockRoot(byte[] signedBlockRoot) {
            this.signedBlockRoot = signedBlockRoot;
        }

        public List<SyncRound.PerNodeSignedBufferRoots> getPerNodeSignedBufferRoots() {
            return perNodeSignedBufferRoots;
        }

        public void setPerNodeSignedBufferRoots(List<SyncRound.PerNodeSignedBufferRoots> perNodeSignedBufferRoots) {
            this.perNodeSignedBufferRoots = perNodeSignedBufferRoots;
        }

        public byte[] getPrevBlockRoot() {
            return prevBlockRoot;
        }

        public void setPrevBlockRoot(byte[] prevBlockRoot) {
            this.prevBlockRoot = prevBlockRoot;
        }
    }

    /**
     * Wrapper for client gRPC connection.
     */
    public static class ClientConnection {
        public final String nodeId;
        public final StreamObserver<ServerMessage> observer;

        public ClientConnection(String nodeId, StreamObserver<ServerMessage> observer) {
            this.nodeId = nodeId;
            this.observer = observer;
        }
    }

    /**
     * Internal: envelope with parsed metadata.
     */
    private static class EnvelopeWithMeta {
        final Node signerNode;
        final byte[] envelopeBytes;
        final String hash;
        final Envelope envelope;
        final Instant timestamp;

        EnvelopeWithMeta(Node signerNode, byte[] envelopeBytes, String hash,
                         Envelope envelope, Instant timestamp) {
            this.signerNode = signerNode;
            this.envelopeBytes = envelopeBytes;
            this.hash = hash;
            this.envelope = envelope;
            this.timestamp = timestamp;
        }
    }

    public static class VerificationsResult {
        private boolean success;
        private String errorCode;
        private String errorMessage;

        public VerificationsResult(boolean success, String errorCode, String errorMessage) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}