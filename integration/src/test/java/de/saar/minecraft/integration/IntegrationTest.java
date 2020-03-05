package de.saar.minecraft.integration;

import de.saar.minecraft.architect.ArchitectServer;
import de.saar.minecraft.architect.DummyArchitect;
import de.saar.minecraft.broker.Broker;
import de.saar.minecraft.broker.BrokerConfiguration;
import de.saar.minecraft.broker.TestClient;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.None;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IntegrationTest {
    private ArchitectServer architectServer;
    private Broker broker;
    private TestClient client;

    private static final int ARCHITECT_PORT = 20001;
    private static final int BROKER_PORT = 20002;

    @Before
    public void setup() throws IOException {
        architectServer = new ArchitectServer(ARCHITECT_PORT, () -> new DummyArchitect(0, true));
        architectServer.start();

        BrokerConfiguration config = new BrokerConfiguration();
        config.setPort(BROKER_PORT);
        var addr = new BrokerConfiguration.ArchitectServerAddress("localhost", ARCHITECT_PORT);

        config.setArchitectServers(List.of(addr));

        broker = new Broker(config);
        broker.start();

        client = new TestClient("localhost", BROKER_PORT);
    }

    @After
    public void teardown() throws InterruptedException {
        // broker.stop();
        broker.stop();
        // broker.blockUntilShutdown();
        broker = null;

        client.shutdown();
        client = null;

        architectServer.stop();
        // architectServer.blockUntilShutdown();
        architectServer = null;
    }

    @Test
    public void testConnection() {
        int gameId = client.registerGame("test", null);
        assert gameId > 0;

        client.finishGame(gameId);
    }

    @Test
    public void testQuestionnaire() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        int gameId = client.registerGame("test", new StreamObserver<TextMessage>() {
            @Override
            public void onNext(TextMessage value) {
                var message = value.getText();
                System.out.print("Client got text message: " + message + "\n");
                if (message.equals("Q1")) {
                    latch.countDown();
                }
            }
            @Override
            public void onError(Throwable t) {
                System.out.println("Error occured!");
                System.out.println(t);
            }
            @Override
            public void onCompleted() {
                System.out.println("Client: stream closed");
            }
        });

        client.sendBlockPlacedMessage(gameId, 1,1,1);
        client.sendBlockPlacedMessage(gameId, 1,1,1);

        boolean messageReceived = latch.await(2000, TimeUnit.MILLISECONDS);

        assert messageReceived;
    }

    @Test
    public void testStatusCallback() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();

        int gameId = client.registerGame("test",
            new StreamObserver<>() {
                @Override
                public void onNext(TextMessage value) {
                    System.err.println("received: " + value.getText());
                    receivedMessages.add(value.getText());
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {

                }

                @Override
                public void onCompleted() {

                }
            }
        );

        client.sendStatusMessage(gameId, 1, 2, 3, 0.4, 0.0, -0.7);

        boolean messageReceived = latch.await(2000, TimeUnit.MILLISECONDS);

        assert messageReceived;
        assert !receivedMessages.isEmpty();
        assert receivedMessages.get(0).startsWith("your x was");
        // client.finishGame(gameId);*/
    }

    @Test
    public void testInvalidStatus() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        // gameid -1 is guaranteed to be invalid
        client.registerGame("test",
            new StreamObserver<>() {
                @Override
                public void onNext(TextMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    if (t.getMessage().contains("No game with ID")) {
                        latch.countDown();
                    }
                }

                @Override
                public void onCompleted() {
                }
        });

        client.sendStatusMessage(-1, 1, 2, 3, 0.5, 0.5, 0.5,
            new StreamObserver<None>() {
                @Override
                public void onNext(None value) {
                }

                @Override
                public void onError(Throwable t) {
                    if (t.getMessage().contains("No game with ID")) {
                        latch.countDown();
                    }
                }

                @Override
                public void onCompleted() {
                }
            });

        boolean errorReceived = latch.await(2000, TimeUnit.MILLISECONDS);
        assert errorReceived;
    }
}
