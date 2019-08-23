package de.saar.minecraft.integration;

import de.saar.minecraft.architect.ArchitectServer;
import de.saar.minecraft.architect.DummyArchitect;
import de.saar.minecraft.broker.Broker;
import de.saar.minecraft.broker.BrokerConfiguration;
import de.saar.minecraft.broker.TestClient;
import de.saar.minecraft.shared.TextMessage;
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

    @Before
    public void setup() throws IOException {
        architectServer = new ArchitectServer(10000, () -> new DummyArchitect());
        architectServer.start();

        BrokerConfiguration config = new BrokerConfiguration();
        config.setPort(2802);
        config.setArchitectServer(new BrokerConfiguration.ArchitectServerAddress("localhost", 10000));

        broker = new Broker(config);
        broker.start();

        client = new TestClient("localhost", 2802);
    }

    @After
    public void teardown() throws InterruptedException {
        client.shutdown();
        client = null;

        broker.stop();
        broker.blockUntilShutdown();
        broker = null;

        architectServer.stop();
        architectServer.blockUntilShutdown();
        architectServer = null;
    }

    @Test
    public void testConnection() {
        int gameId = client.registerGame("test");
        assert gameId > 0;

        client.finishGame(gameId);
    }

    @Test
    public void testStatusCallback() throws InterruptedException {
        int gameId = client.registerGame("test");

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();

        client.sendStatusMessage(gameId, 1, 2, 3, new StreamObserver<TextMessage>() {
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
        });

        boolean messageReceived = latch.await(2000, TimeUnit.MILLISECONDS);

        assert messageReceived;
        assert ! receivedMessages.isEmpty();
        assert receivedMessages.get(0).startsWith("your x was");

        client.finishGame(gameId);
    }

    @Test
    public void testInvalidStatus() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // gameid -1 is guaranteed to be invalid
        client.sendStatusMessage(-1, 1, 2, 3, new StreamObserver<TextMessage>() {
            @Override
            public void onNext(TextMessage value) {
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {

            }
        });

        boolean errorReceived = latch.await(2000, TimeUnit.MILLISECONDS);
        assert errorReceived;
    }
}