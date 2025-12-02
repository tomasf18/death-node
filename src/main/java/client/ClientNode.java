package client;

import entity.Report;
import grpc_generated.DeathNodeGrpc;
import grpc_generated.DeathNodeProtoBuf;
import grpc_generated.DeathNodeProtoBuf.*;
import io.grpc.*;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class ClientNode {

    private final String nodeId;
    private final int localPort;

    // Cliente gRPC (para falar com servidor)
    private final ManagedChannel channel;
    private final DeathNodeGrpc.DeathNodeBlockingStub blockingStub;

    // Servidor gRPC (para receber notificações)
    private final Server localServer;

    private final ClientApp app;

    public ClientNode(String nodeId, String serverHost, int serverPort, int localPort) {
        this.nodeId = nodeId;
        this.localPort = localPort;

        // Setup cliente
        this.channel = ManagedChannelBuilder
                .forAddress(serverHost, serverPort)
                .usePlaintext()
                .build();
        blockingStub = DeathNodeGrpc.newBlockingStub(channel);

        // Setup servidor local
        this.localServer = ServerBuilder.forPort(localPort)
                .addService(new ClientStub())
                .build();

        this.app = new ClientApp();
    }

    public void start() throws IOException {
        localServer.start();
        System.out.println("✓ Cliente " + nodeId + " iniciado (porta local: " + localPort + ")");
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void createReport(String author, String content) {

        Report report = new Report(
                author, content
        );

        // Cria o request
        DeathNodeProtoBuf.Report r = DeathNodeProtoBuf.Report.newBuilder()
                .setId(report.getId())
                .setAuthor(report.getAuthor())
                .setTimestamp(report.getTimestamp())
                .setContent(report.getContent())
                .build();
        CreateReportArgs request = CreateReportArgs.newBuilder()
                .setReport(r)
                .build();

        CreateReportReply response;
        try {
            // Faz a chamada gRPC
            response = blockingStub.createReport(request);
            System.out.println("Server response: " + response.getAck());
            response.getAck();

        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        }
    }

    public void listReports() {
        DeathNodeProtoBuf.Empty request = DeathNodeProtoBuf.Empty.newBuilder().build();
        blockingStub.listReports(request);
    }

    public void sync() {
        int localVersion = app.getLocalVersion();

        SyncRequest request = SyncRequest.newBuilder()
                .setVersion(localVersion)
                .build();

        SyncReply response;
        try {
            response = blockingStub.sync(request);
            for (DeathNodeProtoBuf.Report report : response.getReportsList()) {
                app.storeReport(new Report(
                        report.getId(),
                        report.getAuthor(),
                        report.getTimestamp(),
                        report.getContent()
                ));
            }

        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        if (args.length < 3) {
            System.err.println("Uso: DeathNodeClient <nodeId> <serverHost:serverPort> <localPort>");
            System.err.println("Exemplo: DeathNodeClient client1 localhost:9090 9091");
            System.exit(1);
        }

        String nodeId = args[0];
        String[] serverAddr = args[1].split(":");
        String serverHost = serverAddr[0];
        int serverPort = Integer.parseInt(serverAddr[1]);
        int localPort = Integer.parseInt(args[2]);

        ClientNode client = new ClientNode(nodeId, serverHost, serverPort, localPort);
        client.start();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n> ");
            String line = scanner.nextLine().trim();

            if (line.equals("exit")) {
                break;
            } else if (line.startsWith("submit ")) {
                String[] parts = line.split(" ", 3);
                if (parts.length >= 3) {
                    String author = parts[1];
                    String content = parts[2];

                    client.createReport(author, content);
                } else {
                    System.out.println("Uso: submit <autor> <conteúdo>");
                }
            } else if (line.startsWith("list")) {
                client.listReports();
            } else if (line.startsWith("sync")) {
                client.sync();
            }

            else {
                System.out.println("Comando desconhecido");
            }
        }

        client.shutdown();
        System.exit(0);

    }
}
