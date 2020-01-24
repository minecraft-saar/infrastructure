package de.saar.minecraft.broker;

import com.google.common.base.Stopwatch;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import de.saar.minecraft.architect.ArchitectGrpc;
import de.saar.minecraft.architect.ArchitectGrpc.ArchitectBlockingStub;
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
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;


/**
 * A broker is a singleton object organizing experiments.
 * It holds a connection to all architects as well and decides
 * which experiment to run when a new user enters the Minecraft server.
 */
public class Broker {
    private static Logger logger = LogManager.getLogger(Broker.class);
    private static final String MESSAGE_TYPE_ERROR = "ERROR";
    private static final String MESSAGE_TYPE_LOG = "LOG";

    private Server server;

    private List<ArchitectConnection> architectConnections = new ArrayList<>();

    private HashMap<Integer, ArchitectConnection> runningGames = new HashMap<>();

    private static class ArchitectConnection {
        public ArchitectGrpc.ArchitectStub nonblockingArchitectStub;
        public ArchitectGrpc.ArchitectBlockingStub blockingArchitectStub;
        public ArchitectInformation architectInfo;
        public String host;
        public int port;
    }

    private final BrokerConfiguration config;
    private DSLContext jooq;

    private final TextFormat.Printer pr = TextFormat.printer();
    private List<String> scenarios;

    /**
     * Builds a new broker from a given configuration.
     * You usually only want one broker object in your system.
     */
    public Broker(BrokerConfiguration config) {
        logger.trace("Broker initialization");
        initScenarios(config.getScenarios());
        this.config = config;
        jooq = setupDatabase();

        // start web server
        if (config.getHttpPort() == 0) {
            logger.warn("No HTTP port specified, will run without HTTP server.");
        } else {
            try {
                new HttpServer().start(this);
            } catch (IOException e) {
                logger.warn("Could not open HTTP server (port in use?), will run without it.");
            }
        }
    }

    DSLContext getJooq() {
        return jooq;
    }

    public BrokerConfiguration getConfig() {
        return config;
    }

    /**
     * Starts the broker and tries to connect to the architect. Will exit
     * the program if no architect is available.
     * @throws IOException in case the broker grpc service cannot be started
     */
    public void start() throws IOException {
        // First, connect to all architects.
        for (var asa: config.getArchitectServers()) {
            var archConn = new ArchitectConnection();
            ManagedChannel channelToArchitect = ManagedChannelBuilder
                .forAddress(asa.getHostname(),
                    asa.getPort())
                // Channels are secure by default (via SSL/TLS).
                // we disable TLS to avoid needing certificates.
                .usePlaintext()
                .build();
            archConn.host = asa.getHostname();
            archConn.port = asa.getPort();
            archConn.nonblockingArchitectStub = ArchitectGrpc.newStub(channelToArchitect);
            archConn.blockingArchitectStub = ArchitectGrpc.newBlockingStub(channelToArchitect);
            // check connection to Architect server and get architectInfo string
            try {
                archConn.architectInfo = archConn.blockingArchitectStub.hello(
                    Void.newBuilder().build());
            } catch (StatusRuntimeException e) {
                logger.error("Failed to connect to architect server at "
                    + asa + "\n"
                    + e.getCause().getMessage());
                System.exit(1);
            }
            logger.info("Connected to architect server at " + asa);
            this.architectConnections.add(archConn);
        }

        // Second open Broker service.
        int port = config.getPort();
        server = ServerBuilder.forPort(port)
                .addService(new BrokerImpl())
                .build()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(Broker.this::stop));

        logger.info("Broker service running.");
    }

    /**
     * Performs a shutdown of the underlying grpc server.
     */
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
         */
        @Override
        public void startGame(GameData request,
                              StreamObserver<WorldSelectMessage> responseObserver) {
            var scenario = selectScenario();

            GamesRecord rec = jooq.newRecord(Tables.GAMES);
            rec.setClientIp(request.getClientAddress());
            rec.setPlayerName(request.getPlayerName());
            rec.setScenario(scenario);
            rec.setStartTime(now());
            rec.store();

            int id = rec.getId();
            setGameStatus(id, GameStatus.Created);

            var architect = selectArchitect();
            runningGames.put(id, architect);

            // Select new game
            WorldSelectMessage worldSelectMessage = WorldSelectMessage
                .newBuilder()
                .setGameId(id)
                .setName(scenario)
                .build();
            // tell architect about the new game
            Void x = architect.blockingArchitectStub.startGame(worldSelectMessage);

            rec.setArchitectHostname(architect.host);
            rec.setArchitectPort(architect.port);
            rec.setArchitectInfo(architect.architectInfo.getInfo());
            rec.store();

            // tell client the game ID and selected world
            responseObserver.onNext(worldSelectMessage);
            responseObserver.onCompleted();

            setGameStatus(id, GameStatus.Running);
        }

        @Override
        public void endGame(GameId request, StreamObserver<Void> responseObserver) {
            int id = request.getId();
            log(id, request, GameLogsDirection.PassToArchitect);
            Void v = getBlockingArchitect(id).endGame(request);

            responseObserver.onNext(v);
            responseObserver.onCompleted();

            setGameStatus(id, GameStatus.Finished);
        }

        /**
         * Handles a status update from the Minecraft server. Optionally, sends back a TextMessage
         * with a string that is to be displayed to the user. As calculating this text message may
         * take a long time, this method should be called asynchronously (with a non-blocking stub).
         */
        @Override
        public void handleStatusInformation(StatusMessage request,
                                            StreamObserver<TextMessage> responseObserver) {
            int id = request.getGameId();
            log(id, request, GameLogsDirection.FromClient);
            getNonblockingArchitect(id).handleStatusInformation(
                request,
                new DelegatingStreamObserver<>(id, responseObserver)
            );
        }

        @Override
        public void handleBlockPlaced(BlockPlacedMessage request,
                                      StreamObserver<TextMessage> responseObserver) {
            int id = request.getGameId();
            log(id, request, GameLogsDirection.FromClient);
            getNonblockingArchitect(id).handleBlockPlaced(
                request,
                new DelegatingStreamObserver<>(id, responseObserver)
            );
        }

        @Override
        public void handleBlockDestroyed(BlockDestroyedMessage request,
                                         StreamObserver<TextMessage> responseObserver) {
            int id = request.getGameId();
            log(id, request, GameLogsDirection.FromClient);
            getNonblockingArchitect(id).handleBlockDestroyed(
                request,
                new DelegatingStreamObserver<>(id, responseObserver)
            );
        }

        @Override
        public void handleMinecraftServerError(MinecraftServerError request,
                                               StreamObserver<Void> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            //TODO: react to error (restart?, shutdown with error message?)

            responseObserver.onNext(null);
            responseObserver.onCompleted();
        }

        @Override
        public void handleWorldFileError(WorldFileError request,
                                         StreamObserver<Void> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            //TODO: react to error

            responseObserver.onNext(null);
            responseObserver.onCompleted();
        }

        /**
         * returns the correct architect stub for the given game id.
         */
        private ArchitectBlockingStub getBlockingArchitect(int id) {
            return runningGames.get(id).blockingArchitectStub;
        }

        /**
         * returns the correct architect stub for the given game id.
         */
        private ArchitectGrpc.ArchitectStub getNonblockingArchitect(int id) {
            return runningGames.get(id).nonblockingArchitectStub;
        }

    }

    /**
     * Called whenever the status of a game changes, e.g. to started or completed.
     * Logs the change into the database.
     */
    private void setGameStatus(int gameid, GameStatus status) {
        // update status in games table
        jooq.update(Tables.GAMES)
            .set(Tables.GAMES.STATUS, status)
            .where(Tables.GAMES.ID.equal(gameid))
            .execute();

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
     * Initializes the scenarios by finding all resources that define scenarios and
     * intersecting them with the scenarios defined in the configuration.
     */
    private void initScenarios(List<String> confScenarios) {
        List<String> scenariosInResources = null;
        try (ScanResult scanResult = new ClassGraph()
            .whitelistPaths("de/saar/minecraft/worlds")
            .scan()) {
            scenariosInResources = scanResult.getAllResources()
                .filter(x -> x.getURL().getFile().endsWith(".csv"))
                .getPaths()
                .stream()
                .map(x -> x.substring(x.lastIndexOf("/") + 1, x.length() - 4))
                .collect(Collectors.toList());
        } catch (Exception exception) {
            logger.warn("Could not read scenarios from resources, not performing sanity checks.");
        }
        // sanity check configuration
        if (scenariosInResources != null
            && ! scenariosInResources.containsAll(confScenarios)) {
            String wrongScenarios = confScenarios.stream()
                .filter(scenariosInResources::contains)
                .collect(Collectors.joining(" "));
            logger.error("You defined a scenario in the configuration that is "
                    + "not present in the resources: "
                    + wrongScenarios
            );
            throw(new RuntimeException("Wrong scenario defined"));
        }
        if (confScenarios.isEmpty()) {
            logger.warn("No scenarios defined in the broker configuration.  Will use all of them");
            if (scenariosInResources == null) {
                logger.error("No scenarios defined and resources not readable, aborting");
                throw new RuntimeException("Could not determine scenarios");
            }
            scenarios = scenariosInResources;
        } else {
            scenarios = confScenarios;
        }
        logger.info("Using these scenarios: {}", String.join(" ", scenarios));
    }

    /**
     * Selects a scenario for the next game.
     */
    private String selectScenario() {
        var num = scenarios.size();
        var selected = new Random().nextInt(num);
        return scenarios.get(selected);
    }

    private ArchitectConnection selectArchitect() {
        var num = architectConnections.size();
        var selected = new Random().nextInt(num);
        return architectConnections.get(selected);
    }

    /**
     * A DelegatingStreamObserver acts as a proxy in connections from the Architect to the Client.
     * All messages are logged into the database and forwarded.
     */
    private class DelegatingStreamObserver<E extends MessageOrBuilder>
                                          implements StreamObserver<E> {
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

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * Logs game information to the database.
     */
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

    /**
     * Logs game information to the database.
     */
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

    /**
     * runs the broker, ignores all arguments.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        BrokerConfiguration config = BrokerConfiguration.loadYaml(
            new FileReader("broker-config.yaml")
        );

        Broker server = new Broker(config);
        server.start();
        server.blockUntilShutdown();
    }


    private DSLContext setupDatabase() {
        // special case:  If no database was configured at all, use an in-memory db (for testing)
        if (config.getDatabase() == null) {
            logger.warn("no database configured, will use in-memory database");
            try {
                Class.forName("org.h2.Driver");
            } catch (ClassNotFoundException e) {
                logger.error("No h2 class found, aborting.");
                e.printStackTrace();
                System.exit(1);
            }
            String url = "jdbc:h2:mem:MINECRAFT;DB_CLOSE_DELAY=-1";
            BrokerConfiguration.DatabaseAddress db = new BrokerConfiguration.DatabaseAddress();
            db.setUrl(url);
            db.setSqlDialect("H2");
            db.setUsername("");
            db.setPassword("");
            config.setDatabase(db);
        }

        try {
            var url = config.getDatabase().getUrl();
            var user = config.getDatabase().getUsername();
            var password = config.getDatabase().getPassword();
            // First, migrate to newest version
            Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .schemas("MINECRAFT")
                .defaultSchema("MINECRAFT")
                .load();
            flyway.migrate();
            flyway.migrate();
            // second, connect to database
            Connection conn = DriverManager.getConnection(url, user, password);
            DSLContext ret = DSL.using(
                conn,
                SQLDialect.valueOf(config.getDatabase().getSqlDialect())
            );
            logger.info("Connected to {} database at {}.",
                config.getDatabase().getSqlDialect(),
                config.getDatabase().getUrl());
            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        logger.error("Could not connect to database, exiting");
        System.exit(1);
        return null;
    }

}
