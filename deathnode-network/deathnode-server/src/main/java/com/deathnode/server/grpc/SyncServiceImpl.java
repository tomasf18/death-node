package com.deathnode.server.grpc;

import com.deathnode.common.grpc.Error;
import com.deathnode.common.grpc.SyncServiceGrpc;
import com.deathnode.common.grpc.ClientMessage;
import com.deathnode.common.grpc.ServerMessage;
import com.deathnode.common.grpc.Hello;
import com.deathnode.common.grpc.BufferUpload;
import com.deathnode.common.grpc.SyncResult;
import com.deathnode.server.service.SyncCoordinator;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC service implementation for bidirectional sync streaming.
 * 
 * This handles the server side of the sync protocol:
 * 1. Receives Hello from client
 * 2. Registers client with coordinator
 * 3. Sends RequestBuffer to client
 * 4. Receives BufferUpload from client
 * 5. When all nodes have uploaded, computes SyncResult
 * 6. Sends SyncResult to all clients
 */
@GrpcService
public class SyncServiceImpl extends SyncServiceGrpc.SyncServiceImplBase {
    
    private final SyncCoordinator coordinator;

    public SyncServiceImpl(SyncCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public StreamObserver<ClientMessage> sync(StreamObserver<ServerMessage> responseObserver) {
        return new ClientStreamHandler(responseObserver, coordinator);
    }

    /**
     * Inner class to handle a single client's stream.
     */
    private static class ClientStreamHandler implements StreamObserver<ClientMessage> {
        
        private final StreamObserver<ServerMessage> responseObserver;
        private final SyncCoordinator coordinator;
        private String nodeId;
        private boolean registered = false;

        public ClientStreamHandler(StreamObserver<ServerMessage> responseObserver, 
                                  SyncCoordinator coordinator) {
            this.responseObserver = responseObserver;
            this.coordinator = coordinator;
        }

        @Override
        public void onNext(ClientMessage clientMessage) {
            try {
                if (clientMessage.hasHello()) {
                    handleHello(clientMessage.getHello());
                } else if (clientMessage.hasBufferUpload()) {
                    handleBufferUpload(clientMessage.getBufferUpload());
                } else {
                    System.out.println("Received unknown client message type from " + nodeId);
                }
            } catch (Exception e) {
                System.out.println("Error handling client message from " + nodeId + ": " + e.getMessage());
                sendError("INTERNAL_ERROR", "Failed to process message: " + e.getMessage());
            }
        }

        private void handleHello(Hello hello) {
            this.nodeId = hello.getNodeId();
            System.out.println("Client " + nodeId + " connected, start_sync=" + hello.getStartSync());

            // Register this client connection with coordinator FIRST
            SyncCoordinator.ClientConnection conn = new SyncCoordinator.ClientConnection(nodeId, responseObserver);
            coordinator.registerClient(nodeId, conn);
            registered = true;

            // If this client wants to start a sync, create/join round
            // The coordinator will broadcast RequestBuffer to ALL connected nodes
            if (hello.getStartSync()) {
                String roundId = coordinator.startRoundIfAbsent(nodeId);
                System.out.println("Client " + nodeId + " initiated sync round " + roundId);
            } else {
                // Just joining - if there's an active round, the broadcast already happened
                // or will happen when another node starts sync
                System.out.println("Client " + nodeId + " connected (not initiating sync)");
            }
        }

        private void handleBufferUpload(BufferUpload upload) {
            String uploadNodeId = upload.getNodeId();
            
            if (!uploadNodeId.equals(nodeId)) {
                System.out.println("Node ID mismatch: stream=" + nodeId + ", upload=" + uploadNodeId);
                sendError("NODE_ID_MISMATCH", "Node ID in upload doesn't match connection");
                return;
            }

            System.out.println("Received buffer upload from " + nodeId + " with " + upload.getEnvelopesCount() + " envelopes");

            // Convert protobuf repeated bytes to List<byte[]>
            java.util.List<byte[]> envelopes = upload.getEnvelopesList().stream()
                    .map(com.google.protobuf.ByteString::toByteArray)
                    .toList();

            try {
                // Submit buffer to coordinator and get future
                java.util.concurrent.CompletableFuture<SyncCoordinator.SyncResult> future = 
                        coordinator.submitBuffer(nodeId, envelopes);

                // When complete, send result to this client
                future.whenComplete((result, error) -> {
                    if (error != null) {
                        System.err.println("Sync round failed: " + error.getMessage());
                        sendError("SYNC_FAILED", error.getMessage());
                        responseObserver.onCompleted();
                    } else {
                        sendSyncResult(result);
                        responseObserver.onCompleted();
                    }
                });

            } catch (Exception e) {
                System.err.println("Failed to submit buffer for " + nodeId + ": " + e.getMessage());
                sendError("SUBMIT_FAILED", e.getMessage());
                responseObserver.onCompleted();
            }
        }

        private void sendSyncResult(SyncCoordinator.SyncResult result) {
            System.out.println("Sending SyncResult to " + nodeId + " for round " + result.roundId + " (" + result.orderedEnvelopes.size() + " envelopes)");

            SyncResult.Builder builder = SyncResult.newBuilder()
                    .setRoundId(result.roundId);

            // Add all ordered envelopes
            for (byte[] env : result.orderedEnvelopes) {
                builder.addOrderedEnvelopes(com.google.protobuf.ByteString.copyFrom(env));
            }

            // Add corresponding hashes
            for (String hash : result.envelopeHashes) {
                builder.addEnvelopeHashes(hash);
            }

            // TODO: Add signed_block_root and per_node_roots when implementing security

            ServerMessage msg = ServerMessage.newBuilder()
                    .setSyncResult(builder.build())
                    .build();

            responseObserver.onNext(msg);
        }

        private void sendError(String code, String message) {
            // Fully qualify to avoid collision with java.lang.Error
            Error error = Error.newBuilder()
                .setCode(code)
                .setMessage(message)
                .build();

            ServerMessage msg = ServerMessage.newBuilder()
                    .setError(error)
                    .build();

            responseObserver.onNext(msg);
        }

        @Override
        public void onError(Throwable t) {
            System.err.println("Stream error for client " + nodeId + ": " + t.getMessage());
            if (registered) {
                coordinator.unregisterClient(nodeId);
            }
        }

        @Override
        public void onCompleted() {
            System.out.println("Client " + nodeId + " closed connection");
            if (registered) {
                coordinator.unregisterClient(nodeId);
            }
            responseObserver.onCompleted();
        }
    }
}