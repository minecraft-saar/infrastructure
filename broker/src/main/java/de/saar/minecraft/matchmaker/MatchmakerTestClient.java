package de.saar.minecraft.matchmaker;

import com.google.protobuf.TextFormat;
import de.saar.minecraft.shared.GameId;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * A test client for the matchmaker. This is a mockup class that generates messages that would
 * usually be sent by the Minecraft server. You can enter the following kinds of strings on the
 * console. Type "status id" to send a status message for game id "id", and receive a text message
 * back asynchronously. Type any other string to create a new game with the given game data. Type
 * Ctrl-D to quit the client.
 */
public class MatchmakerTestClient {

  private ManagedChannel channel;
  private MatchmakerGrpc.MatchmakerBlockingStub blockingStub;
  private MatchmakerGrpc.MatchmakerStub nonblockingStub;

  /**
   * Construct client connecting to HelloWorld server at {@code host:port}.
   */
  public MatchmakerTestClient(String host, int port) {
    this(ManagedChannelBuilder.forAddress(host, port)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build());
  }

  /**
   * Construct client for accessing HelloWorld server using the existing channel.
   */
  MatchmakerTestClient(ManagedChannel channel) {
    this.channel = channel;
    blockingStub = MatchmakerGrpc.newBlockingStub(channel);
    nonblockingStub = MatchmakerGrpc.newStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /**
   * Registers a game with the matchmaker. Returns a unique game ID for this game.
   */
  public int registerGame(String playerName) {
    // TODO fill in PlayerLoginEvent#getAddress, Player#getDisplayName

    String hostname = "localhost";
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
    }

    GameData mGameInfo = GameData.newBuilder().setClientAddress(hostname).setPlayerName(playerName)
        .build();

    GameId mGameId;
    try {
      mGameId = blockingStub.startGame(mGameInfo);
    } catch (StatusRuntimeException e) {
      System.err.println("RPC failed: " + e.getStatus());
      return -1;
    }

    return mGameId.getId();
  }


  public void finishGame(int gameId) {
    GameId mGameId = GameId.newBuilder().setId(gameId).build();
    blockingStub.endGame(mGameId);
  }

  /**
   * Sends a status message for the given game ID to the matchmaker. Handles any text messages that
   * the matchmaker sends back.
   */
  public void sendStatusMessage(int gameId, int x, int y, int z) {
    StatusMessage mStatus = StatusMessage.newBuilder().setGameId(gameId).setX(x).setY(y).setZ(z)
        .build();

    nonblockingStub.handleStatusInformation(mStatus, new StreamObserver<TextMessage>() {
      @Override
      public void onNext(TextMessage value) {
        System.err.printf("got text message for gameid %d: %s\n", gameId, value);
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("ERROR: " + t.toString());
      }

      @Override
      public void onCompleted() {
      }
    });

  }

  public static void main(String[] args) throws InterruptedException {
    MatchmakerTestClient client = new MatchmakerTestClient("localhost", 2802);
    int gameId = 0;

    try {
      while (true) {
        System.out.print("enter player name: ");
        String gameData = System.console().readLine();

        if (gameData == null) {
          client.finishGame(gameId);
          break;
        } else if (gameData.startsWith(STATUS)) {
          int id = Integer.parseInt(gameData.substring(STATUS.length() + 1));
          client.sendStatusMessage(id, 1, 2, 3);
        } else {
          gameId = client.registerGame(gameData);
          System.err.printf("got game ID %d\n", gameId);
        }
      }
    } finally {
      client.shutdown();

    }
  }

  private static final String STATUS = "status";
}
