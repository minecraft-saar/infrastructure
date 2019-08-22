package de.saar.minecraft.matchmaker;

import de.saar.minecraft.architect.ArchitectGrpc;
import de.saar.minecraft.architect.GameDataWithId;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.Void;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public class Matchmaker {
    private Server server;
    private static int nextGameId = 1;

    private ArchitectGrpc.ArchitectStub nonblockingArchitectStub;
    private ArchitectGrpc.ArchitectBlockingStub blockingArchitectStub;
    private ManagedChannel channelToArchitect;

    private void start() throws IOException {
        // connect to architect server; TODO: make this configurable
        channelToArchitect = ManagedChannelBuilder.forAddress("localhost", 10000)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        nonblockingArchitectStub = ArchitectGrpc.newStub(channelToArchitect);
        blockingArchitectStub = ArchitectGrpc.newBlockingStub(channelToArchitect);
        System.err.println("Connected to architect server.");

        // TODO fail correctly if Architect server is not running


        // open Matchmaker service
        int port = 2802;
        server = ServerBuilder.forPort(port)
                .addService(new MatchmakerImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                Matchmaker.this.stop();
                System.err.println("*** server shut down");
            }
        });

        System.err.println("Matchmaker service running.");
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

    private class MatchmakerImpl extends MatchmakerGrpc.MatchmakerImplBase {
        /**
         * Handles the start of a game. Creates a record for this game in the database
         * and returns a unique game ID to the client.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void startGame(GameData request, StreamObserver<GameId> responseObserver) {
            int id = nextGameId++;

            log("rcvd: " + request);

            // tell architect about the new game
            GameDataWithId mGameDataWithId = GameDataWithId.newBuilder().setId(id).setGameData(request.getGameData()).build();
            System.err.println("x");
            Void x = blockingArchitectStub.startGame(mGameDataWithId);
            System.err.println("got " + x);
//            nonblockingArchitectStub.startGame(mGameDataWithId, new DummyStreamObserver<>());
            System.err.println("y");

            // tell client the game ID
            GameId idMessage = GameId.newBuilder().setId(id).build();
            responseObserver.onNext(idMessage);
            responseObserver.onCompleted();
            log("sent: " + idMessage);
        }

        /**
         * Handles a status update from the Minecraft server. Optionally, sends back a TextMessage
         * with a string that is to be displayed to the user. Because calculating this text message
         * may take a long time, this method should be called asynchronously (with a non-blocking stub).
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void handleStatusInformation(StatusMessage request, StreamObserver<TextMessage> responseObserver) {
            log("rcvd: " + request);
            nonblockingArchitectStub.handleStatusInformation(request, new DelegatingStreamObserver<>(responseObserver));
        }
    }

    private static class DummyStreamObserver<E> implements StreamObserver<E> {

        @Override
        public void onNext(E value) {

        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onCompleted() {

        }
    }

    private class DelegatingStreamObserver<E> implements StreamObserver<E> {
        private StreamObserver<E> toClient;

        public DelegatingStreamObserver(StreamObserver<E> toClient) {
            this.toClient = toClient;
        }

        @Override
        public void onNext(E value) {
            log("sent: " + value);
            toClient.onNext(value);
        }

        @Override
        public void onError(Throwable t) {
            log("error: " + t.toString());
            toClient.onError(t);
        }

        @Override
        public void onCompleted() {
            toClient.onCompleted();
        }
    }

    private void log(String message) {
        System.err.println("Logged: " + message);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Matchmaker server = new Matchmaker();
        server.start();
        server.blockUntilShutdown();
    }
}
