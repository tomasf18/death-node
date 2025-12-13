package com.deathnode.client.grpc;

import com.deathnode.server.grpc.Connections;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class Node {
    private final Connections connections;
    private final Server server;

    public Node(int port) {
        this.server = ServerBuilder.forPort(port)
                .addService(new Stub())
                .build();

        this.connections = Connections.getInstance();
    }

    public void start() throws IOException {
        server.start();
        System.out.println("✓ DeathNode Client initialized at port: " + server.getPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("✗ Shutting down...");
            this.stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        connections.shutdown();
    }

    /**
     * This should be used to register the server
     */
    public void registerServer(String nodeId, String host, int port) {
        if (connections.registerBlockingPeer(nodeId, host, port))
            System.out.println("✓ Server registered: " + nodeId + " @ " + host + ":" + port);
        else
            System.out.println("✗ Error registering server " + nodeId + " @ " + host + ":" + port);
    }
}