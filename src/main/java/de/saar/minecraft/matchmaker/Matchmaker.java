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

            System.err.printf("start game %s -> id %d\n", request.getGameData(), id);
            GameId idMessage = GameId.newBuilder().setId(id).build();
            responseObserver.onNext(idMessage);
            responseObserver.onCompleted();
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
            int x = request.getX();
            int gameId = request.getGameId();

            // spawn a thread for a long-running computation
            new Thread() {
                @Override
                public void run() {
                    String text = "your x was " + x;
                    TextMessage mText = TextMessage.newBuilder().setGameId(gameId).setText(text).build();

                    // delay for a bit
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // send the text message back to the client
                    responseObserver.onNext(mText);
                    responseObserver.onCompleted();
                }
            }.start();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Matchmaker server = new Matchmaker();
        server.start();
        server.blockUntilShutdown();
    }
}
