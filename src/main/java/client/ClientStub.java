package client;

import entity.Report;
import grpc_generated.DeathNodeGrpc;
import grpc_generated.DeathNodeProtoBuf;
import io.grpc.stub.StreamObserver;

public class ClientStub extends DeathNodeGrpc.DeathNodeImplBase {

    final static ClientApp app = new ClientApp();
}
