package com.deathnode.client.grpc;

import com.deathnode.server.grpc.Connections;
import com.deathnode.server.grpc.SyncServiceGrpc;

public class Client {

    private final SyncServiceGrpc.SyncServiceBlockingStub stub;

    public Client() {
        Connections connections = Connections.getInstance();
        this.stub = SyncServiceGrpc.newBlockingStub(connections.getChannel("server"));
    }

}
