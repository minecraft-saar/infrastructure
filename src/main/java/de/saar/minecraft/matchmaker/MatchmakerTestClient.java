package de.saar.minecraft.matchmaker;

import com.google.protobuf.TextFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public class MatchmakerTestClient {
    private ManagedChannel channel;
    private MatchmakerGrpc.MatchmakerBlockingStub blockingStub;

    /** Construct client connecting to HelloWorld server at {@code host:port}. */
    public MatchmakerTestClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build());
    }

    /** Construct client for accessing HelloWorld server using the existing channel. */
    MatchmakerTestClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = MatchmakerGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public int registerGame(String gameIdentifier) {
        GameData mGameInfo = GameData.newBuilder().setGameIdentifier(gameIdentifier).build();
        GameId mGameId;
        try {
            mGameId = blockingStub.startGame(mGameInfo);
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
            return -1;
        }

        return mGameId.getId();
    }

    public static void main(String[] args) throws InterruptedException {
        MatchmakerTestClient client = new MatchmakerTestClient("localhost", 2802);

        try {
            System.out.print("enter game data: ");
            String gameId = System.console().readLine();

            int playerId = client.registerGame(gameId);
            System.err.printf("got game ID %d\n", playerId);
        } finally {
            client.shutdown();
        }
    }
}
