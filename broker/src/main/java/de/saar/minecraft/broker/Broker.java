package de.saar.minecraft.broker;

import com.google.common.base.Stopwatch;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import de.saar.minecraft.architect.ArchitectGrpc;
import de.saar.minecraft.architect.ArchitectInformation;
import de.saar.minecraft.architect.GameDataWithId;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import de.saar.minecraft.shared.GameId;
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
import java.sql.*;

public class Broker {
    private static final String MESSAGE_TYPE_ERROR = "ERROR";
    private static final String MESSAGE_TYPE_LOG = "LOG";

    private Server server;
    private static int nextGameId = 1;

    private ArchitectGrpc.ArchitectStub nonblockingArchitectStub;
    private ArchitectGrpc.ArchitectBlockingStub blockingArchitectStub;
    private ManagedChannel channelToArchitect;
    private ArchitectInformation architectInfo;

    private final BrokerConfiguration config;
    private DSLContext jooq = null;
    private Connection conn = null;

    private final TextFormat.Printer pr = TextFormat.printer();

    public Broker(BrokerConfiguration config) {
        this.config = config;
        jooq = setupDatabase();

        // start web server
        if (config.getHttpPort() == 0) {
            System.err.println("No HTTP port specified, will run without HTTP server.");
        } else {
            try {
                new HttpServer().start(this);
            } catch (IOException e) {
                System.err.println("Could not open HTTP server, will run without it.");
                e.printStackTrace();
            }
        }
    }

    DSLContext getJooq() {
        return jooq;
    }

    public BrokerConfiguration getConfig() {
        return config;
    }


    public void start() throws IOException {
        // connect to architect server
        channelToArchitect = ManagedChannelBuilder.forAddress(config.getArchitectServer().getHostname(), config.getArchitectServer().getPort())
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        nonblockingArchitectStub = ArchitectGrpc.newStub(channelToArchitect);
        blockingArchitectStub = ArchitectGrpc.newBlockingStub(channelToArchitect);
        System.err.printf("Connected to architect server at %s.\n", config.getArchitectServer());

        // check connection to Architect server and get architectInfo string
        try {
            architectInfo = blockingArchitectStub.hello(Void.newBuilder().build());
        } catch (StatusRuntimeException e) {
            System.err.printf("\nERROR: Failed to connect to architect server at %s:\n",
                    config.getArchitectServer());
            System.err.println(e.getCause().getMessage());
            System.exit(1);
        }

        // open Broker service
        int port = config.getPort();
        server = ServerBuilder.forPort(port)
                .addService(new BrokerImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
//                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                Broker.this.stop();
//                System.err.println("*** server shut down");
            }
        });

        System.err.println("Broker service running.");
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
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
            Stopwatch sw = Stopwatch.createStarted();

            GamesRecord rec = jooq.newRecord(Tables.GAMES);
            rec.setClientIp(request.getClientAddress());
            rec.setPlayerName(request.getPlayerName());
            rec.setStartTime(now());
            rec.store();

            int id = rec.getId();
            setGameStatus(id, GameStatus.Created);

//            System.err.printf("db insert: %s\n", sw);

            // tell architect about the new game
            GameDataWithId mGameDataWithId = GameDataWithId.newBuilder().setId(id).build();
            Void x = blockingArchitectStub.startGame(mGameDataWithId);

//            System.err.printf("architect instantiated: %s\n", sw);

            rec.setArchitectHostname(config.getArchitectServer().getHostname());
            rec.setArchitectPort(config.getArchitectServer().getPort());
            rec.setArchitectInfo(architectInfo.getInfo());
            rec.store();

//            System.err.printf("db updated: %s\n", sw);

            // tell client the game ID
            GameId idMessage = GameId.newBuilder().setId(id).build();
            responseObserver.onNext(idMessage);
            responseObserver.onCompleted();

//            System.err.printf("client called back: %s\n", sw);

            setGameStatus(id, GameStatus.Running);

//            System.err.printf("done: %s\n", sw);
        }

        @Override
        public void endGame(GameId request, StreamObserver<Void> responseObserver) {
            log(request.getId(), request, GameLogsDirection.PassToArchitect);
            Void v = blockingArchitectStub.endGame(request);

            responseObserver.onNext(v);
            responseObserver.onCompleted();

            setGameStatus(request.getId(), GameStatus.Finished);
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

    private void setGameStatus(int gameid, GameStatus status) {
        // update status in games table
        jooq.update(Tables.GAMES).set(Tables.GAMES.STATUS, status).where(Tables.GAMES.ID.equal(gameid)).execute();

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

        System.err.println("Could not connect to database; setting up temporary in-memory database.");

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            String url = "jdbc:h2:mem:minecraft;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";

            // create db configuration (for display on website)
            BrokerConfiguration.DatabaseAddress db = new BrokerConfiguration.DatabaseAddress();
            db.setUrl(url);
            db.setSqlDialect("H2");
            db.setUsername("");
            db.setPassword("");
            config.setDatabase(db);

            // create schema "minecraft" and activate it
            conn = DriverManager.getConnection(url, "", "");
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("create schema if not exists minecraft;");
            conn.setSchema("minecraft");

            // create tables
            stmt.executeUpdate(CREATE_TABLES);
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }

        DSLContext ret = DSL.using(conn, SQLDialect.H2);
        return ret;
    }

    private static final String CREATE_TABLES = "CREATE TABLE if not exists `game_logs` (\n" +
            "  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
            "  `gameid` int(11) DEFAULT NULL,\n" +
            "  `direction` enum('FromClient','ToClient','FromArchitect','ToArchitect','PassToClient','PassToArchitect','None') DEFAULT NULL,\n" +
            "  `message_type` varchar(100) DEFAULT NULL,\n" +
            "  `message` varchar(500) DEFAULT NULL,\n" +
            "  `timestamp` timestamp NULL DEFAULT NULL,\n" +
            "  PRIMARY KEY (`id`)\n" +
            ")  ;\n" +
            "\n" +
            "CREATE TABLE if not exists `games` (\n" +
            "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
            "  `client_ip` varchar(200) DEFAULT NULL,\n" +
            "  `player_name` varchar(200) DEFAULT NULL,\n" +
            "  `start_time` timestamp NULL DEFAULT NULL,\n" +
            "  `status` enum('created','running','finished') DEFAULT NULL,\n" +
            "  `architect_hostname` varchar(100) DEFAULT NULL,\n" +
            "  `architect_port` int(11) DEFAULT NULL,\n" +
            "  `architect_info` varchar(500) DEFAULT NULL,\n" +
            "  PRIMARY KEY (`id`)\n" +
            ")  ;";
}
