package com.deathnode.server.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Connections {

    private final Map<String, SyncServiceGrpc.SyncServiceStub> peerStubs;
    private final Map<String, ManagedChannel> connectedPeers;

    public Connections() {
        this.connectedPeers = new ConcurrentHashMap<>();
        this.peerStubs = new ConcurrentHashMap<>();
    }

    public void shutdown() {
        for (ManagedChannel channel : connectedPeers.values()) {
            channel.shutdown();
        }
    }

    public boolean registerPeer(String nodeId, String host, int port) {
        if (!connectedPeers.containsKey(nodeId)) {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();

            SyncServiceGrpc.SyncServiceStub stub = SyncServiceGrpc.newStub(channel);

            connectedPeers.put(nodeId, channel);
            peerStubs.put(nodeId, stub);

            return true;
        }
        return false;
    }
}
