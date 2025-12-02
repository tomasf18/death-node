package grpc_generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class DeathNodeGrpc {

  private DeathNodeGrpc() {}

  public static final java.lang.String SERVICE_NAME = "DeathNode";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.CreateReportArgs,
      grpc_generated.DeathNodeProtoBuf.CreateReportReply> getCreateReportMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateReport",
      requestType = grpc_generated.DeathNodeProtoBuf.CreateReportArgs.class,
      responseType = grpc_generated.DeathNodeProtoBuf.CreateReportReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.CreateReportArgs,
      grpc_generated.DeathNodeProtoBuf.CreateReportReply> getCreateReportMethod() {
    io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.CreateReportArgs, grpc_generated.DeathNodeProtoBuf.CreateReportReply> getCreateReportMethod;
    if ((getCreateReportMethod = DeathNodeGrpc.getCreateReportMethod) == null) {
      synchronized (DeathNodeGrpc.class) {
        if ((getCreateReportMethod = DeathNodeGrpc.getCreateReportMethod) == null) {
          DeathNodeGrpc.getCreateReportMethod = getCreateReportMethod =
              io.grpc.MethodDescriptor.<grpc_generated.DeathNodeProtoBuf.CreateReportArgs, grpc_generated.DeathNodeProtoBuf.CreateReportReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateReport"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  grpc_generated.DeathNodeProtoBuf.CreateReportArgs.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  grpc_generated.DeathNodeProtoBuf.CreateReportReply.getDefaultInstance()))
              .setSchemaDescriptor(new DeathNodeMethodDescriptorSupplier("CreateReport"))
              .build();
        }
      }
    }
    return getCreateReportMethod;
  }

  private static volatile io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.Empty,
      grpc_generated.DeathNodeProtoBuf.Empty> getListReportsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListReports",
      requestType = grpc_generated.DeathNodeProtoBuf.Empty.class,
      responseType = grpc_generated.DeathNodeProtoBuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.Empty,
      grpc_generated.DeathNodeProtoBuf.Empty> getListReportsMethod() {
    io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.Empty, grpc_generated.DeathNodeProtoBuf.Empty> getListReportsMethod;
    if ((getListReportsMethod = DeathNodeGrpc.getListReportsMethod) == null) {
      synchronized (DeathNodeGrpc.class) {
        if ((getListReportsMethod = DeathNodeGrpc.getListReportsMethod) == null) {
          DeathNodeGrpc.getListReportsMethod = getListReportsMethod =
              io.grpc.MethodDescriptor.<grpc_generated.DeathNodeProtoBuf.Empty, grpc_generated.DeathNodeProtoBuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListReports"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  grpc_generated.DeathNodeProtoBuf.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  grpc_generated.DeathNodeProtoBuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new DeathNodeMethodDescriptorSupplier("ListReports"))
              .build();
        }
      }
    }
    return getListReportsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.SyncRequest,
      grpc_generated.DeathNodeProtoBuf.SyncReply> getSyncMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Sync",
      requestType = grpc_generated.DeathNodeProtoBuf.SyncRequest.class,
      responseType = grpc_generated.DeathNodeProtoBuf.SyncReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.SyncRequest,
      grpc_generated.DeathNodeProtoBuf.SyncReply> getSyncMethod() {
    io.grpc.MethodDescriptor<grpc_generated.DeathNodeProtoBuf.SyncRequest, grpc_generated.DeathNodeProtoBuf.SyncReply> getSyncMethod;
    if ((getSyncMethod = DeathNodeGrpc.getSyncMethod) == null) {
      synchronized (DeathNodeGrpc.class) {
        if ((getSyncMethod = DeathNodeGrpc.getSyncMethod) == null) {
          DeathNodeGrpc.getSyncMethod = getSyncMethod =
              io.grpc.MethodDescriptor.<grpc_generated.DeathNodeProtoBuf.SyncRequest, grpc_generated.DeathNodeProtoBuf.SyncReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Sync"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  grpc_generated.DeathNodeProtoBuf.SyncRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  grpc_generated.DeathNodeProtoBuf.SyncReply.getDefaultInstance()))
              .setSchemaDescriptor(new DeathNodeMethodDescriptorSupplier("Sync"))
              .build();
        }
      }
    }
    return getSyncMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DeathNodeStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeathNodeStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeathNodeStub>() {
        @java.lang.Override
        public DeathNodeStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeathNodeStub(channel, callOptions);
        }
      };
    return DeathNodeStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static DeathNodeBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeathNodeBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeathNodeBlockingV2Stub>() {
        @java.lang.Override
        public DeathNodeBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeathNodeBlockingV2Stub(channel, callOptions);
        }
      };
    return DeathNodeBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DeathNodeBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeathNodeBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeathNodeBlockingStub>() {
        @java.lang.Override
        public DeathNodeBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeathNodeBlockingStub(channel, callOptions);
        }
      };
    return DeathNodeBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DeathNodeFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeathNodeFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeathNodeFutureStub>() {
        @java.lang.Override
        public DeathNodeFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeathNodeFutureStub(channel, callOptions);
        }
      };
    return DeathNodeFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void createReport(grpc_generated.DeathNodeProtoBuf.CreateReportArgs request,
        io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.CreateReportReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateReportMethod(), responseObserver);
    }

    /**
     */
    default void listReports(grpc_generated.DeathNodeProtoBuf.Empty request,
        io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListReportsMethod(), responseObserver);
    }

    /**
     */
    default void sync(grpc_generated.DeathNodeProtoBuf.SyncRequest request,
        io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.SyncReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSyncMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DeathNode.
   */
  public static abstract class DeathNodeImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DeathNodeGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DeathNode.
   */
  public static final class DeathNodeStub
      extends io.grpc.stub.AbstractAsyncStub<DeathNodeStub> {
    private DeathNodeStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeathNodeStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeathNodeStub(channel, callOptions);
    }

    /**
     */
    public void createReport(grpc_generated.DeathNodeProtoBuf.CreateReportArgs request,
        io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.CreateReportReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateReportMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listReports(grpc_generated.DeathNodeProtoBuf.Empty request,
        io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListReportsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void sync(grpc_generated.DeathNodeProtoBuf.SyncRequest request,
        io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.SyncReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSyncMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DeathNode.
   */
  public static final class DeathNodeBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<DeathNodeBlockingV2Stub> {
    private DeathNodeBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeathNodeBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeathNodeBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public grpc_generated.DeathNodeProtoBuf.CreateReportReply createReport(grpc_generated.DeathNodeProtoBuf.CreateReportArgs request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCreateReportMethod(), getCallOptions(), request);
    }

    /**
     */
    public grpc_generated.DeathNodeProtoBuf.Empty listReports(grpc_generated.DeathNodeProtoBuf.Empty request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getListReportsMethod(), getCallOptions(), request);
    }

    /**
     */
    public grpc_generated.DeathNodeProtoBuf.SyncReply sync(grpc_generated.DeathNodeProtoBuf.SyncRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getSyncMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service DeathNode.
   */
  public static final class DeathNodeBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DeathNodeBlockingStub> {
    private DeathNodeBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeathNodeBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeathNodeBlockingStub(channel, callOptions);
    }

    /**
     */
    public grpc_generated.DeathNodeProtoBuf.CreateReportReply createReport(grpc_generated.DeathNodeProtoBuf.CreateReportArgs request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateReportMethod(), getCallOptions(), request);
    }

    /**
     */
    public grpc_generated.DeathNodeProtoBuf.Empty listReports(grpc_generated.DeathNodeProtoBuf.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListReportsMethod(), getCallOptions(), request);
    }

    /**
     */
    public grpc_generated.DeathNodeProtoBuf.SyncReply sync(grpc_generated.DeathNodeProtoBuf.SyncRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSyncMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DeathNode.
   */
  public static final class DeathNodeFutureStub
      extends io.grpc.stub.AbstractFutureStub<DeathNodeFutureStub> {
    private DeathNodeFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeathNodeFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeathNodeFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<grpc_generated.DeathNodeProtoBuf.CreateReportReply> createReport(
        grpc_generated.DeathNodeProtoBuf.CreateReportArgs request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateReportMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<grpc_generated.DeathNodeProtoBuf.Empty> listReports(
        grpc_generated.DeathNodeProtoBuf.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListReportsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<grpc_generated.DeathNodeProtoBuf.SyncReply> sync(
        grpc_generated.DeathNodeProtoBuf.SyncRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSyncMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_REPORT = 0;
  private static final int METHODID_LIST_REPORTS = 1;
  private static final int METHODID_SYNC = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CREATE_REPORT:
          serviceImpl.createReport((grpc_generated.DeathNodeProtoBuf.CreateReportArgs) request,
              (io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.CreateReportReply>) responseObserver);
          break;
        case METHODID_LIST_REPORTS:
          serviceImpl.listReports((grpc_generated.DeathNodeProtoBuf.Empty) request,
              (io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.Empty>) responseObserver);
          break;
        case METHODID_SYNC:
          serviceImpl.sync((grpc_generated.DeathNodeProtoBuf.SyncRequest) request,
              (io.grpc.stub.StreamObserver<grpc_generated.DeathNodeProtoBuf.SyncReply>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getCreateReportMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              grpc_generated.DeathNodeProtoBuf.CreateReportArgs,
              grpc_generated.DeathNodeProtoBuf.CreateReportReply>(
                service, METHODID_CREATE_REPORT)))
        .addMethod(
          getListReportsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              grpc_generated.DeathNodeProtoBuf.Empty,
              grpc_generated.DeathNodeProtoBuf.Empty>(
                service, METHODID_LIST_REPORTS)))
        .addMethod(
          getSyncMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              grpc_generated.DeathNodeProtoBuf.SyncRequest,
              grpc_generated.DeathNodeProtoBuf.SyncReply>(
                service, METHODID_SYNC)))
        .build();
  }

  private static abstract class DeathNodeBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DeathNodeBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return grpc_generated.DeathNodeProtoBuf.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DeathNode");
    }
  }

  private static final class DeathNodeFileDescriptorSupplier
      extends DeathNodeBaseDescriptorSupplier {
    DeathNodeFileDescriptorSupplier() {}
  }

  private static final class DeathNodeMethodDescriptorSupplier
      extends DeathNodeBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    DeathNodeMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (DeathNodeGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DeathNodeFileDescriptorSupplier())
              .addMethod(getCreateReportMethod())
              .addMethod(getListReportsMethod())
              .addMethod(getSyncMethod())
              .build();
        }
      }
    }
    return result;
  }
}
