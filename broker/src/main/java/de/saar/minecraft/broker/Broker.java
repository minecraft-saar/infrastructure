package de.saar.minecraft.broker;

import com.google.common.base.Stopwatch;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import de.saar.minecraft.architect.ArchitectGrpc;
import de.saar.minecraft.architect.ArchitectInformation;
import de.saar.minecraft.broker.db.GameLogsDirection;
import de.saar.minecraft.broker.db.GameStatus;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.GameId;
import de.saar.minecraft.shared.MinecraftServerError;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.Void;
import de.saar.minecraft.shared.WorldFileError;
import de.saar.minecraft.shared.WorldSelectMessage;
import de.saar.minecraft.util.Util;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Broker {
    private static Logger logger = LogManager.getLogger(Broker.class);
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
    private List<String> objectives;

    public Broker(BrokerConfiguration config) {
        logger.trace("Broker initialization");
        initObjectives();
        this.config = config;
        jooq = setupDatabase();

        // start web server
        if (config.getHttpPort() == 0) {
            logger.warn("No HTTP port specified, will run without HTTP server.");
        } else {
            try {
                new HttpServer().start(this);
            } catch (IOException e) {
                logger.warn("Could not open HTTP server, will run without it.");
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

        // check connection to Architect server and get architectInfo string
        try {
            architectInfo = blockingArchitectStub.hello(Void.newBuilder().build());
        } catch (StatusRuntimeException e) {
            logger.error("Failed to connect to architect server at " +
                         config.getArchitectServer() + "\n" +
                         e.getCause().getMessage());
            System.exit(1);
        }

        logger.info("Connected to architect server at " + config.getArchitectServer());

        // open Broker service
        int port = config.getPort();
        server = ServerBuilder.forPort(port)
                .addService(new BrokerImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Broker.this.stop();
            }
        });

        logger.info("Broker service running.");
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
        public void startGame(GameData request, StreamObserver<WorldSelectMessage> responseObserver) {
            Stopwatch sw = Stopwatch.createStarted();

            GamesRecord rec = jooq.newRecord(Tables.GAMES);
            rec.setClientIp(request.getClientAddress());
            rec.setPlayerName(request.getPlayerName());
            rec.setStartTime(now());
            rec.store();

            int id = rec.getId();
            setGameStatus(id, GameStatus.Created);

            // Select new game
            WorldSelectMessage mWorld = WorldSelectMessage.newBuilder().setGameId(id).setName("bridge").build();
            // TODO: actually select instead of hardcoded 'bridge'
            // tell architect about the new game
            Void x = blockingArchitectStub.startGame(mWorld);

            rec.setArchitectHostname(config.getArchitectServer().getHostname());
            rec.setArchitectPort(config.getArchitectServer().getPort());
            rec.setArchitectInfo(architectInfo.getInfo());
            rec.store();

            // tell client the game ID and selected world
            responseObserver.onNext(mWorld);
            responseObserver.onCompleted();

            setGameStatus(id, GameStatus.Running);
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

        @Override
        public void handleBlockPlaced(BlockPlacedMessage request, StreamObserver<TextMessage> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            nonblockingArchitectStub.handleBlockPlaced(request, new DelegatingStreamObserver<>(request.getGameId(), responseObserver));
        }

        @Override
        public void handleBlockDestroyed(BlockDestroyedMessage request, StreamObserver<TextMessage> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            nonblockingArchitectStub.handleBlockDestroyed(request, new DelegatingStreamObserver<>(request.getGameId(), responseObserver));
        }

        @Override
        public void handleMinecraftServerError(MinecraftServerError request, StreamObserver<Void> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            //TODO: react to error (restart?, shutdown with error message?)

            Void v = null;
            responseObserver.onNext(v);
            responseObserver.onCompleted();
        }

        @Override
        public void handleWorldFileError(WorldFileError request, StreamObserver<Void> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            //TODO: react to error

            Void v = null;
            responseObserver.onNext(v);
            responseObserver.onCompleted();
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

    /**
     * Finds all resources that define objectives.
     */
    private void initObjectives() {
        try (ScanResult scanResult = new ClassGraph()
            .whitelistPaths("de/saar/minecraft/worlds")
            .scan()) {
            objectives = scanResult.getAllResources()
                .filter(x -> x.getURL().getFile().endsWith(".csv"))
                .getPaths()
                .stream().map(x -> x.substring(x.lastIndexOf("/") + 1, x.length() - 4))
                .collect(Collectors.toList());
        }
        logger.info("Found these objectives: {}", String.join(" ", objectives));
    }

    /**
     * Selects an objective for the next game.
     */
    private String selectObjective() {
        var num = objectives.size();
        var selected = new Random().nextInt(num);
        return objectives.get(selected);
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

    public static void main(String[] args) throws IOException, InterruptedException {
        BrokerConfiguration config = BrokerConfiguration.loadYaml(new FileReader("broker-config.yaml"));

        Broker server = new Broker(config);
        server.start();
        server.blockUntilShutdown();
    }


    private DSLContext setupDatabase() {
        if (config.getDatabase() != null) {
            try {
                conn = DriverManager.getConnection(config.getDatabase().getUrl(), config.getDatabase().getUsername(), config.getDatabase().getPassword());
                DSLContext ret = DSL.using(conn, SQLDialect.valueOf(config.getDatabase().getSqlDialect()));
                logger.info("Connected to {} database at {}.",
                            config.getDatabase().getSqlDialect(),
                            config.getDatabase().getUrl());
                return ret;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        logger.warn("Could not connect to database; setting up temporary in-memory database.");

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            String url = "jdbc:h2:mem:minecraft;DB_CLOSE_DELAY=-1";

            // Capitalization in H2 is finicky. By default, H2 converts all unquoted names to uppercase,
            // and is case-sensitive. Here we allow this default, to fit with the all-uppercase names
            // that the jOOQ Gradle plugin generates; there it secretly uses a H2 database which can't
            // be configured so easily. MySQL doesn't care about case, and can live with the uppercased
            // names.
            //
            // If we ever choose to go back to the original, un-uppercased names, we can achieve
            // this here by adding the following string to the JDBC URL: ";DATABASE_TO_UPPER=FALSE"

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
            stmt.executeUpdate("create schema if not exists MINECRAFT;"); // note the uppercased schema name
            conn.setSchema("MINECRAFT");

            // create tables
            String createTablesStr = Util.slurp(new InputStreamReader(getClass().getResourceAsStream("/database.sql")));
            stmt.executeUpdate(createTablesStr);
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }

        DSLContext ret = DSL.using(conn, SQLDialect.H2);
        return ret;
    }
}
