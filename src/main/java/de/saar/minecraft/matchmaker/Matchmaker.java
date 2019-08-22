package de.saar.minecraft.matchmaker;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public class Matchmaker {
    private Server server;
    private static int nextGameId = 1;

    private void start() throws IOException {
        int port = 2802;
        server = ServerBuilder.forPort(port)
                .addService(new MatchmakerImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                Matchmaker.this.stop();
                System.err.println("*** server shut down");
            }
        });

        System.err.println("Server running.");
    }

    private void stop() {
        if( server != null ) {
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

    private static class MatchmakerImpl extends MatchmakerGrpc.MatchmakerImplBase {
        @Override
        public void startGame(GameData request, StreamObserver<GameId> responseObserver) {
            int id = nextGameId++;

            System.err.printf("start game %s -> id %d\n", request.getGameIdentifier(), id);
            GameId idMessage = GameId.newBuilder().setId(id).build();
            responseObserver.onNext(idMessage);
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Matchmaker server = new Matchmaker();
        server.start();
        server.blockUntilShutdown();
    }
}
