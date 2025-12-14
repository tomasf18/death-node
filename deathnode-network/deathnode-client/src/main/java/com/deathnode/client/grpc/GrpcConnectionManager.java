package com.deathnode.client.grpc;

import com.deathnode.common.grpc.SyncServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages gRPC connection to the server.
 * Singleton pattern for shared channel.
 */
public class GrpcConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(GrpcConnectionManager.class);

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

        log.info("Connecting to server at {}:{}", serverHost, serverPort);

        channel = ManagedChannelBuilder
                .forAddress(serverHost, serverPort)
                .usePlaintext() // TODO: Switch to TLS for security
                .build();

        asyncStub = SyncServiceGrpc.newStub(channel);
        blockingStub = SyncServiceGrpc.newBlockingStub(channel);

        log.info("gRPC channel established");
    }

    public SyncServiceGrpc.SyncServiceStub getAsyncStub() {
        return asyncStub;
    }

    public SyncServiceGrpc.SyncServiceBlockingStub getBlockingStub() {
        return blockingStub;
    }

    public void shutdown() {
        if (channel != null) {
            log.info("Shutting down gRPC channel");
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