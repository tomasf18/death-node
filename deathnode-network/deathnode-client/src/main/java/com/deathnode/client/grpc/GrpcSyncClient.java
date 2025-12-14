package com.deathnode.client.grpc;

import com.deathnode.client.config.Config;
import com.deathnode.client.service.DatabaseService;
import com.deathnode.common.grpc.*;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.util.HashUtils;
import com.google.gson.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client-side gRPC synchronization handler.
 * 
 * Manages the sync flow:
 * 1. Connect and send Hello
 * 2. Receive RequestBuffer from server
 * 3. Send BufferUpload with all pending envelopes
 * 4. Receive SyncResult with globally ordered envelopes
 * 5. Process and store received envelopes
 */
public class GrpcSyncClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcSyncClient.class);
    
    private final DatabaseService db;
    private final GrpcConnectionManager connectionManager;

    public GrpcSyncClient(DatabaseService db, String serverHost, int serverPort) {
        this.db = db;
        this.connectionManager = new GrpcConnectionManager(serverHost, serverPort);
    }

    /**
     * Synchronize pending envelopes with the server.
     * 
     * @param envelopeFilePaths List of paths to envelope files to send
     * @param timeoutSeconds Max seconds to wait for sync completion
     * @return true on success, false on error/timeout
     */
    public boolean syncPendingEnvelopes(List<String> envelopeFilePaths, int timeoutSeconds) {
        
        if (envelopeFilePaths.isEmpty()) {
            log.info("No envelopes to sync");
            return true;
        }

        log.info("Starting sync of {} envelopes", envelopeFilePaths.size());

        CountDownLatch completionLatch = new CountDownLatch(1);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // Get async stub
        SyncServiceGrpc.SyncServiceStub asyncStub = connectionManager.getAsyncStub();
        if (asyncStub == null) {
            log.error("gRPC stub not available");
            return false;
        }

        // Holder for request observer (needed to send messages)
        StreamObserver<ClientMessage>[] requestObserverHolder = new StreamObserver[1];

        // Create response handler
        StreamObserver<ServerMessage> responseObserver = new ServerResponseHandler(
                completionLatch, errors, envelopeFilePaths, requestObserverHolder);

        // Open bidirectional stream
        requestObserverHolder[0] = asyncStub.sync(responseObserver);

        // Send initial Hello
        Hello hello = Hello.newBuilder()
                .setNodeId(Config.NODE_SELF_ID)
                .setStartSync(true)
                .build();

        ClientMessage helloMsg = ClientMessage.newBuilder()
                .setHello(hello)
                .build();

        requestObserverHolder[0].onNext(helloMsg);
        log.info("Sent Hello to server");

        // Wait for completion or timeout
        boolean completed;
        try {
            completed = completionLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sync interrupted");
            return false;
        }

        if (!completed) {
            log.error("Sync timeout after {} seconds", timeoutSeconds);
            return false;
        }

        if (!errors.isEmpty()) {
            log.error("Sync completed with errors:");
            errors.forEach(e -> log.error("  - {}", e));
            return false;
        }

        log.info("Sync completed successfully");
        return true;
    }

    public void shutdown() {
        connectionManager.shutdown();
    }

    /**
     * Handles server responses during sync.
     */
    private class ServerResponseHandler implements StreamObserver<ServerMessage> {
        
        private final CountDownLatch completionLatch;
        private final List<String> errors;
        private final List<String> envelopeFilePaths;
        private final StreamObserver<ClientMessage>[] requestObserverHolder;

        public ServerResponseHandler(CountDownLatch completionLatch,
                                     List<String> errors,
                                     List<String> envelopeFilePaths,
                                     StreamObserver<ClientMessage>[] requestObserverHolder) {
            this.completionLatch = completionLatch;
            this.errors = errors;
            this.envelopeFilePaths = envelopeFilePaths;
            this.requestObserverHolder = requestObserverHolder;
        }

        @Override
        public void onNext(ServerMessage serverMessage) {
            try {
                if (serverMessage.hasRequestBuffer()) {
                    handleRequestBuffer(serverMessage.getRequestBuffer());
                } else if (serverMessage.hasSyncResult()) {
                    handleSyncResult(serverMessage.getSyncResult());
                } else if (serverMessage.hasAck()) {
                    log.info("Server ACK: {}", serverMessage.getAck().getMessage());
                } else if (serverMessage.hasError()) {
                    handleError(serverMessage.getError());
                } else {
                    log.warn("Unknown server message type");
                }
            } catch (Exception e) {
                log.error("Error handling server message: {}", e.getMessage(), e);
                errors.add("Error handling server message: " + e.getMessage());
                completionLatch.countDown();
            }
        }

        private void handleRequestBuffer(RequestBuffer request) {
            log.info("Server requested buffer for round: {}", request.getRoundId());

            try {
                // Build BufferUpload with all envelopes
                BufferUpload.Builder builder = BufferUpload.newBuilder()
                        .setNodeId(Config.NODE_SELF_ID);

                // Add each envelope file
                for (String pathStr : envelopeFilePaths) {
                    try {
                        Path path = Paths.get(pathStr);
                        byte[] envelopeBytes = Files.readAllBytes(path);
                        builder.addEnvelopes(ByteString.copyFrom(envelopeBytes));
                        log.debug("Added envelope: {}", path.getFileName());
                    } catch (Exception e) {
                        String msg = "Failed to read envelope " + pathStr + ": " + e.getMessage();
                        log.error(msg);
                        errors.add(msg);
                    }
                }

                // Add last known state (for future chain verification)
                try {
                    long lastSeq = db.getLastSequenceNumber(Config.NODE_SELF_ID);
                    String lastHash = db.getLastEnvelopeHash(Config.NODE_SELF_ID);
                    builder.setLastNodeSequence(lastSeq);
                    if (lastHash != null) {
                        builder.setLastEnvelopeHash(lastHash);
                    }
                } catch (Exception e) {
                    log.warn("Could not get last state: {}", e.getMessage());
                }

                // TODO: Add signed_buffer_root when implementing security

                ClientMessage msg = ClientMessage.newBuilder()
                        .setBufferUpload(builder.build())
                        .build();

                requestObserverHolder[0].onNext(msg);
                requestObserverHolder[0].onCompleted();

                log.info("Sent buffer with {} envelopes", builder.getEnvelopesCount());

            } catch (Exception e) {
                log.error("Failed to send buffer: {}", e.getMessage(), e);
                errors.add("Failed to send buffer: " + e.getMessage());
                completionLatch.countDown();
            }
        }

        private void handleSyncResult(SyncResult result) {
            log.info("Received SyncResult for round: {} ({} envelopes)", 
                    result.getRoundId(), result.getOrderedEnvelopesCount());

            try {
                // Process each ordered envelope
                for (int i = 0; i < result.getOrderedEnvelopesCount(); i++) {
                    byte[] envelopeBytes = result.getOrderedEnvelopes(i).toByteArray();
                    String hash = (i < result.getEnvelopeHashesCount()) 
                            ? result.getEnvelopeHashes(i)
                            : HashUtils.sha256Hex(envelopeBytes);

                    processReceivedEnvelope(envelopeBytes, hash);
                }

                // TODO: Verify signed_block_root when implementing security
                // TODO: Verify per_node_roots when implementing security
                // TODO: Verify prev_block_root chain when implementing security

                log.info("Successfully processed all envelopes from SyncResult");
                completionLatch.countDown();

            } catch (Exception e) {
                log.error("Failed to process SyncResult: {}", e.getMessage(), e);
                errors.add("Failed to process SyncResult: " + e.getMessage());
                completionLatch.countDown();
            }
        }

        private void processReceivedEnvelope(byte[] envelopeBytes, String hash) {
            try {
                // Store file (deterministic filename)
                String filename = hash + ".env";
                Path outDir = Paths.get(Config.ENVELOPES_DIR);
                Files.createDirectories(outDir);
                Path filePath = outDir.resolve(filename);

                // Check if already exists
                if (Files.exists(filePath)) {
                    // Verify content matches
                    byte[] existing = Files.readAllBytes(filePath);
                    String existingHash = HashUtils.sha256Hex(existing);
                    if (!existingHash.equals(hash)) {
                        String msg = "Hash mismatch for " + filename + 
                                " (expected=" + hash + ", found=" + existingHash + ")";
                        log.error(msg);
                        errors.add(msg);
                        return;
                    }
                    log.debug("Envelope already exists: {}", filename);
                } else {
                    // Write new file
                    Files.write(filePath, envelopeBytes, StandardOpenOption.CREATE_NEW);
                    log.debug("Stored envelope: {}", filename);
                }

                // Parse and insert/update DB
                String json = new String(envelopeBytes, StandardCharsets.UTF_8);
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                Envelope envelope = Envelope.fromJson(jsonObj);

                String signer = envelope.getMetadata().getSignerNodeId();
                long nodeSeq = envelope.getMetadata().getNodeSequenceNumber();
                String prevHash = envelope.getMetadata().getPrevEnvelopeHash();

                // Insert report (ignore if duplicate)
                try {
                    db.insertReport(hash, filePath.toString(), signer, nodeSeq, prevHash);
                } catch (java.sql.SQLException e) {
                    // Likely unique constraint - ignore
                    log.debug("Report {} already in DB", hash);
                }

                // Update node state
                try {
                    db.upsertNodeState(signer, nodeSeq, hash);
                } catch (Exception e) {
                    log.warn("Failed to update node state for {}: {}", signer, e.getMessage());
                }

            } catch (Exception e) {
                String msg = "Failed to process envelope " + hash + ": " + e.getMessage();
                log.error(msg);
                errors.add(msg);
            }
        }

        private void handleError(com.deathnode.common.grpc.Error error) {
            String msg = "Server error [" + error.getCode() + "]: " + error.getMessage();
            log.error(msg);
            errors.add(msg);
            completionLatch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            log.error("gRPC stream error: {}", t.getMessage(), t);
            errors.add("gRPC error: " + t.getMessage());
            completionLatch.countDown();
        }

        @Override
        public void onCompleted() {
            log.info("Server closed stream");
            completionLatch.countDown();
        }
    }
}