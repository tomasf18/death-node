package server;

import entity.Report;
import grpc_generated.DeathNodeGrpc;
import grpc_generated.DeathNodeProtoBuf;
import io.grpc.stub.StreamObserver;

import java.util.Iterator;

public class ServerStub extends DeathNodeGrpc.DeathNodeImplBase {

    private static final ServerApp app = new ServerApp();

    public ServerStub() {
    }

    @Override
    public void createReport(DeathNodeProtoBuf.CreateReportArgs request,
                             StreamObserver<DeathNodeProtoBuf.CreateReportReply> responseObserver) {

        DeathNodeProtoBuf.Report report = request.getReport();
        int sn = app.createReport(new Report(
                report.getId(),
                report.getTimestamp(),
                report.getAuthor(),
                report.getContent()
        ));
        System.out.println("created report → seq=" + sn);

        DeathNodeProtoBuf.CreateReportReply reply = DeathNodeProtoBuf.CreateReportReply.newBuilder()
                .setAck(Integer.toString(sn))
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void listReports(DeathNodeProtoBuf.Empty request, StreamObserver<DeathNodeProtoBuf.Empty> responseObserver) {
        app.listReports();

        DeathNodeProtoBuf.Empty reply = DeathNodeProtoBuf.Empty.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void sync(DeathNodeProtoBuf.SyncRequest request, StreamObserver<DeathNodeProtoBuf.SyncReply> responseObserver) {
        int currentVersion = app.getVersion();
        Iterator<Report> it = app.getReports(request.getVersion(), currentVersion);
        DeathNodeProtoBuf.SyncReply.Builder reply = DeathNodeProtoBuf.SyncReply.newBuilder();
        while (it.hasNext()) {
            Report report = it.next();
            reply.addReports(DeathNodeProtoBuf.Report.newBuilder()
                    .setId(report.getId())
                    .setTimestamp(report.getTimestamp())
                    .setAuthor(report.getAuthor())
                    .setContent(report.getContent())
                    .build()
            );
            responseObserver.onNext(reply.build());
            responseObserver.onCompleted();
        }


    }
}
