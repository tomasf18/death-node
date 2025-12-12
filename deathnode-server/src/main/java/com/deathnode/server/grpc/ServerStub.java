package com.deathnode.server.grpc;

import com.deathnode.server.ServerApp;
import io.grpc.stub.StreamObserver;

public class ServerStub extends SyncServiceGrpc.SyncServiceImplBase {

    private final ServerApp app;
    public ServerStub(ServerApp app) {
        this.app = app;
    }

    /**
     * Server initializes sync procedure
     */
    @Override
    public void requestSync(SyncProto.ClientMessage request, StreamObserver<SyncProto.Ack> responseObserver) {

        SyncProto.Ack ack = SyncProto.Ack.newBuilder()
                .setMessage("")
                .build();
        responseObserver.onNext(ack);
        responseObserver.onCompleted();

        app.collectReports();
    }

}
