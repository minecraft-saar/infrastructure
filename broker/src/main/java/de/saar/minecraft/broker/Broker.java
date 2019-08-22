package de.saar.minecraft.broker;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import de.saar.minecraft.architect.ArchitectGrpc;
import de.saar.minecraft.architect.ArchitectInformation;
import de.saar.minecraft.architect.GameDataWithId;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.enums.GameLogsDirection;
import de.saar.minecraft.broker.db.enums.GamesStatus;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import de.saar.minecraft.shared.GameId;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.Void;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
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

public class Broker {
    private static final String MESSAGE_TYPE_ERROR = "ERROR";
    private static final String MESSAGE_TYPE_LOG = "LOG";

    private Server server;
    private static int nextGameId = 1;

    private ArchitectGrpc.ArchitectStub nonblockingArchitectStub;
    private ArchitectGrpc.ArchitectBlockingStub blockingArchitectStub;
    private ManagedChannel channelToArchitect;

    private final BrokerConfiguration config;
    private DSLContext jooq = null;
    private Connection conn = null;

    private final TextFormat.Printer pr = TextFormat.printer();

    public Broker(BrokerConfiguration config) {
        this.config = config;
        jooq = setupDatabase();

        try {
            new HttpServer().start(this);
        } catch (IOException e) {
            System.err.println("Could not open HTTP server, will run without it.");
            e.printStackTrace();
        }
    }

    DSLContext getJooq() {
        return jooq;
    }

    public BrokerConfiguration getConfig() {
        return config;
    }

    private DSLContext setupDatabase() {
        if (config.getDatabase() != null) {
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


        // open Broker service
        int port = config.getPort();
        server = ServerBuilder.forPort(port)
                .addService(new BrokerImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                Broker.this.stop();
                System.err.println("*** server shut down");
            }
        });

        System.err.println("Broker service running.");
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


    private class BrokerImpl extends BrokerGrpc.BrokerImplBase {
        /**
         * Handles the start of a game. Creates a record for this game in the database
         * and returns a unique game ID to the client.
         *
         * @param request
         * @param responseObserver
         */
        @Override
        public void startGame(GameData request, StreamObserver<GameId> responseObserver) {
            GamesRecord rec = jooq.newRecord(Tables.GAMES);
            rec.setClientIp(request.getClientAddress());
            rec.setPlayerName(request.getPlayerName());
            rec.setStartTime(now());
            rec.store();

            int id = rec.getId();
            setGameStatus(id, GamesStatus.created);

            // tell architect about the new game
            GameDataWithId mGameDataWithId = GameDataWithId.newBuilder().setId(id).build();
            ArchitectInformation info = blockingArchitectStub.startGame(mGameDataWithId);

            rec.setArchitectHostname(config.getArchitectServer().getHostname());
            rec.setArchitectPort(config.getArchitectServer().getPort());
            rec.setArchitectInfo(info.getInfo());
            rec.store();

            // tell client the game ID
            GameId idMessage = GameId.newBuilder().setId(id).build();
            responseObserver.onNext(idMessage);
            responseObserver.onCompleted();

            setGameStatus(id, GamesStatus.running);
        }

        @Override
        public void endGame(GameId request, StreamObserver<Void> responseObserver) {
            log(request.getId(), request, GameLogsDirection.PassToArchitect);
            Void v = blockingArchitectStub.endGame(request);

            responseObserver.onNext(v);
            responseObserver.onCompleted();

            setGameStatus(request.getId(), GamesStatus.finished);
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
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            nonblockingArchitectStub.handleStatusInformation(request, new DelegatingStreamObserver<>(request.getGameId(), responseObserver));
        }

    }

    private void setGameStatus(int gameid, GamesStatus status) {
        // update status in games table
        GamesRecord rec = jooq.fetchOne(Tables.GAMES, Tables.GAMES.ID.eq(gameid));
        rec.setStatus(status);
        rec.store();

        // record updating of status in game_logs table
        GameLogsRecord glr = jooq.newRecord(Tables.GAME_LOGS);
        glr.setGameid(gameid);
        glr.setDirection(GameLogsDirection.None);
        glr.setMessageType(MESSAGE_TYPE_LOG);
        glr.setMessage(String.format("Status of game %d changed to %s", gameid, status.toString()));
        glr.setTimestamp(now());
        glr.store();
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
            log(gameId, value, GameLogsDirection.PassToClient);
        }

        @Override
        public void onError(Throwable t) {
            log(gameId, t, GameLogsDirection.PassToClient);
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
        rec.setMessageType(message.getClass().getSimpleName());
        rec.setMessage(messageStr);
        rec.setTimestamp(now());
        rec.store();
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private void log(int gameid, Throwable message, GameLogsDirection direction) {
        String messageStr = message.toString();

        GameLogsRecord rec = jooq.newRecord(Tables.GAME_LOGS);
        rec.setGameid(gameid);
        rec.setDirection(direction);
        rec.setMessage(messageStr);
        rec.setMessageType(MESSAGE_TYPE_ERROR);
        rec.setTimestamp(now());
        rec.store();
    }

    private void log(String message) {
        System.err.println("Logged: " + message);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        BrokerConfiguration config = BrokerConfiguration.loadYaml(new FileReader("broker-config.yaml"));

        System.err.println("jdbc: " + config.getDatabase());
        System.err.println("architect: " + config.getArchitectServer().getPort());

        Broker server = new Broker(config);
        server.start();
        server.blockUntilShutdown();
    }
}
