package com.deathnode.server.grpc;

import java.util.ArrayList;
import java.util.List;

public class Client {

    private List<SyncServiceGrpc.SyncServiceStub> stubs;

    /**
     * Server's gRPC client:
     * Issues buffer collection requests and broadcasts blocks.
     */
    public Client() {
        Connections connections = Connections.getInstance();
        this.stubs = new ArrayList<>();
        connections.getStubs(stubs);
    }

    // TODO Buffer collection

    // TODO Block broadcast

}
