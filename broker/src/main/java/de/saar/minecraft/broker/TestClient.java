package de.saar.minecraft.broker;

import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.GameId;
import de.saar.minecraft.shared.None;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A test client for the matchmaker. This is a mockup class that generates messages that would
 * usually be sent by the Minecraft server. You can enter the following kinds of strings on the
 * console. Type "status id" to send a status message for game id "id", and receive a text message
 * back asynchronously. Type any other string to create a new game with the given game data. Type
 * Ctrl-D to quit the client.
 */
public class TestClient {

    private static Logger logger = LogManager.getLogger(TestClient.class);
    private ManagedChannel channel;
    private BrokerGrpc.BrokerBlockingStub blockingStub;
    private BrokerGrpc.BrokerStub nonblockingStub;
    private List<Integer> runningGames;

    /**
     * Construct client connecting to HelloWorld server at {@code host:port}.
     */
    public TestClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext()
            .build());
    }

    /**
     * Construct client for accessing HelloWorld server using the existing channel.
     */
    TestClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = BrokerGrpc.newBlockingStub(channel);
        nonblockingStub = BrokerGrpc.newStub(channel);
        this.runningGames = new ArrayList<>();
    }

    /**
     * Terminates all running games (ignoring errors) and shuts down the grpc channel to the broker.
     */
    public void shutdown() {
        // end all running games to close the open streams
        for (Integer i: runningGames) {
            try {
                blockingStub.endGame(GameId.newBuilder().setId(i).build());
            } catch (Exception e) {
                // maybe the game was already stopped by the broker
                // We don't know because the test client does not check this.
            }
        }
        // shut down connection to broker
        channel.shutdown();
    }

    /**
     * Registers a game with the matchmaker. Returns a unique game ID for this game.
     * Registers streamObserver for messages if provided.  If that argument is null,
     * it will instantiate a default TextStreamObserver.
     */
    public int registerGame(String playerName, StreamObserver<TextMessage> streamObserver) {
        // TODO fill in PlayerLoginEvent#getAddress, Player#getDisplayName

        String hostname = "localhost";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.error("could not determine address of localhost");
            System.exit(1);
        }

        GameData gameData = GameData.newBuilder()
            .setClientAddress(hostname)
            .setPlayerName(playerName)
            .build();

        WorldSelectMessage worldSelectMessage;
        try {
            worldSelectMessage = blockingStub.startGame(gameData);
        } catch (StatusRuntimeException e) {
            logger.error("RPC failed: " + e.getStatus());
            return -1;
        }
        int gameId = worldSelectMessage.getGameId();
        runningGames.add(gameId);
        var scenario = worldSelectMessage.getName();
        System.out.println("Game started for client " + gameId + " with scenario " + scenario);
        if (streamObserver == null) {
            streamObserver = new TextStreamObserver(gameId);
        }
        var gameIdMessage = GameId.newBuilder().setId(gameId).build();
        nonblockingStub.getMessageChannel(gameIdMessage, streamObserver);
        return gameId;
    }

    public void finishGame(int gameId) {
        GameId gameIdMessage = GameId.newBuilder().setId(gameId).build();
        blockingStub.endGame(gameIdMessage);
        runningGames.remove(runningGames.indexOf(gameId));
    }

    /**
     * Sends a status message for the given game ID to the matchmaker.
     * Handles any text messages that the matchmaker sends back.
     */
    public void sendStatusMessage(int gameId, int x, int y, int z,
                                  double xdir, double ydir, double zdir,
                                  StreamObserver<None> obs) {
        StatusMessage message = StatusMessage
            .newBuilder()
            .setGameId(gameId)
            .setX(x)
            .setY(y)
            .setZ(z)
            .setXDirection(xdir)
            .setYDirection(ydir)
            .setZDirection(zdir)
            .build();
        nonblockingStub.handleStatusInformation(message, obs);
    }

    public void sendStatusMessage(int gameId, int x, int y, int z,
                                   double xdir, double ydir, double zdir) {
        sendStatusMessage(gameId, x, y, z, xdir, ydir, zdir, new NoneObserver());
    }

    public void sendBlockPlacedMessage(int gameId, int x, int y, int z) {
        nonblockingStub.handleBlockPlaced(BlockPlacedMessage.newBuilder()
            .setGameId(gameId)
            .setX(x)
            .setY(y)
            .setZ(z)
            .setType(0)
            .build(), new NoneObserver());
    }

    public static class NoneObserver implements StreamObserver<None> {

        @Override
        public void onNext(None value) {
        }

        @Override
        public void onError(Throwable t) {
            logger.error(t);
        }

        @Override
        public void onCompleted() {
        }
    }

    private static class TextStreamObserver implements StreamObserver<TextMessage> {
        private int gameId;

        public TextStreamObserver(int gameId) {
            this.gameId = gameId;
        }

        @Override
        public void onNext(TextMessage value) {
            System.out.printf("got text message for gameid %d: %s\n", gameId, value);
        }

        @Override
        public void onError(Throwable t) {
            logger.error(t.toString());
        }

        @Override
        public void onCompleted() {
        }
    }

    /**
     * Runs the TestClient with a promp to start new clients.
     */
    public static void main(String[] args) throws InterruptedException {
        TestClient client = new TestClient("localhost", 2802);
        int gameId = 0;
        System.out.println("Interactive test console for starting test clients");
        System.out.println("Enter a player name to start a new client");
        System.out.println("Enter 'status XY' to send a status to the broker for client no XY.");
        System.out.println("Ctrl-D exits the program.");
        try {
            while (true) {
                System.out.print("enter player name: ");
                String gameData = System.console().readLine();

                if (gameData == null) {
                    client.finishGame(gameId);
                    break;
                } else if (gameData.startsWith(STATUS)) {
                    int id = Integer.parseInt(gameData.substring(STATUS.length() + 1));
                    client.sendStatusMessage(id, 1, 2, 3, 0.4, 0.0, -0.7);
                } else {
                    gameId = client.registerGame(gameData, null);
                    System.out.printf("got game ID %d\n", gameId);
                }
            }
        } finally {
            client.shutdown();
        }
    }

    private static final String STATUS = "status";
}
