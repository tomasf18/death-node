package com.deathnode.client;

import com.deathnode.server.grpc.SyncProto;
import com.deathnode.server.grpc.SyncServiceGrpc;
import io.grpc.stub.StreamObserver;

public class ClientStub extends SyncServiceGrpc.SyncServiceImplBase {

    /**
     * Client flushes its buffer to server
     */
    public void sync(){}

    /**
     * Client receives block from server
     */
    @Override
    public void sendBlock(SyncProto.ServerMessage request, StreamObserver<SyncProto.Ack> responseObserver) {
        // TODO
    }

}
