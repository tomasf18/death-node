package com.deathnode.server.service;

import com.deathnode.common.grpc.RequestBuffer;
import com.deathnode.common.grpc.SyncResult;
import com.deathnode.common.grpc.ServerMessage;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.common.util.HashUtils;
import com.deathnode.server.entity.Node;
import com.deathnode.server.entity.ReportEntity;
import com.deathnode.server.grpc.SyncRound;
import com.deathnode.server.repository.NodeRepository;
import com.deathnode.server.repository.ReportRepository;
import com.google.gson.*;
import io.grpc.stub.StreamObserver;
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
 */
@Service
public class SyncCoordinator {
    
    private final NodeRepository nodeRepository;
    private final ReportRepository reportRepository;
    private final FileStorageService fileStorageService;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<String, ClientConnection> allConnections = new HashMap<>();
    
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
     */
    public String startRoundIfAbsent(String initiatorNodeId) {
        synchronized (roundLock) {
            if (activeRound != null) {
                System.out.println("Reusing existing round: " + activeRound.getRoundId());
                return activeRound.getRoundId();
            }

            // Create new round with all known nodes as participants -> only the ones currently connected
            Set<String> expectedNodes = allConnections.keySet().stream().collect(Collectors.toSet());

            String roundId = UUID.randomUUID().toString();
            activeRound = new SyncRound(roundId, expectedNodes, initiatorNodeId);
            
            System.out.println("Started new sync round: " + roundId + " (initiator: " + initiatorNodeId + ", expected nodes: " + expectedNodes + ")");
            
            // BROADCAST RequestBuffer to ALL currently registered connections
            broadcastRequestBuffer(roundId);
            
            return roundId;
        }
    }

    /**
     * Broadcast RequestBuffer to all currently registered connections.
     */
    private void broadcastRequestBuffer(String roundId) {
        if (activeRound == null) return;

        RequestBuffer request = 
                RequestBuffer.newBuilder()
                .setRoundId(roundId)
                .setMessage("Sync round started - please send your buffer")
                .build();

        ServerMessage msg = 
                ServerMessage.newBuilder()
                .setRequestBuffer(request)
                .build();

        // Broadcast to all registered connections
        synchronized (roundLock) {
            for (ClientConnection conn : allConnections.values()) {
                try {
                    conn.observer.onNext(msg);
                    System.out.println("Sent RequestBuffer to node: " + conn.nodeId);
                } catch (Exception e) {
                    System.out.println("Failed to send RequestBuffer to " + conn.nodeId + ": " + e.getMessage());
                }
            }
        }
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
    public CompletableFuture<SyncResult> submitBuffer(String nodeId, List<byte[]> envelopes) {
        synchronized (roundLock) {
            if (activeRound == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No active round"));
            }

            System.out.println("Node " + nodeId + " submitted " + envelopes.size() + " envelopes to round " + activeRound.roundId);

            activeRound.putBuffer(nodeId, envelopes);

            // Check if round is complete (all expected nodes submitted)
            if (activeRound.isComplete()) {
                System.out.println("Round " + activeRound.roundId + " is complete - finalizing");
                
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
        System.out.println("Finalizing round: " + round.roundId);

        // 1. Collect all envelopes with metadata
        List<EnvelopeWithMeta> allEnvelopes = new ArrayList<>();
        
        for (Map.Entry<String, List<byte[]>> entry : round.getBuffers().entrySet()) {
            String nodeId = entry.getKey();
            Node signerNode = nodeRepository.findByNodeId(nodeId);
            
            if (signerNode == null) {
                System.out.println("Unknown node " + nodeId + " in round " + round.roundId + " - skipping");
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
                    // TODO: In future, reject entire round on parse failure
                }
            }
        }

        // 2. Sort by timestamp (tie-breakers: node_id, sequence)
        allEnvelopes.sort(Comparator
                .comparing((EnvelopeWithMeta e) -> e.timestamp)
                .thenComparing(e -> e.signerNode.getNodeId())
                .thenComparingLong(e -> e.envelope.getMetadata().getNodeSequenceNumber()));

        System.out.println("Ordered " + allEnvelopes.size() + " envelopes for round " + round.roundId);

        // 3. Persist each envelope to file and database
        List<byte[]> orderedBytes = new ArrayList<>();
        List<String> orderedHashes = new ArrayList<>();
        long nextGlobalSeq = computeNextGlobalSequence();

        for (EnvelopeWithMeta meta : allEnvelopes) {
            try {
                // Store file in node-specific directory
                String filename = meta.hash + ".json";
                Path filePath = fileStorageService.store(meta.envelopeBytes, filename, meta.signerNode.getNodeId());

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

                System.out.println("Persisted envelope: " + meta.hash + " (global_seq=" + entity.getGlobalSequenceNumber() + ")");

            } catch (Exception e) {
                System.out.println("Failed to persist envelope " + meta.hash + ": " + e.getMessage());
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

        System.out.println("Round " + round.roundId + " finalized with " + orderedBytes.size() + " envelopes");
        
        return result;
    }

    private long computeNextGlobalSequence() {
        Long max = reportRepository.findMaxGlobalSequenceNumber();
        return (max == null) ? 1L : max + 1L;
    }

    // ========== Helper Classes ==========

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