package com.deathnode.server.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Connections {

    private final Map<String, SyncServiceGrpc.SyncServiceStub> peerStubs = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> connectedPeers = new ConcurrentHashMap<>();;

    private static Connections instance = null;
    private Connections() {
    }
    public static Connections getInstance() {
        if (instance == null) {
            instance = new Connections();
        }
        return instance;
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

    public ManagedChannel getChannel(String nodeId) {
        return connectedPeers.get(nodeId);
    }
}
