package com.deathnode.server.grpc;

import com.deathnode.common.grpc.Error;
import com.deathnode.common.grpc.SyncServiceGrpc;
import com.deathnode.common.grpc.ClientMessage;
import com.deathnode.common.grpc.ServerMessage;
import com.deathnode.common.grpc.Hello;
import com.deathnode.common.grpc.BufferUpload;
import com.deathnode.common.grpc.Ack;
import com.deathnode.server.service.SyncCoordinator;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.protobuf.ByteString;

/**
 * gRPC service implementation for bidirectional sync streaming.
 * <p>
 * This handles the server side of the sync protocol:
 * 1. Receives Hello from client
 * 2. Registers client with coordinator
 * 3. Sends RequestBuffer to client
 * 4. Receives BufferUpload from client
 * 5. When all nodes have uploaded, if an error occurs, sends Error to all clients
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
     * handle a single client's stream.
     */
    private static class ClientStreamHandler implements StreamObserver<ClientMessage> {

        private final StreamObserver<ServerMessage> responseObserver;
        private final SyncCoordinator coordinator;
        private String nodeId;
        private boolean registered = false;

        public ClientStreamHandler(StreamObserver<ServerMessage> responseObserver, SyncCoordinator coordinator) {
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
                } else if (clientMessage.hasAck()) {
                    handleBlockAck(clientMessage.getAck());
                } else if (clientMessage.hasError()) {
                    handleError(clientMessage.getError());
                } else {
                    // System.out.println("Received unknown client message type from " + this.nodeId);
                }
            } catch (Exception e) {
                // System.out.println("Error handling client message from " + this.nodeId + ": " + e.getMessage());
                sendError("INTERNAL_ERROR", "Failed to process message: " + e.getMessage());
            }
        }

        private void handleHello(Hello hello) {
            this.nodeId = hello.getNodeId();

            if (hello.getStartSync()) {
                System.out.println("\n  <- Received Sync Request from " + this.nodeId);
                String roundId = coordinator.startRoundIfAbsent(this.nodeId);
                // System.out.println("Client " + this.nodeId + " initiated sync round " + roundId);
            } else {
                SyncCoordinator.ClientConnection conn = new SyncCoordinator.ClientConnection(this.nodeId, responseObserver);
                coordinator.registerClient(this.nodeId, conn);
                registered = true;
                System.out.println("[V] Connected: " + this.nodeId);
            }
        }

        private void handleBufferUpload(BufferUpload upload) {
            String bufferNodeId = upload.getNodeId();

            // Convert protobuf repeated bytes to List<byte[]>
            List<byte[]> envelopes = upload.getEnvelopesList().stream()
                    .map(ByteString::toByteArray)
                    .toList();

            System.out.println("  <- Buffer received from " + bufferNodeId + " (" + envelopes.size() + " envelopes)");

            byte[] bufferRoot = upload.getBufferRoot().toByteArray();
            byte[] signedBufferRoot = upload.getSignedBufferRoot().toByteArray();

            SyncCoordinator.VerificationsResult verificationsResult = coordinator.performAllVerifications(bufferNodeId, this.nodeId, envelopes, bufferRoot, signedBufferRoot);

            if (!verificationsResult.isSuccess()) {
                sendError(verificationsResult.getErrorCode(), verificationsResult.getErrorMessage());
                return;
            }

            // System.out.println("Received buffer upload from " + bufferNodeId + " with " + upload.getEnvelopesCount() + " envelopes");

            try {
                // Subnit buffer to coordinator and get future
                CompletableFuture<SyncCoordinator.SyncResultObject> future = coordinator.submitBufferAndRoot(bufferNodeId, envelopes, bufferRoot, signedBufferRoot);
                // When complete, check inf an error occurred. If so, send Error to client. If not, the result have already been broadcasted by SyncCoordinator
                future.whenComplete((result, error) -> {
                    if (error != null) {
                        // System.err.println("Sync round failed: " + error.getMessage());
                        sendError("SYNC_FAILED", error.getMessage());
                        responseObserver.onCompleted();
                    } else if (result == null) {
                        // System.out.println("Empty sync round. Nothing to do...");
                    } else{
                        // sendSyncResult(result);
                    }

                });

            } catch (Exception e) {
                // System.err.println("Failed to submit buffer for " + bufferNodeId + ": " + e.getMessage());
                sendError("SUBMIT_FAILED", e.getMessage());
                responseObserver.onCompleted();
            }
        }

        private void handleBlockAck(Ack blockAck) {
            coordinator.receivePeerAck(blockAck.getSuccess());
        }

        private void sendError(String code, String message) {
            Error error = Error.newBuilder()
                    .setCode(code)
                    .setMessage(message)
                    .build();

            ServerMessage msg = ServerMessage.newBuilder()
                    .setError(error)
                    .build();

            responseObserver.onNext(msg);
        }

        private void handleError(Error error) {
            System.err.println("[ERROR] " + this.nodeId + " [" + error.getCode() + "]: " + error.getMessage());
        }

        @Override
        public void onError(Throwable t) {
            System.err.println("[ERROR] Stream error for " + this.nodeId + ": " + t.getMessage());
            if (registered) {
                coordinator.unregisterClient(this.nodeId);
            }
        }

        @Override
        public void onCompleted() {
            // System.out.println("Client " + this.nodeId + " closed connection");
            if (registered) {
                coordinator.unregisterClient(this.nodeId);
            }
            responseObserver.onCompleted();
        }
    }
}