package com.deathnode.client.grpc;

import com.deathnode.common.grpc.SyncServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * Manages gRPC connection to the server.
 * Singleton pattern for shared channel.
 */
public class GrpcConnectionManager {

    private final String serverHost;
    private final int serverPort;
    
    private ManagedChannel channel;
    private SyncServiceGrpc.SyncServiceStub asyncStub;
    private SyncServiceGrpc.SyncServiceBlockingStub blockingStub;

    public GrpcConnectionManager(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        connect();
    }

    private void connect() {
        if (channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            return; // Already connected
        }

        System.out.println("Connecting to server at " + serverHost + ":" + serverPort);

        channel = ManagedChannelBuilder
                .forAddress(serverHost, serverPort)
                .usePlaintext() // TODO: Switch to TLS for security
                .build();

        asyncStub = SyncServiceGrpc.newStub(channel);
        blockingStub = SyncServiceGrpc.newBlockingStub(channel);

        System.out.println("gRPC channel established");
    }

    public SyncServiceGrpc.SyncServiceStub getAsyncStub() {
        return asyncStub;
    }

    public SyncServiceGrpc.SyncServiceBlockingStub getBlockingStub() {
        return blockingStub;
    }

    public void shutdown() {
        if (channel != null) {
            System.out.println("Shutting down gRPC channel");
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}