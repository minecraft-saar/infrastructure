package de.saar.minecraft.architect;

import com.google.rpc.Code;
import com.google.rpc.Status;
import de.saar.minecraft.shared.GameId;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.Void;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A server which provides access to Architects of one type.
 * This server responds to gRPC requests from the Broker, creates
 * and destroys Architect instances as needed, and routes further
 * requests from one player on the Minecraft server to their
 * corresponding Architect instance.
 *
 */
public class ArchitectServer {
    private Server server;
    private Map<Integer, Architect> runningArchitects;
    private ArchitectFactory factory;
    private int port;

    public ArchitectServer(int port, ArchitectFactory factory) {
        this.factory = factory;
        this.port = port;
        runningArchitects = new HashMap<>();
    }

    private void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new ArchitectImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                ArchitectServer.this.stop();
                System.err.println("*** server shut down");
            }
        });

        String info = factory.build().getArchitectInformation();

        System.err.printf("Architect server running on port %d.\n", port);
        System.err.println(info);
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private class ArchitectImpl extends ArchitectGrpc.ArchitectImplBase {
        /**
         * This method is called once when the Broker connects to the Architect server
         * for the first time. This is to make sure the Architect server is
         * actually available. The method returns an information string about
         * the type of architect (including perhaps its version) that is served
         * by this ArchitectServer.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void hello(Void request, StreamObserver<ArchitectInformation> responseObserver) {
            Architect arch = factory.build();
            // no need to initialize it, it will disappear right away

            responseObserver.onNext(ArchitectInformation.newBuilder().setInfo(arch.getArchitectInformation()).build());
            responseObserver.onCompleted();
        }

        /**
         * Creates a new architect instance for the new game.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void startGame(GameDataWithId request, StreamObserver<Void> responseObserver) {
            Architect arch = factory.build();
            arch.initialize();
            runningArchitects.put(request.getId(), arch);

            responseObserver.onNext(Void.newBuilder().build());
            responseObserver.onCompleted();

            System.err.printf("architect for id %d: %s\n", request.getId(), arch);
        }

        /**
         * Marks the given game as finished and shuts down its corresponding
         * architect instance.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void endGame(GameId request, StreamObserver<Void> responseObserver) {
            runningArchitects.remove(request.getId());
            System.err.printf("architect for id %d finished\n", request.getId());
            responseObserver.onNext(Void.newBuilder().build());
            responseObserver.onCompleted();
        }

        /**
         * Delegates the status message to the architect for the given game ID.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void handleStatusInformation(StatusMessage request, StreamObserver<TextMessage> responseObserver) {
            Architect arch = runningArchitects.get(request.getGameId());

            if (arch == null) {
                Status status = Status.newBuilder()
                        .setCode(Code.INVALID_ARGUMENT.getNumber())
                        .setMessage("No architect running for game ID " + request.getGameId())
                        .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
            } else {
                arch.handleStatusInformation(request, responseObserver);
            }
        }
    }


    /**
     * Starts an architect server.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        ArchitectFactory factory = () -> new DummyArchitect();
        ArchitectServer server = new ArchitectServer(10000, factory);
        server.start();
        server.blockUntilShutdown();
    }
}
