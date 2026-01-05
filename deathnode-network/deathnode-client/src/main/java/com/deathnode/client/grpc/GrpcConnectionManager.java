package com.deathnode.client.grpc;

import com.deathnode.common.grpc.SyncServiceGrpc;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

import com.deathnode.client.config.Config;
import javax.net.ssl.SSLException;
import java.io.File;

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
            return;
        }

        System.out.println("Connecting to server at " + serverHost + ":" + serverPort + " (TLS)");

        try {
            // Build SSL context
            SslContext sslContext = GrpcSslContexts.forClient()
                    // Trust server cert (signed by CA)
                    .trustManager(new File(Config.getCaCertificate()))
                    // Client certificate for mutual TLS
                    .keyManager(
                            new File(Config.getSelfCertificate()),
                            new File(Config.getSelfGrpcPrivateKey()))
                    .build();

            channel = NettyChannelBuilder
                    .forAddress(serverHost, serverPort)
                    .sslContext(sslContext)
                    .disableRetry() // Disable automatic retries
                    .build();

            asyncStub = SyncServiceGrpc.newStub(channel);
            blockingStub = SyncServiceGrpc.newBlockingStub(channel);

            System.out.println("gRPC TLS channel established.");

        } catch (SSLException e) {
            throw new RuntimeException("Failed to configure TLS: " + e.getMessage(), e);
        }
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