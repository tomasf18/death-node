package server;

import grpc_generated.DeathNodeGrpc;
import grpc_generated.DeathNodeProtoBuf;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerNode {
    private final int port;
    private final Server server;
    private final Map<String, ManagedChannel> connectedPeers;
    private final Map<String, DeathNodeGrpc.DeathNodeStub> peerStubs;

    public ServerNode(int port) {
        this.port = port;
        this.connectedPeers = new ConcurrentHashMap<>();
        this.peerStubs = new ConcurrentHashMap<>();

        this.server = ServerBuilder.forPort(port)
                .addService(new ServerStub())
                .build();
    }

    public void start() throws IOException {
        server.start();
        System.out.println("✓ Servidor DeathNode iniciado na porta " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("✗ Desligando servidor...");
            ServerNode.this.stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        // Fechar conexões com peers
        for (ManagedChannel channel : connectedPeers.values()) {
            channel.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Registra um peer cliente para receber notificações
     */
    public void registerPeer(String nodeId, String host, int port) {
        if (!connectedPeers.containsKey(nodeId)) {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();

            DeathNodeGrpc.DeathNodeStub stub = DeathNodeGrpc.newStub(channel);

            connectedPeers.put(nodeId, channel);
            peerStubs.put(nodeId, stub);

            System.out.println("✓ Peer registrado: " + nodeId + " @ " + host + ":" + port);
        }
    }

    public void ping(String nodeId) {
        DeathNodeProtoBuf.Empty empty = DeathNodeProtoBuf.Empty.newBuilder().build();
        peerStubs.get(nodeId).listReports(empty, new StreamObserver<DeathNodeProtoBuf.Empty>() {
            @Override
            public void onNext(DeathNodeProtoBuf.Empty empty) {
                    System.out.println("  → Report propagado para peer: " + nodeId);
            }

            @Override
            public void onError(Throwable throwable) {
                    throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;

        ServerNode server = new ServerNode(port);
        server.start();

        Scanner in = new Scanner(System.in);

        while (true) {
            System.out.print("\n> ");
            String line = in.nextLine().trim();

            if (line.equals("exit")) {
                break;
            } else if (line.startsWith("register ")) {
                String[] parts = line.split(" ", 4);
                if (parts.length == 4) {
                    String nodeId = parts[1];
                    String host = parts[2];
                    int nodePort = Integer.parseInt(parts[3]);
                    server.registerPeer(nodeId, host, nodePort);
                }
            } else if (line.startsWith("ping ")) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    String nodeId = parts[1];
                    System.out.println("Pinging: " + nodeId);
                    server.ping(nodeId);
                }
            }
        }
        server.blockUntilShutdown();
    }

}
