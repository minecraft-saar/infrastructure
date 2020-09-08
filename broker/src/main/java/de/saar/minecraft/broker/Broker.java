package de.saar.minecraft.broker;

import com.google.protobuf.InvalidProtocolBufferException;
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
import de.saar.minecraft.shared.None;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.WorldFileError;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
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
    private ConcurrentHashMap<Integer, Questionnaire> questionnaires = new ConcurrentHashMap<>();


    private static class ArchitectConnection {
        public ArchitectGrpc.ArchitectStub nonblockingArchitectStub;
        public ArchitectGrpc.ArchitectBlockingStub blockingArchitectStub;
        public ArchitectInformation architectInfo;
        public String host;
        public int port;
    }

    final BrokerConfiguration config;
    private DSLContext jooq;

    private final TextFormat.Printer pr = TextFormat.printer();
    private List<String> scenarios;
    private HashMap<String, List<Question>> questionTemplates;

    /**
     * Builds a new broker from a given configuration.
     * You usually only want one broker object in your system.
     */
    public Broker(BrokerConfiguration config) {
        logger.trace("Broker initialization");
        initScenarios(config.getScenarios());
        initQuestionnaires(config.getScenarios());
        this.config = config;
        jooq = setupDatabase();
        // Do a random query every 20 minutes to keep the
        // database connection alive.
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(20*1000*60);
                } catch (InterruptedException e) {
                    //whatever, we don't care
                }
                jooq.select().from(Tables.GAMES).fetch();
            }
        }).start();

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
                    None.newBuilder().build());
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
     * Performs a shutdown of the underlying grpc server after terminating all games
     * currently running.
     */
    public void stop() {
        for (ArchitectConnection a: architectConnections) {
            a.blockingArchitectStub.endAllGames(None.getDefaultInstance());
        }
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
            rec.setModified(now());
            rec.store();

            int id = rec.getId();
            setGameStatus(id, GameStatus.Created);

            var architect = selectArchitect(scenario);
            runningGames.put(id, architect);

            // Select new game
            WorldSelectMessage worldSelectMessage = WorldSelectMessage
                .newBuilder()
                .setGameId(id)
                .setName(scenario)
                .build();
            // tell architect about the new game
            architect.blockingArchitectStub.startGame(worldSelectMessage);

            rec.setArchitectHostname(architect.host);
            rec.setArchitectPort(architect.port);
            rec.setArchitectInfo(architect.architectInfo.getInfo());
            rec.store();

            // tell client the game ID and selected world
            responseObserver.onNext(worldSelectMessage);
            responseObserver.onCompleted();
        }

        @Override
        public void getMessageChannel(GameId request,
            StreamObserver<TextMessage> responseObserver) {
            int id = request.getId();
            var so = new DelegatingStreamObserver(id, responseObserver, Broker.this);
            var architect = getNonblockingArchitect(id);
            if (architect != null) {
                architect.getMessageChannel(request, so);
            } else {
                responseObserver.onError(new RuntimeException("Architect is null"));
            }
        }

        @Override
        public void playerReady(GameId request, StreamObserver<None> responseObserver) {
            int id = request.getId();
            setGameStatus(id, GameStatus.Running);
            var architect = getBlockingArchitect(request.getId());
            architect.playerReady(request);
            responseObserver.onNext(None.getDefaultInstance());
            responseObserver.onCompleted();
        }

        private StatusException createNoSuchIdException(int id) {
            return new StatusException(
                Status
                    .INVALID_ARGUMENT
                    .withDescription("No game with ID " + id)
            );
        }

        @Override
        public void endGame(GameId request, StreamObserver<None> responseObserver) {
            int id = request.getId();
            if (! runningGames.containsKey(id)) {
                responseObserver.onError(createNoSuchIdException(id));
                return;
            }
            log(id, request, GameLogsDirection.PassToArchitect);
            None v = getBlockingArchitect(id).endGame(request);

            responseObserver.onNext(v);
            responseObserver.onCompleted();

            setGameStatus(id, GameStatus.Finished);
            runningGames.remove(id);
        }

        /**
         * Handles a status update from the Minecraft server. Optionally, sends back a TextMessage
         * with a string that is to be displayed to the user. As calculating this text message may
         * take a long time, this method should be called asynchronously (with a non-blocking stub).
         */
        @Override
        public void handleStatusInformation(StatusMessage request,
                                            StreamObserver<None> responseObserver) {
            int id = request.getGameId();
            if (! runningGames.containsKey(id)) {
                responseObserver.onError(createNoSuchIdException(id));
                return;
            }
            log(id, request, GameLogsDirection.FromClient);
            if (!questionnaires.containsKey(id)) {
                getNonblockingArchitect(id).handleStatusInformation(
                    request, responseObserver
                );
            } else {
                responseObserver.onNext(None.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }

        @Override
        public void handleBlockPlaced(BlockPlacedMessage request,
                                      StreamObserver<None> responseObserver) {
            int id = request.getGameId();
            if (! runningGames.containsKey(id)) {
                responseObserver.onError(createNoSuchIdException(id));
                return;
            }
            log(id, request, GameLogsDirection.FromClient);
            if (!questionnaires.containsKey(id)) {
                getNonblockingArchitect(id).handleBlockPlaced(request, responseObserver);
            } else {
                responseObserver.onNext(None.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }

        @Override
        public void handleBlockDestroyed(BlockDestroyedMessage request,
                                         StreamObserver<None> responseObserver) {
            int id = request.getGameId();
            if (! runningGames.containsKey(id)) {
                responseObserver.onError(createNoSuchIdException(id));
                return;
            }
            log(id, request, GameLogsDirection.FromClient);
            if (!questionnaires.containsKey(id)) {
                getNonblockingArchitect(id).handleBlockDestroyed(
                    request, responseObserver);
            } else {
                responseObserver.onNext(None.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }

        @Override
        public void handleMinecraftServerError(MinecraftServerError request,
                                               StreamObserver<None> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            //TODO: react to error (restart?, shutdown with error message?)

            responseObserver.onNext(None.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void handleWorldFileError(WorldFileError request,
                                         StreamObserver<None> responseObserver) {
            log(request.getGameId(), request, GameLogsDirection.FromClient);
            //TODO: react to error

            responseObserver.onNext(None.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void handleTextMessage(TextMessage request,
                                      StreamObserver<None> responseObserver) {
            int id = request.getGameId();
            log(id, request, GameLogsDirection.FromClient);
            if (!questionnaires.containsKey(id)) {
                // ignore text messages if no questionnaire is running
                responseObserver.onNext(None.getDefaultInstance());
                responseObserver.onCompleted();
                return;
            }
            try {
                questionnaires.get(id).onNext(request);
                responseObserver.onNext(None.getDefaultInstance());
                responseObserver.onCompleted();
            } catch (Exception e) {
                responseObserver.onError(e);
            }
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
            .set(Tables.GAMES.MODIFIED, now())
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
     * Loads questionnaires for all scenarios defined in the configuration that have one.
     */
    private void initQuestionnaires(List<String> confScenarios) {
        questionTemplates = new HashMap<>();
        List<String> questionnairesInResources = null;
        // Check availability of questionnaires
        try (ScanResult scanResult = new ClassGraph()
            .whitelistPaths("de/saar/minecraft/questionnaires")
            .scan()) {
            questionnairesInResources = scanResult.getAllResources()
                .filter(x -> x.getURL().getFile().endsWith(".txt"))
                .getPaths()
                .stream()
                .map(x -> x.substring(x.lastIndexOf("/") + 1, x.length() - 4))
                .collect(Collectors.toList());
        } catch (Exception exception) {
            logger.warn("Could not read questionnaires from resources, not performing sanity checks.");
        }
        // Checks
        if (questionnairesInResources == null) {
            throw(new RuntimeException("No questionnaires available"));
        }
        if (! questionnairesInResources.containsAll(confScenarios)) {
            confScenarios.removeAll(questionnairesInResources);
            logger.error("You defined scenarios in the configuration without a questionnaire "
                + confScenarios
            );
            throw(new RuntimeException("Missing Questionnaires: " + String.join(", ", confScenarios)));
        }
        if (confScenarios.isEmpty()) {
            logger.warn("No scenarios defined in the broker configuration. "
                + "Will load all questionnaires");
        } else {
            questionnairesInResources.stream().filter(confScenarios::contains);
            logger.info("Start loading defined questionnaires.");
        }
        // Load questionnaires
        for (String filename: questionnairesInResources) {
            try {
                String path = String.format("/de/saar/minecraft/questionnaires/%s.txt", filename);
                InputStream in = Broker.class.getResourceAsStream(path);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                List<Question> current = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip empty lines
                    if (line.strip().length() > 0) {
                        current.add(new Question(line));
                    }
                    logger.debug("Line: {}", line);
                }
                reader.close();
                questionTemplates.put(filename, current);
            } catch (IOException e) {
                logger.error("Could not load questionnaire {}", filename);
                throw(new RuntimeException("Could not load questionnaire: " + filename));
            }
        }

        logger.info("Using questionnaires for these scenarios: {}",
            String.join(" ", questionnairesInResources));

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
        // scenarios in ascending order
        // by number of games started with them
        List<String> scenariosByNumExperiments =
            jooq.select(Tables.GAMES.SCENARIO)
                .from(Tables.GAMES)
                .groupBy(Tables.GAMES.SCENARIO)
                .orderBy(DSL.count())
                .fetch(Tables.GAMES.SCENARIO)
            .stream()
            .filter(this.scenarios::contains)
            .collect(Collectors.toList());

        var neverPlayed = this.scenarios
            .stream()
            .filter((x) -> !scenariosByNumExperiments.contains(x))
            .collect(Collectors.toList());

        String scenarioToUse;
        if (!neverPlayed.isEmpty()) {
            scenarioToUse = neverPlayed.get(0);
        } else {
            scenarioToUse = scenariosByNumExperiments.get(0);
        }
        return scenarioToUse;
    }

    private ArchitectConnection selectArchitect(String scenario) {
        Set<String> currentArchitects = architectConnections
            .stream()
            .map((x) -> x.architectInfo.getInfo())
            .collect(Collectors.toSet());

        // architect info in ascending order
        // by number of games started with them
        List<String> architectsByNumExperiments =
            jooq.select(Tables.GAMES.ARCHITECT_INFO)
                .from(Tables.GAMES)
                .where(Tables.GAMES.SCENARIO.eq(scenario))
                .groupBy(Tables.GAMES.ARCHITECT_INFO)
                .orderBy(DSL.count())
                .fetch(Tables.GAMES.ARCHITECT_INFO)
                .stream()
                .filter(currentArchitects::contains)
                .collect(Collectors.toList());

        logger.debug("architectsByNumExperiments: " + architectsByNumExperiments);

        var neverPlayed = currentArchitects.stream()
            .filter((x) -> !architectsByNumExperiments.contains(x))
            .collect(Collectors.toList());
        logger.debug("never played: " + neverPlayed);

        String architectToUse;
        if (!neverPlayed.isEmpty()) {
            architectToUse = neverPlayed.get(0);
        } else {
            architectToUse = architectsByNumExperiments.get(0);
        }

        logger.debug("architectToUse: " + architectToUse);

        return architectConnections.stream()
            .filter((x) ->
                x.architectInfo
                    .getInfo()
                    .equals(architectToUse))
                .findFirst()
                .get();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Logs game information to the database.
     */
    void log(int gameid, MessageOrBuilder message, GameLogsDirection direction) {
        String messageStr = "";
        try {
            messageStr = com.google.protobuf.util.JsonFormat.printer()
                .includingDefaultValueFields()
                .print(message);
        } catch (InvalidProtocolBufferException e) {
            logger.error("could convert message to json: " + message);
        }
        log(gameid, messageStr, message.getClass().getSimpleName(), direction);
    }

    void log(int gameid,
        String messageStr,
        String messageType,
        GameLogsDirection direction) {

        GameLogsRecord rec = jooq.newRecord(Tables.GAME_LOGS);
        rec.setGameid(gameid);
        rec.setDirection(direction);
        rec.setMessageType(messageType);
        rec.setMessage(messageStr);
        rec.setTimestamp(now());
        rec.store();
    }

    /**
     * Logs game information to the database.
     */
    void log(int gameid, Throwable message, GameLogsDirection direction) {
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
            logger.warn("no database configured, will use temporary in-memory database");
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
            Flyway.configure()
                .dataSource(url, user, password)
                .load()
                .migrate();

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

    /**
     * initializes a new Questionnaire object that takes
     * over asking and storing answers to questions.
     * @param gameId The id of the game for which the questionnaire is needed.
     * @param streamObserver The stream used to send questions to the player.
     */
    public void startQuestionnaire(int gameId,
        DelegatingStreamObserver streamObserver) {
        if (config.getUseInternalQuestionnaire()) {
            logger.info("Starting questionnaire for game {}", gameId);
            Questionnaire questionnaire = createQuestionnaire(gameId, streamObserver);
            questionnaire.start();
            questionnaires.put(gameId, questionnaire);
        } else {
            // We put in a mock questionnaire as null values
            // are not allowed. Note that this one is not start()ed.
            Questionnaire questionnaire = new Questionnaire(
                gameId,
                new ArrayList<>(),
                streamObserver, jooq
            );
            questionnaires.put(gameId, questionnaire);
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                streamObserver.onNext(TextMessage.newBuilder()
                    .setGameId(gameId)
                    .setText(
                        "Thank you for your time! you can hang around or disconnect now.")
                    // .setNewGameState(NewGameState.QuestionnaireFinished)
                    .build());
            }).start();
        }
    }

    /**
     * returns a questionnaire for a given game.
     * @param gameId Used to find the correct scenario
     * @param streamObserver The stream used to send questions to the player.
     * @return a Questionnaire depending on the scenario
     */
    private Questionnaire createQuestionnaire(int gameId, DelegatingStreamObserver streamObserver) {
        // Find current scenario
        Result<Record> records = jooq.select()
            .from(Tables.GAMES)
            .where(Tables.GAMES.ID.eq(gameId))
            .fetch();
        List<String> scenarios = records.getValues(Tables.GAMES.SCENARIO);

        // Get matching questionnaire
        List<Question> questions = questionTemplates.get(scenarios.get(0));
        return new Questionnaire(gameId, questions, streamObserver, jooq);
    }
}
