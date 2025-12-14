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
import java.util.concurrent.*;

/**
 * Persistent gRPC client that maintains an open connection to the server.
 * 
 * This allows the server to push RequestBuffer messages at any time,
 * enabling coordinated sync rounds across all nodes.
 */
public class PersistentSyncClient {
    
    private static final Logger log = LoggerFactory.getLogger(PersistentSyncClient.class);
    private final DatabaseService db;
    private final GrpcConnectionManager connectionManager;
    private final List<String> pendingEnvelopes = new CopyOnWriteArrayList<>();
    
    private StreamObserver<ClientMessage> requestObserver;
    private volatile boolean connected = false;
    private volatile String currentRoundId = null;

    public PersistentSyncClient(DatabaseService db, String serverHost, int serverPort) {
        this.db = db;
        this.connectionManager = new GrpcConnectionManager(serverHost, serverPort);
    }

    /**
     * Connect to server and maintain persistent connection.
     * 
     * @param startSync If true, this client initiates a sync round
     */
    public void connect() {
        if (connected) {
            System.out.println("Already connected");
            return;
        }

        System.out.println("Connecting to server...");

        SyncServiceGrpc.SyncServiceStub asyncStub = connectionManager.getAsyncStub();
        
        // Open bidirectional stream
        requestObserver = asyncStub.sync(new ServerResponseHandler());

        // Send Hello
        Hello hello = Hello.newBuilder()
                .setNodeId(Config.NODE_SELF_ID)
                .setStartSync(false)
                .build();

        ClientMessage helloMsg = ClientMessage.newBuilder()
                .setHello(hello)
                .build();

        requestObserver.onNext(helloMsg);
        connected = true;

        System.out.println("Connected to server");
    }

    /**
     * Add envelope file path to pending buffer.
     */
    public void addPendingEnvelope(String envelopeFilePath) {
        pendingEnvelopes.add(envelopeFilePath);
        log.debug("Added envelope to buffer: {} (total: {})", 
                Paths.get(envelopeFilePath).getFileName(), pendingEnvelopes.size());
    }

    /**
     * Get current pending buffer size.
     */
    public int getPendingCount() {
        return pendingEnvelopes.size();
    }

    /**
     * Trigger a sync round (only if this node wants to initiate).
     */
    public void triggerSync() {
        if (!connected) {
            System.out.println("Not connected - cannot trigger sync");
            return;
        }

        if (currentRoundId != null) {
            System.out.println("Sync already in progress (round: " + currentRoundId + ")");
            return;
        }

        System.out.println("Triggering sync round...");

        Hello hello = Hello.newBuilder()
                .setNodeId(Config.NODE_SELF_ID)
                .setStartSync(true)
                .build();

        ClientMessage helloMsg = ClientMessage.newBuilder()
                .setHello(hello)
                .build();

        requestObserver.onNext(helloMsg);
    }

    /**
     * Disconnect from server.
     */
    public void disconnect() {
        if (requestObserver != null && connected) {
            try {
                requestObserver.onCompleted();
            } catch (Exception e) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
        }
        connected = false;
    }

    public void shutdown() {
        disconnect();
        connectionManager.shutdown();
    }

    /**
     * Handles all messages from the server.
     */
    private class ServerResponseHandler implements StreamObserver<ServerMessage> {

        @Override
        public void onNext(ServerMessage serverMessage) {
            try {
                if (serverMessage.hasRequestBuffer()) {
                    handleRequestBuffer(serverMessage.getRequestBuffer());
                } else if (serverMessage.hasSyncResult()) {
                    handleSyncResult(serverMessage.getSyncResult());
                } else if (serverMessage.hasAck()) {
                    System.out.println("Server ACK: " + serverMessage.getAck().getMessage());
                } else if (serverMessage.hasError()) {
                    handleError(serverMessage.getError());
                }
            } catch (Exception e) {
                log.error("Error handling server message: {}", e.getMessage(), e);
            }
        }

        private void handleRequestBuffer(RequestBuffer request) {
            currentRoundId = request.getRoundId();
            System.out.println("Server requested buffer for round: " + currentRoundId + " - sending " + pendingEnvelopes.size() + " envelopes");

            try {
                // Build BufferUpload
                BufferUpload.Builder builder = BufferUpload.newBuilder()
                        .setNodeId(Config.NODE_SELF_ID);

                // Add all pending envelopes
                for (String pathStr : pendingEnvelopes) {
                    try {
                        Path path = Paths.get(pathStr);
                        byte[] envelopeBytes = Files.readAllBytes(path);
                        builder.addEnvelopes(ByteString.copyFrom(envelopeBytes));
                    } catch (Exception e) {
                        log.error("Failed to read envelope {}: {}", pathStr, e.getMessage());
                    }
                }

                // Add last known state
                try {
                    long lastSeq = db.getLastSequenceNumber(Config.NODE_SELF_ID);
                    String lastHash = db.getLastEnvelopeHash(Config.NODE_SELF_ID);
                    builder.setLastNodeSequence(lastSeq);
                    if (lastHash != null) {
                        builder.setLastEnvelopeHash(lastHash);
                    }
                } catch (Exception e) {
                    System.out.println("Could not get last state: " + e.getMessage());
                }

                // Send buffer
                ClientMessage msg = ClientMessage.newBuilder()
                        .setBufferUpload(builder.build())
                        .build();

                requestObserver.onNext(msg);
                System.out.println("Sent buffer with " + builder.getEnvelopesCount() + " envelopes for round " + currentRoundId);

            } catch (Exception e) {
                log.error("Failed to send buffer: {}", e.getMessage(), e);
            }
        }

        private void handleSyncResult(SyncResult result) {
            System.out.println("Received SyncResult for round: " + result.getRoundId() + " (" + result.getOrderedEnvelopesCount() + " envelopes)");

            try {
                int newEnvelopes = 0;
                int existingEnvelopes = 0;

                // Process each ordered envelope
                for (int i = 0; i < result.getOrderedEnvelopesCount(); i++) {
                    byte[] envelopeBytes = result.getOrderedEnvelopes(i).toByteArray();
                    String hash = (i < result.getEnvelopeHashesCount()) 
                            ? result.getEnvelopeHashes(i)
                            : HashUtils.sha256Hex(envelopeBytes);

                    boolean isNew = processReceivedEnvelope(envelopeBytes, hash);
                    if (isNew) newEnvelopes++;
                    else existingEnvelopes++;
                }

                // Clear pending buffer after successful sync
                pendingEnvelopes.clear();
                currentRoundId = null;

                System.out.println("Sync completed: " + newEnvelopes + " new, " + existingEnvelopes + " existing envelopes");
                System.out.println(String.format(
                        "✓ Sync completed: %d new envelopes received (total: %d)", 
                        newEnvelopes, result.getOrderedEnvelopesCount()));

            } catch (Exception e) {
                log.error("Failed to process SyncResult: {}", e.getMessage(), e);
            }
        }

        private boolean processReceivedEnvelope(byte[] envelopeBytes, String hash) {
            try {
                // Store file
                String filename = hash + ".env";
                Path outDir = Paths.get(Config.ENVELOPES_DIR);
                Files.createDirectories(outDir);
                Path filePath = outDir.resolve(filename);

                boolean isNew = false;

                // Check if already exists
                if (Files.exists(filePath)) {
                    byte[] existing = Files.readAllBytes(filePath);
                    String existingHash = HashUtils.sha256Hex(existing);
                    if (!existingHash.equals(hash)) {
                        log.error("Hash mismatch for {}: expected={}, found={}", 
                                filename, hash, existingHash);
                        return false;
                    }
                } else {
                    Files.write(filePath, envelopeBytes, StandardOpenOption.CREATE_NEW);
                    isNew = true;
                    log.debug("Stored new envelope: {}", filename);
                }

                // Parse and update DB
                String json = new String(envelopeBytes, StandardCharsets.UTF_8);
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                Envelope envelope = Envelope.fromJson(jsonObj);

                String signer = envelope.getMetadata().getSignerNodeId();
                long nodeSeq = envelope.getMetadata().getNodeSequenceNumber();
                String prevHash = envelope.getMetadata().getPrevEnvelopeHash();

                
                try {
                    db.insertReport(hash, filePath.toString(), signer, nodeSeq, prevHash);
                } catch (java.sql.SQLException e) {
                    // Unique constraint - already exists
                }

                // Update node state
                try {
                    db.upsertNodeState(signer, nodeSeq, hash);
                } catch (Exception e) {
                    System.out.println("Failed to update node state for " + signer + ": " + e.getMessage());
                }

                return isNew;

            } catch (Exception e) {
                log.error("Failed to process envelope {}: {}", hash, e.getMessage());
                return false;
            }
        }

        private void handleError(com.deathnode.common.grpc.Error error) {
            log.error("Server error [{}]: {}", error.getCode(), error.getMessage());
            System.err.println("Server error: " + error.getMessage());
        }

        @Override
        public void onError(Throwable t) {
            log.error("gRPC stream error: {}", t.getMessage(), t);
            connected = false;
        }

        @Override
        public void onCompleted() {
            System.out.println("Server closed connection");
            connected = false;
        }
    }
}