package com.deathnode.server.service;

import com.deathnode.common.grpc.ServerMessage;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.util.HashUtils;
import com.deathnode.server.entity.Node;
import com.deathnode.server.entity.ReportEntity;
import com.deathnode.server.repository.NodeRepository;
import com.deathnode.server.repository.ReportRepository;
import com.google.gson.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Coordinates synchronization rounds between clients.
 * 
 * Simplified version for now:
 * - Single active round at a time
 * - Simple timestamp-based ordering
 * - No security verifications yet (Merkle roots, signatures, etc.)
 * - Focus on getting the sync flow working
 */
@Service
public class SyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SyncCoordinator.class);
    
    private final NodeRepository nodeRepository;
    private final ReportRepository reportRepository;
    private final FileStorageService fileStorageService;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    // Active round state (simplified - single round for now)
    private SyncRound activeRound = null;
    private final Object roundLock = new Object();

    public SyncCoordinator(NodeRepository nodeRepository,
                          ReportRepository reportRepository,
                          FileStorageService fileStorageService) {
        this.nodeRepository = nodeRepository;
        this.reportRepository = reportRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Start a sync round if none exists, or return existing round ID.
     * If a NEW round is created, broadcasts RequestBuffer to all connected nodes.
     */
    public String startRoundIfAbsent(String initiatorNodeId) {
        synchronized (roundLock) {
            if (activeRound != null) {
                log.info("Reusing existing round: {}", activeRound.roundId);
                return activeRound.roundId;
            }

            // Create new round with all known nodes as participants
            Set<String> expectedNodes = nodeRepository.findAll().stream()
                    .map(Node::getNodeId)
                    .collect(Collectors.toSet());

            String roundId = UUID.randomUUID().toString();
            activeRound = new SyncRound(roundId, expectedNodes, initiatorNodeId);
            
            log.info("Started new sync round: {} (initiator: {}, expected nodes: {})", 
                    roundId, initiatorNodeId, expectedNodes);
            
            // BROADCAST RequestBuffer to ALL connected nodes (including initiator)
            broadcastRequestBuffer(roundId);
            
            return roundId;
        }
    }

    /**
     * Broadcast RequestBuffer to all currently connected nodes.
     */
    private void broadcastRequestBuffer(String roundId) {
        if (activeRound == null) return;

        com.deathnode.common.grpc.RequestBuffer request = 
                com.deathnode.common.grpc.RequestBuffer.newBuilder()
                .setRoundId(roundId)
                .setMessage("Sync round started - please send your buffer")
                .build();

        com.deathnode.common.grpc.ServerMessage msg = 
                com.deathnode.common.grpc.ServerMessage.newBuilder()
                .setRequestBuffer(request)
                .build();

        synchronized (roundLock) {
            for (ClientConnection conn : activeRound.connections.values()) {
                try {
                    conn.observer.onNext(msg);
                    log.info("Sent RequestBuffer to node: {}", conn.nodeId);
                } catch (Exception e) {
                    log.error("Failed to send RequestBuffer to {}: {}", 
                            conn.nodeId, e.getMessage());
                }
            }
        }
    }

    /**
     * Register a client connection for the active round.
     */
    public void registerClient(String nodeId, ClientConnection connection) {
        synchronized (roundLock) {
            if (activeRound == null) {
                log.warn("Cannot register client {} - no active round", nodeId);
                return;
            }
            activeRound.registerConnection(nodeId, connection);
            log.info("Registered client {} for round {}", nodeId, activeRound.roundId);
        }
    }

    /**
     * Unregister a client connection.
     */
    public void unregisterClient(String nodeId) {
        synchronized (roundLock) {
            if (activeRound != null) {
                activeRound.unregisterConnection(nodeId);
                log.info("Unregistered client {} from round {}", nodeId, activeRound.roundId);
            }
        }
    }

    /**
     * Submit a node's buffer to the active round.
     * Returns a future that completes when the round is finalized.
     */
    public CompletableFuture<SyncResult> submitBuffer(String nodeId, List<byte[]> envelopes) {
        synchronized (roundLock) {
            if (activeRound == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No active round"));
            }

            log.info("Node {} submitted {} envelopes to round {}", 
                    nodeId, envelopes.size(), activeRound.roundId);

            activeRound.putBuffer(nodeId, envelopes);

            // Check if round is complete (all expected nodes submitted)
            if (activeRound.isComplete()) {
                log.info("Round {} is complete - finalizing", activeRound.roundId);
                
                // Finalize asynchronously
                SyncRound round = activeRound;
                activeRound = null; // Clear active round
                
                return CompletableFuture.supplyAsync(() -> finalizeRound(round));
            } else {
                // Return the round's completion future
                return activeRound.completionFuture;
            }
        }
    }

    /**
     * Finalize a round: order envelopes, persist, and return result.
     * 
     * For now: Simple timestamp-based ordering, no security checks.
     */
    @Transactional
    protected SyncResult finalizeRound(SyncRound round) {
        log.info("Finalizing round: {}", round.roundId);

        // 1. Collect all envelopes with metadata
        List<EnvelopeWithMeta> allEnvelopes = new ArrayList<>();
        
        for (Map.Entry<String, List<byte[]>> entry : round.getBuffers().entrySet()) {
            String nodeId = entry.getKey();
            Node signerNode = nodeRepository.findByNodeId(nodeId);
            
            if (signerNode == null) {
                log.warn("Unknown node {} in round {} - skipping", nodeId, round.roundId);
                continue;
            }

            for (byte[] envelopeBytes : entry.getValue()) {
                try {
                    // Parse envelope
                    String json = new String(envelopeBytes, StandardCharsets.UTF_8);
                    JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                    Envelope envelope = Envelope.fromJson(jsonObj);
                    
                    // Extract timestamp for ordering
                    String tsStr = envelope.getMetadata().getMetadataTimestamp();
                    Instant timestamp = Instant.parse(tsStr);
                    
                    String hash = HashUtils.sha256Hex(envelopeBytes);
                    
                    allEnvelopes.add(new EnvelopeWithMeta(
                            signerNode, envelopeBytes, hash, envelope, timestamp));
                    
                } catch (Exception e) {
                    log.error("Failed to parse envelope from {}: {}", nodeId, e.getMessage(), e);
                    // TODO: In future, reject entire round on parse failure
                }
            }
        }

        // 2. Sort by timestamp (tie-breakers: node_id, sequence)
        allEnvelopes.sort(Comparator
                .comparing((EnvelopeWithMeta e) -> e.timestamp)
                .thenComparing(e -> e.signerNode.getNodeId())
                .thenComparingLong(e -> e.envelope.getMetadata().getNodeSequenceNumber()));

        log.info("Ordered {} envelopes for round {}", allEnvelopes.size(), round.roundId);

        // 3. Persist each envelope to file and database
        List<byte[]> orderedBytes = new ArrayList<>();
        List<String> orderedHashes = new ArrayList<>();
        long nextGlobalSeq = computeNextGlobalSequence();

        for (EnvelopeWithMeta meta : allEnvelopes) {
            try {
                // Store file
                String filename = meta.hash + ".env";
                Path filePath = fileStorageService.store(meta.envelopeBytes, filename);

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

                orderedBytes.add(meta.envelopeBytes);
                orderedHashes.add(meta.hash);

                log.debug("Persisted envelope: {} (global_seq={})", meta.hash, entity.getGlobalSequenceNumber());

            } catch (Exception e) {
                log.error("Failed to persist envelope {}: {}", meta.hash, e.getMessage(), e);
                // TODO: In future, rollback entire round on persistence failure
            }
        }

        // 4. Build result
        SyncResult result = new SyncResult();
        result.roundId = round.roundId;
        result.orderedEnvelopes = orderedBytes;
        result.envelopeHashes = orderedHashes;

        // 5. Complete the round's future
        round.completionFuture.complete(result);

        log.info("Round {} finalized with {} envelopes", round.roundId, orderedBytes.size());
        
        return result;
    }

    private long computeNextGlobalSequence() {
        Long max = reportRepository.findMaxGlobalSequenceNumber();
        return (max == null) ? 1L : max + 1L;
    }

    // ========== Helper Classes ==========

    /**
     * Represents an active synchronization round.
     */
    public static class SyncRound {
        final String roundId;
        final Set<String> expectedNodes;
        final String initiator;
        final Map<String, List<byte[]>> buffers = new HashMap<>();
        final Map<String, ClientConnection> connections = new HashMap<>();
        final CompletableFuture<SyncResult> completionFuture = new CompletableFuture<>();

        public SyncRound(String roundId, Set<String> expectedNodes, String initiator) {
            this.roundId = roundId;
            this.expectedNodes = new HashSet<>(expectedNodes);
            this.initiator = initiator;
        }

        public synchronized void registerConnection(String nodeId, ClientConnection conn) {
            connections.put(nodeId, conn);
        }

        public synchronized void unregisterConnection(String nodeId) {
            connections.remove(nodeId);
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
    }

    /**
     * Result of a completed sync round.
     */
    public static class SyncResult {
        public String roundId;
        public List<byte[]> orderedEnvelopes;
        public List<String> envelopeHashes;
        
        // Future fields for security:
        // public byte[] signedBlockRoot;
        // public Map<String, byte[]> perNodeSignedRoots;
        // public String prevBlockRoot;
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
}