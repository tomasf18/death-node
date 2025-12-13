package com.deathnode.client.grpc;

import com.deathnode.server.grpc.Connections;
import com.deathnode.server.grpc.SyncProto;
import com.deathnode.server.grpc.SyncServiceGrpc;

public class Client {

    private final SyncServiceGrpc.SyncServiceBlockingStub stub;

    public Client() {
        Connections connections = Connections.getInstance();
        this.stub = connections.getBlockingStub("server");
    }

    /**
     * Client starts a synchronization round in the system
     */
    public void requestSync() {

        // FIXME: Redo this (this is a premature version)

        SyncProto.Ack ack = stub.requestSync(SyncProto.ClientMessage.newBuilder()
                        .setHello(SyncProto.Hello.newBuilder()
                                .setNodeId("test")
                                .build())
                .build());

        System.out.println("Sync ack: " + ack.getMessage());
    }

}
