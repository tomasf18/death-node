package com.deathnode.client.grpc;

import com.deathnode.client.config.Config;
import com.deathnode.client.service.DatabaseService;
import com.deathnode.common.grpc.*;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.common.util.HashUtils;
import com.google.gson.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

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
                .setNodeId(Config.getNodeSelfId())
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
        System.out.println("Added envelope to buffer: " + Paths.get(envelopeFilePath).getFileName() + " (total: " + pendingEnvelopes.size() + ")");
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
                .setNodeId(Config.getNodeSelfId())
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
                System.out.println("Error handling server message: " + e.getMessage());
            }
        }

        private void handleRequestBuffer(RequestBuffer request) {
            currentRoundId = request.getRoundId();
            System.out.println("Server requested buffer for round: " + currentRoundId + " - sending " + pendingEnvelopes.size() + " envelopes");

            try {
                // Build BufferUpload
                BufferUpload.Builder builder = BufferUpload.newBuilder()
                        .setNodeId(Config.getNodeSelfId());

                // Add all pending envelopes
                for (String pathStr : pendingEnvelopes) {
                    try {
                        Path path = Paths.get(pathStr);
                        byte[] envelopeBytes = Files.readAllBytes(path);
                        builder.addEnvelopes(ByteString.copyFrom(envelopeBytes));
                    } catch (Exception e) {
                        System.err.println("Failed to read envelope " + pathStr + ": " + e.getMessage());
                    }
                }

                // Add last known state
                try {
                    long lastSeq = db.getLastSequenceNumber(Config.getNodeSelfId());
                    String lastHash = db.getLastEnvelopeHash(Config.getNodeSelfId());
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
                System.err.println("Failed to send buffer: " + e.getMessage());
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
                System.out.println(String.format("Sync completed: %d new envelopes received (total: %d)", newEnvelopes, result.getOrderedEnvelopesCount()));

            } catch (Exception e) {
                System.err.println("Failed to process SyncResult: " + e.getMessage());
            }
        }

        private boolean processReceivedEnvelope(byte[] envelopeBytes, String hash) {
            try {
                // Store file
                String filename = hash + ".json";
                Path outDir = Paths.get(Config.getEnvelopesDir());
                Files.createDirectories(outDir);
                Path filePath = outDir.resolve(filename);

                boolean isNew = false;

                // Check if already exists
                if (Files.exists(filePath)) {
                    byte[] existing = Files.readAllBytes(filePath);
                    String existingHash = HashUtils.sha256Hex(existing);
                    if (!existingHash.equals(hash)) {
                        System.err.println("Hash mismatch for " + filename + ": expected=" + hash + ", found=" + existingHash);
                        return false;
                    }
                } else {
                    Files.write(filePath, envelopeBytes, StandardOpenOption.CREATE_NEW);
                    isNew = true;
                    System.out.println("Stored new envelope: " + filename);
                }

                // Parse and update DB
                String json = new String(envelopeBytes, StandardCharsets.UTF_8);
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                Envelope envelope = Envelope.fromJson(jsonObj);

                Metadata metadata = envelope.getMetadata();
                String signer = metadata.getSignerNodeId();
                long nodeSeq = metadata.getNodeSequenceNumber();
                String prevHash = metadata.getPrevEnvelopeHash();
                String metadataTimestamp = metadata.getMetadataTimestamp();
                
                try {
                    if (isNew) {
                        db.insertReport(hash, filePath.toString(), signer, nodeSeq, db.getGlobalSeqFromLastSyncedReport() + 1, metadataTimestamp, prevHash);
                    } else {
                        db.updateReport(hash, db.getGlobalSeqFromLastSyncedReport() + 1);
                        isNew = false;
                        System.out.println("Envelope already exists: " + filename);
                    }
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
                System.err.println("Failed to process envelope " + hash + ": " + e.getMessage());
                return false;
            }
        }

        private void handleError(com.deathnode.common.grpc.Error error) {
            System.err.println("Server error [" + error.getCode() + "]: " + error.getMessage());
        }

        @Override
        public void onError(Throwable t) {
            System.err.println("gRPC stream error: " + t.getMessage());
            connected = false;
        }

        @Override
        public void onCompleted() {
            System.out.println("Server closed connection");
            connected = false;
        }
    }
}