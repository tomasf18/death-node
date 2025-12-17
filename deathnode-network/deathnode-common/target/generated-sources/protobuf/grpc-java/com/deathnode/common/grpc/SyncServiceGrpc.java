package com.deathnode.common.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * Bidirectional streaming RPC for synchronization.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class SyncServiceGrpc {

  private SyncServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "deathnode.sync.SyncService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.deathnode.common.grpc.ClientMessage,
      com.deathnode.common.grpc.ServerMessage> getSyncMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Sync",
      requestType = com.deathnode.common.grpc.ClientMessage.class,
      responseType = com.deathnode.common.grpc.ServerMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.deathnode.common.grpc.ClientMessage,
      com.deathnode.common.grpc.ServerMessage> getSyncMethod() {
    io.grpc.MethodDescriptor<com.deathnode.common.grpc.ClientMessage, com.deathnode.common.grpc.ServerMessage> getSyncMethod;
    if ((getSyncMethod = SyncServiceGrpc.getSyncMethod) == null) {
      synchronized (SyncServiceGrpc.class) {
        if ((getSyncMethod = SyncServiceGrpc.getSyncMethod) == null) {
          SyncServiceGrpc.getSyncMethod = getSyncMethod =
              io.grpc.MethodDescriptor.<com.deathnode.common.grpc.ClientMessage, com.deathnode.common.grpc.ServerMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Sync"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.deathnode.common.grpc.ClientMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.deathnode.common.grpc.ServerMessage.getDefaultInstance()))
              .setSchemaDescriptor(new SyncServiceMethodDescriptorSupplier("Sync"))
              .build();
        }
      }
    }
    return getSyncMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SyncServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceStub>() {
        @java.lang.Override
        public SyncServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceStub(channel, callOptions);
        }
      };
    return SyncServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static SyncServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceBlockingV2Stub>() {
        @java.lang.Override
        public SyncServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return SyncServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SyncServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceBlockingStub>() {
        @java.lang.Override
        public SyncServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceBlockingStub(channel, callOptions);
        }
      };
    return SyncServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SyncServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceFutureStub>() {
        @java.lang.Override
        public SyncServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceFutureStub(channel, callOptions);
        }
      };
    return SyncServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * Bidirectional streaming RPC for synchronization.
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default io.grpc.stub.StreamObserver<com.deathnode.common.grpc.ClientMessage> sync(
        io.grpc.stub.StreamObserver<com.deathnode.common.grpc.ServerMessage> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getSyncMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service SyncService.
   * <pre>
   **
   * Bidirectional streaming RPC for synchronization.
   * </pre>
   */
  public static abstract class SyncServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SyncServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service SyncService.
   * <pre>
   **
   * Bidirectional streaming RPC for synchronization.
   * </pre>
   */
  public static final class SyncServiceStub
      extends io.grpc.stub.AbstractAsyncStub<SyncServiceStub> {
    private SyncServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceStub(channel, callOptions);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.deathnode.common.grpc.ClientMessage> sync(
        io.grpc.stub.StreamObserver<com.deathnode.common.grpc.ServerMessage> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getSyncMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service SyncService.
   * <pre>
   **
   * Bidirectional streaming RPC for synchronization.
   * </pre>
   */
  public static final class SyncServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<SyncServiceBlockingV2Stub> {
    private SyncServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<com.deathnode.common.grpc.ClientMessage, com.deathnode.common.grpc.ServerMessage>
        sync() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getSyncMethod(), getCallOptions());
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service SyncService.
   * <pre>
   **
   * Bidirectional streaming RPC for synchronization.
   * </pre>
   */
  public static final class SyncServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SyncServiceBlockingStub> {
    private SyncServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service SyncService.
   * <pre>
   **
   * Bidirectional streaming RPC for synchronization.
   * </pre>
   */
  public static final class SyncServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<SyncServiceFutureStub> {
    private SyncServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_SYNC = 0;

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
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SYNC:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.sync(
              (io.grpc.stub.StreamObserver<com.deathnode.common.grpc.ServerMessage>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSyncMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.deathnode.common.grpc.ClientMessage,
              com.deathnode.common.grpc.ServerMessage>(
                service, METHODID_SYNC)))
        .build();
  }

  private static abstract class SyncServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    SyncServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.deathnode.common.grpc.SyncProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("SyncService");
    }
  }

  private static final class SyncServiceFileDescriptorSupplier
      extends SyncServiceBaseDescriptorSupplier {
    SyncServiceFileDescriptorSupplier() {}
  }

  private static final class SyncServiceMethodDescriptorSupplier
      extends SyncServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    SyncServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (SyncServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new SyncServiceFileDescriptorSupplier())
              .addMethod(getSyncMethod())
              .build();
        }
      }
    }
    return result;
  }
}
