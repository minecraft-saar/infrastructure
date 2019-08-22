package de.saar.minecraft.matchmaker;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import de.saar.minecraft.architect.ArchitectGrpc;
import de.saar.minecraft.architect.GameDataWithId;
import de.saar.minecraft.matchmaker.db.Tables;
import de.saar.minecraft.matchmaker.db.enums.GameLogsDirection;
import de.saar.minecraft.matchmaker.db.tables.GameLogs;
import de.saar.minecraft.matchmaker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.Void;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;

public class Matchmaker {
    private Server server;
    private static int nextGameId = 1;

    private ArchitectGrpc.ArchitectStub nonblockingArchitectStub;
    private ArchitectGrpc.ArchitectBlockingStub blockingArchitectStub;
    private ManagedChannel channelToArchitect;

    private final MatchmakerConfiguration config;
    private DSLContext jooq = null;
    private Connection conn = null;

    private final TextFormat.Printer pr = TextFormat.printer();

    public Matchmaker(MatchmakerConfiguration config) {
        this.config = config;
        jooq = setupDatabase();
    }

    private DSLContext setupDatabase() {
        if( config.getDatabase() != null ) {
            try {
                conn = DriverManager.getConnection(config.getDatabase().getUrl(), config.getDatabase().getUsername(), config.getDatabase().getPassword());
                DSLContext ret = DSL.using(conn, SQLDialect.valueOf(config.getDatabase().getSqlDialect()));
                return ret;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        System.err.println("Could not connect to database, terminating.");
        System.exit(0);
        return null;
    }

    private void start() throws IOException {
        // connect to architect server
        channelToArchitect = ManagedChannelBuilder.forAddress(config.getArchitectServer().getHostname(), config.getArchitectServer().getPort())
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        nonblockingArchitectStub = ArchitectGrpc.newStub(channelToArchitect);
        blockingArchitectStub = ArchitectGrpc.newBlockingStub(channelToArchitect);
        System.err.println("Connected to architect server.");

        // TODO fail correctly if Architect server is not running


        // open Matchmaker service
        int port = config.getPort();
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
            log(request.getGameId(), request, GameLogsDirection.FROMCLIENT);
            nonblockingArchitectStub.handleStatusInformation(request, new DelegatingStreamObserver<>(request.getGameId(), responseObserver));
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

    private class DelegatingStreamObserver<E extends MessageOrBuilder> implements StreamObserver<E> {
        private StreamObserver<E> toClient;
        private int gameId;

        public DelegatingStreamObserver(int gameId, StreamObserver<E> toClient) {
            this.toClient = toClient;
            this.gameId = gameId;
        }

        @Override
        public void onNext(E value) {
            toClient.onNext(value);
            log(gameId, value, GameLogsDirection.PASSTOCLIENT);
        }

        @Override
        public void onError(Throwable t) {
            log(gameId, t, GameLogsDirection.PASSTOCLIENT);
            toClient.onError(t);
        }

        @Override
        public void onCompleted() {
            toClient.onCompleted();
        }
    }

    private void log(int gameid, MessageOrBuilder message, GameLogsDirection direction) {
        String messageStr = pr.printToString(message);

        GameLogsRecord rec = jooq.newRecord(Tables.GAME_LOGS);
        rec.setGameid(gameid);
        rec.setDirection(direction);
        rec.setMessage(messageStr);
        rec.setTimestamp(new Timestamp(System.currentTimeMillis()));
        rec.store();
    }

    private void log(int gameid, Throwable message, GameLogsDirection direction) {
        String messageStr = "ERROR: " + message.toString();

        GameLogsRecord rec = jooq.newRecord(Tables.GAME_LOGS);
        rec.setGameid(gameid);
        rec.setDirection(direction);
        rec.setMessage(messageStr);
        rec.setTimestamp(new Timestamp(System.currentTimeMillis()));
        rec.store();
    }

    private void log(String message) {
        System.err.println("Logged: " + message);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        MatchmakerConfiguration config = MatchmakerConfiguration.loadYaml(new FileReader("matchmaker-config.yaml"));

        System.err.println("jdbc: " + config.getDatabase());
        System.err.println("architect: " + config.getArchitectServer().getPort());

        Matchmaker server = new Matchmaker(config);
        server.start();
        server.blockUntilShutdown();
    }
}
