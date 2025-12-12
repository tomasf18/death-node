package com.deathnode.server;

import com.deathnode.server.grpc.Connections;

public class ServerApp {

    private final Connections connections;

    public ServerApp(Connections connections) {
        this.connections = connections;
    }

    public void collectReports() {
        // TODO call for collection in peerStubs
    }
}
