package com.deathnode.server;

import com.deathnode.server.grpc.Client;
import com.deathnode.server.grpc.Connections;

public class ServerApp {

    private final Client client;

    public ServerApp() {
        this.client = new Client();
    }

    public void collectReports() {
        // TODO call for collection using the client
    }
}
