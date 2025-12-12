package com.deathnode.server.grpc;

import com.deathnode.server.ServerApp;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ServerNode {
    private final int port;
    private final Server server;
    private final Connections connections;
    private final ServerApp app;

    public ServerNode(int port) {
        this.port = port;

        this.connections = new Connections();
        this.app = new ServerApp(connections);

        this.server = ServerBuilder.forPort(port)
                .addService(new ServerStub(app))
                .build();
    }

    public void start() throws IOException {
        server.start();
        System.out.println("✓ Servidor DeathNode initialized at port: " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("✗ Shutting down...");
            ServerNode.this.stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        connections.shutdown();
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public void registerPeer(String nodeId, String host, int port) {
        if (connections.registerPeer(nodeId, host, port))
            System.out.println("✓ Peer registered: " + nodeId + " @ " + host + ":" + port);
        else
            System.out.println("✗ Error registering peer " + nodeId + " @ " + host + ":" + port);
    }
}