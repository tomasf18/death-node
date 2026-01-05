package com.deathnode.client.grpc;

import com.deathnode.client.config.Config;
import com.deathnode.client.service.DatabaseService;
import com.deathnode.client.service.ReportCleanupService;
import com.deathnode.tool.util.KeyLoader;
import com.deathnode.common.grpc.*;
import com.deathnode.common.grpc.ServerMessage;
import com.deathnode.common.grpc.Error;
import com.deathnode.common.grpc.Ack;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.common.util.HashUtils;
import com.deathnode.common.util.MerkleUtils;
import com.deathnode.tool.SecureDocumentProtocol;
import com.google.gson.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.*;
import java.sql.SQLException;

/**
 * Persistent gRPC client that maintains an open connection to the server.
 * <p>
 * This allows the server to push RequestBuffer messages at any time,
 * enabling coordinated sync rounds across all nodes.
 */
public class PersistentSyncClient {
    private final DatabaseService db;
    private final GrpcConnectionManager connectionManager;
    private final ReportCleanupService cleanupService;
    private final Queue<String> pendingEnvelopes = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService pendingReportsExecutor = Executors.newScheduledThreadPool(1);

    private StreamObserver<ClientMessage> requestObserver;
    private volatile boolean connected = false;
    private volatile String currentRoundId = null;
    private volatile long roundStartTime = -1;
    private volatile ScheduledFuture<?> timeoutTask = null;
    private volatile ScheduledFuture<?> pendingReportsTask = null;
    private volatile CompletableFuture<Void> syncCompletionFuture = null;

    public PersistentSyncClient(DatabaseService db, String serverHost, int serverPort) {
        this.db = db;
        this.connectionManager = new GrpcConnectionManager(serverHost, serverPort);
        this.cleanupService = new ReportCleanupService(db);
    }

    /**
     * Connect to server and maintain persistent connection.
     *
     * @param startSync If true, this client initiates a sync round
     */
    public void connect() {
        if (connected) {
            // System.out.println("Already connected");
            return;
        }

        SyncServiceGrpc.SyncServiceStub asyncStub = connectionManager.getAsyncStub();

        // Open bidirectional stream
        requestObserver = asyncStub.sync(new ServerResponseHandler(db));

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

        System.out.println("[V] Connected to server");
    }

    /**
     * Start a periodic task that checks for pending reports every N seconds.
     * If pending reports are found, it triggers a sync round.y
     */
    public void startPendingReportsMonitor() {
        if (pendingReportsTask != null) {
            // System.out.println("Pending reports monitor already running");
            return;
        }

        // System.out.println("Starting pending reports monitor (interval: " + Config.INTERVAL_BETWEEN_PENDING_CHECKS_SECONDS + " seconds)");

        pendingReportsTask = pendingReportsExecutor.scheduleAtFixedRate(() -> {
            int pendingCount = pendingEnvelopes.size();

            if (pendingCount > 0) {
                System.out.println("Found " + pendingCount + " pending reports. Triggering sync...");
                triggerSync();
            } else {
                //System.out.println("No pending reports found");
            }
        }, Config.INTERVAL_BETWEEN_PENDING_CHECKS_SECONDS, Config.INTERVAL_BETWEEN_PENDING_CHECKS_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stop the pending reports monitor.
     */
    public void stopPendingReportsMonitor() {
        // System.out.println("Stopping pending reports monitor");
        if (pendingReportsTask != null) {
            pendingReportsTask.cancel(false);
            pendingReportsTask = null;
        }
    }

    /**
     * Handle connection timeout by cleaning up unsynced reports.
     */
    private void handleConnectionTimeout() {
        System.err.println("=== NODE CORRUPTED ===");
        System.err.println("Connection timeout detected. Cleaning up unsynced reports...");

        try {
            ReportCleanupService.CleanupResult result = cleanupService.cleanupAllUnsyncedReports();

            System.out.println("Cleanup completed successfully:");
            System.out.println("  - Total unsynced reports found: " + result.getTotalReportsFound());
            System.out.println("  - Files deleted: " + result.getFilesDeleted());
            System.out.println("  - Database records deleted: " + result.getDatabaseRecordsDeleted());

            if (!result.isComplete()) {
                System.err.println("WARNING: Some deletions failed:");
                for (String failedHash : result.getFailedDeletions()) {
                    System.err.println("  - Failed to cleanup: " + failedHash);
                }
            }

            // Clear the buffer
            int bufferSize = pendingEnvelopes.size();
            pendingEnvelopes.clear();
            System.out.println("Pending buffer cleared: " + bufferSize + " envelopes removed");

            System.err.println("=== RECOVERY COMPLETE ===");
            System.err.println("All unsynced reports have been deleted. The node can now reconnect.");
            
            // Signal sync completion
            PersistentSyncClient.this.currentRoundId = null;
            PersistentSyncClient.this.completeSyncRound();

        } catch (SQLException e) {
            System.err.println("CRITICAL ERROR during cleanup: " + e.getMessage());
            System.err.println("Manual intervention may be required to clean up unsynced reports.");
        }
    }

    /**
     * Start monitoring for sync round timeout.
     * If no SyncResult is received within the timeout period, trigger cleanup.
     */
    private void startTimeoutMonitoring(String roundId) {
        // Cancel any existing timeout task
        cancelTimeoutMonitoring("Starting new timeout monitoring for round " + roundId);

        roundStartTime = System.currentTimeMillis();
        currentRoundId = roundId;

        // Schedule a task to check for timeout
        this.timeoutTask = timeoutExecutor.schedule(() -> {
            synchronized (this) {
                // Check if we're still in the same round
                if (!roundId.equals(currentRoundId)) {
                    return; // Round was completed or changed
                }

                long elapsedMs = System.currentTimeMillis() - roundStartTime;
                long timeoutMs = Config.SYNC_TIMEOUT_SECONDS * 1000;

                if (elapsedMs >= timeoutMs) {
                    System.err.println("TIMEOUT: Round " + roundId + " exceeded " + Config.SYNC_TIMEOUT_SECONDS + " seconds");
                    currentRoundId = null; // Clear round ID
                    handleConnectionTimeout();
                }
            }
        }, Config.SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Cancel the current timeout monitoring task.
     */
    private void cancelTimeoutMonitoring(String reason) {
        // System.out.println("Canceling timeout monitoring: " + reason);
        if (this.timeoutTask != null) {
            this.timeoutTask.cancel(false);
            this.timeoutTask = null;
        }
        roundStartTime = -1;
    }

    /**
     * Add envelope file path to pending buffer.
     */
    public void addPendingEnvelope(String envelopeFilePath) {
        pendingEnvelopes.add(envelopeFilePath);
        // System.out.println("Added envelope to buffer: " + Paths.get(envelopeFilePath).getFileName() + " (total: " + pendingEnvelopes.size() + ")");
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
            System.out.println("[!] Not connected - cannot trigger sync");
            return;
        }

        if (currentRoundId != null) {
            System.out.println("[!] Sync already in progress (round: " + currentRoundId + ")");
            return;
        }

        // Create a new CompletableFuture to track this sync round's completion
        syncCompletionFuture = new CompletableFuture<>();

        System.out.println("\n[SYNC] Triggering sync round...");

        Hello hello = Hello.newBuilder()
                .setNodeId(Config.getNodeSelfId())
                .setStartSync(true)
                .build();

        ClientMessage helloMsg = ClientMessage.newBuilder()
                .setHello(hello)
                .build();

        requestObserver.onNext(helloMsg);

        // Start timeout monitoring (UUID of round will be assigned by server, use a placeholder)
        startTimeoutMonitoring("initiating-" + System.currentTimeMillis());
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
        cancelTimeoutMonitoring("Shutting down client");
        stopPendingReportsMonitor();
        connectionManager.shutdown();
        timeoutExecutor.shutdown();
        pendingReportsExecutor.shutdown();
    }

    /**
     * Wait for the current sync round to complete (success, error, or timeout).
     * Returns immediately if no sync is in progress.
     *
     * @throws Exception if waiting for sync completion times out
     */
    public void waitForSyncCompletion() throws Exception {
        if (syncCompletionFuture == null) {
            return;
        }
        try {
            syncCompletionFuture.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new Exception("Sync completion wait timed out after 60 seconds");
        } catch (Exception e) {
            // CompletableFuture completed (either successfully or with error from the round)
            // The error/success was already printed by handlers, so we just return
        }
    }

    /**
     * Signal that the current sync round has completed (via any path: success, error, or timeout).
     */
    private void completeSyncRound() {
        if (syncCompletionFuture != null && !syncCompletionFuture.isDone()) {
            syncCompletionFuture.complete(null);
        }
    }

    /**
     * Handles all messages from the server.
     */
    private class ServerResponseHandler implements StreamObserver<ServerMessage> {

        private final DatabaseService db;
        VerificationsHandler verificationsHandler;
        private PendingBlock pendingBlock;

        public ServerResponseHandler(DatabaseService db) {
            this.db = db;
            this.verificationsHandler = new VerificationsHandler(db);
            this.pendingBlock = null;
        }

        @Override
        public void onNext(ServerMessage serverMessage) {
            try {
                if (serverMessage.hasRequestBuffer()) {
                    handleRequestBuffer(serverMessage.getRequestBuffer());
                } else if (serverMessage.hasSyncResult()) {
                    handleSyncResult(serverMessage.getSyncResult());
                } else if (serverMessage.hasAck()) {
                    String message = serverMessage.getAck().getMessage();
                    checkForCommit(serverMessage, message);
                    // System.out.println("Server ACK: " + message);
                    cancelTimeoutMonitoring("Server ACK received in time");
                } else if (serverMessage.hasError()) {
                    handleError(serverMessage.getError());
                }
            } catch (Exception e) {
                // System.out.println("Error handling server message: " + e.getMessage());
            }
        }

        private void checkForCommit(ServerMessage serverMessage, String message) {
            try {
                if (message.startsWith("commit")) {
                    boolean success = serverMessage.getAck().getSuccess();
                    if (success) {
                        commitBlock();
                    } else if (pendingBlock != null && message.split(" ")[1].equals(new String(pendingBlock.root)))
                        pendingBlock = null;
                }
            }
            catch (Exception e) {
                return;
            }
        }

        private void handleRequestBuffer(RequestBuffer request) {
            // Cancel timeout monitoring - server answered in time
            cancelTimeoutMonitoring("Server buffer request received in time");

            String roundId = request.getRoundId();
            //System.out.println("Server requested buffer for round: " + roundId + " - sending " + pendingEnvelopes.size() + " envelopes");
            System.out.println("\n  <- Server requested buffer for round: " + roundId);


            try {
                // Build BufferUpload
                BufferUpload.Builder builder = BufferUpload.newBuilder()
                        .setNodeId(Config.getNodeSelfId());

                List<byte[]> envelopesToSend = new ArrayList<>();

                // Add MAX_ENVELOPES_TO_SEND_PER_SYNC pending envelopes
                for (String pathStr : pendingEnvelopes) { // first-in, first-out, so buffer order is preserved when sending
                    if (envelopesToSend.size() >= Config.MAX_ENVELOPES_TO_SEND_PER_SYNC) {
                        break; // limit number of envelopes per sync
                    }
                    try {
                        Path path = Paths.get(pathStr);
                        byte[] envelopeBytes = Files.readAllBytes(path);
                        builder.addEnvelopes(ByteString.copyFrom(envelopeBytes));
                        envelopesToSend.add(envelopeBytes);
                    } catch (Exception e) {
                        System.err.println("Failed to read envelope " + pathStr + ": " + e.getMessage());
                    }
                }

                PrivateKey signPrivateKey = KeyLoader.loadPrivateKeyFromKeystore(Config.ED_PRIVATE_KEY_ALIAS, Config.getKeystorePath(), Config.KEYSTORE_PASSWORD);
                // Compute and sign Merkle root
                byte[] merkleRoot = MerkleUtils.computeMerkleRoot(envelopesToSend);
                // System.out.println("Computed Merkle root for buffer: " + HashUtils.bytesToHex(merkleRoot));
                byte[] signedMerkleRoot = SecureDocumentProtocol.signData(merkleRoot, signPrivateKey);
                // System.out.println("Signed Merkle root for buffer: " + HashUtils.bytesToHex(signedMerkleRoot));

                builder.setBufferRoot(ByteString.copyFrom(merkleRoot));
                builder.setSignedBufferRoot(ByteString.copyFrom(signedMerkleRoot));

                // Send buffer
                ClientMessage msg = ClientMessage.newBuilder()
                        .setBufferUpload(builder.build())
                        .build();

                requestObserver.onNext(msg);

                // Start timeout monitoring for this round
                startTimeoutMonitoring(roundId);
                System.out.println("  -> Buffer sent (" + builder.getEnvelopesCount() + " envelopes)");

            } catch (Exception e) {
                System.err.println("[ERROR] Failed to send buffer: " + e.getMessage());
            }
        }

        private void handleSyncResult(SyncResult result) {
            // Cancel timeout monitoring - sync completed successfully
            cancelTimeoutMonitoring("Sync result received in time");

            String roundId = result.getRoundId();
            List<byte[]> orderedEnvelopes = new ArrayList<>();
            for (int i = 0; i < result.getOrderedEnvelopesCount(); i++) {
                orderedEnvelopes.add(result.getOrderedEnvelopes(i).toByteArray());
            }
            long blockNumber = result.getBlockNumber();
            byte[] blockRoot = result.getBlockRoot().toByteArray();
            byte[] signedBlockRoot = result.getSignedBlockRoot().toByteArray();
            List<SignedBufferRoot> perNodeSignedBufferRoots = result.getPerNodeSignedBufferRootsList();
            byte[] prevBlockRoot = result.getPrevBlockRoot().toByteArray();

            System.out.println("  <- Received SyncResult (" + result.getOrderedEnvelopesCount() + " envelopes)");

            VerificationsHandler.VerificationsResult verificationsResult = verificationsHandler.performAllVerifications(
                    roundId,
                    orderedEnvelopes,
                    blockNumber,
                    blockRoot,
                    signedBlockRoot,
                    perNodeSignedBufferRoots,
                    prevBlockRoot
            );

            /*
            if (!verificationsResult.isSuccess()) {
                sendError(verificationsResult.getErrorCode(), verificationsResult.getErrorMessage());
                return; 
            }
             */

            boolean success = verificationsResult.isSuccess();
            sendBlockAck(success);
            if (!success) {
                System.out.println("[X] Block failed verification. Voiding round " + roundId);
                PersistentSyncClient.this.currentRoundId = null;
                PersistentSyncClient.this.completeSyncRound();
                return;
            }

            pendingBlock = new PendingBlock(result, blockNumber, blockRoot);
            // System.out.println("Pending commit...");
            
            // Signal sync completion
            PersistentSyncClient.this.currentRoundId = null;

        }

        private void commitBlock() {
            SyncResult result = pendingBlock.result;
            long blockNumber = pendingBlock.number;
            byte[] blockRoot = pendingBlock.root;

            try {
                db.upsertBlockState(blockNumber, HashUtils.bytesToHex(blockRoot));
            } catch (SQLException e) {
                System.err.println("Failed to update block state in DB: " + e.getMessage());
            }

            try {
                int newEnvelopes = 0;
                int existingEnvelopes = 0;

                // Process each ordered envelope
                for (int i = 0; i < result.getOrderedEnvelopesCount(); i++) {
                    byte[] envelopeBytes = result.getOrderedEnvelopes(i).toByteArray();

                    boolean isNew = processReceivedEnvelope(envelopeBytes);
                    if (isNew) newEnvelopes++;
                    else existingEnvelopes++;
                }

                // Poll all synchronized reports
                for (int i = 0; i < existingEnvelopes; i++) {
                    pendingEnvelopes.poll(); // Remove from pending buffer
                }

                currentRoundId = null;

                System.out.println("Sync completed: " + newEnvelopes + " new, " + existingEnvelopes + " existing envelopes, total " + result.getOrderedEnvelopesCount());
                PersistentSyncClient.this.completeSyncRound();
            } catch (Exception e) {
                System.err.println("Failed to process SyncResult: " + e.getMessage());
            }
            pendingBlock = null;

        }

        private boolean processReceivedEnvelope(byte[] envelopeBytes) {
            String hash = HashUtils.sha256Hex(envelopeBytes);
            try {
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
                } catch (SQLException e) {
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

        private void handleError(Error error) {
            cancelTimeoutMonitoring("Server error received");
            System.err.println("Server error [" + error.getCode() + "]: " + error.getMessage());
            try {
                PersistentSyncClient.this.currentRoundId = null;
                cleanupService.cleanupAllUnsyncedReports();
            } catch (SQLException e) {
                System.err.println("Failed to cleanup unsynced reports after server error: " + e.getMessage());
            }
            // Signal sync completion
            PersistentSyncClient.this.completeSyncRound();
        }

        private void sendError(String code, String message) {
            Error error = Error.newBuilder()
                    .setCode(code)
                    .setMessage(message)
                    .build();

            ClientMessage msg = ClientMessage.newBuilder()
                    .setError(error)
                    .build();

            PersistentSyncClient.this.requestObserver.onNext(msg);
        }

        private void sendBlockAck(boolean ack) {
            Ack blockAck = Ack.newBuilder()
                    .setSuccess(ack)
                    .build();

            ClientMessage msg = ClientMessage.newBuilder()
                    .setAck(blockAck)
                    .build();

            PersistentSyncClient.this.requestObserver.onNext(msg);
        }

        @Override
        public void onError(Throwable t) {
            cancelTimeoutMonitoring("Server error received");
            System.err.println("gRPC stream error: " + t.getMessage());
            connected = false;
        }

        @Override
        public void onCompleted() {
            cancelTimeoutMonitoring("Server closed connection");
            System.out.println("Server closed connection");
            connected = false;
        }

        private record PendingBlock(SyncResult result, long number, byte[] root) {
        }
    }
}