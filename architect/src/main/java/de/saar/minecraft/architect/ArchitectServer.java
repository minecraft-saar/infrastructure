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

public class ArchitectServer {
    private Server server;
    private Map<Integer, Architect> runningArchitects;

    public ArchitectServer() {
        runningArchitects = new HashMap<>();
    }

    private void start() throws IOException {
        int port = 10000;

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

        System.err.println("Architect server running.");
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
         * Creates a new architect instance for the new game.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void startGame(GameDataWithId request, StreamObserver<ArchitectInformation> responseObserver) {
            Architect arch = new DummyArchitect();
            runningArchitects.put(request.getId(), arch);
            responseObserver.onNext(ArchitectInformation.newBuilder().setInfo(arch.getArchitectInformation()).build());
            responseObserver.onCompleted();

            System.err.printf("architect for id %d: %s\n", request.getId(), arch);
        }

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
        ArchitectServer server = new ArchitectServer();
        server.start();
        server.blockUntilShutdown();
    }
}
