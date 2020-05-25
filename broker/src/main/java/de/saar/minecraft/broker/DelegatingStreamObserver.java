package de.saar.minecraft.broker;

import de.saar.minecraft.broker.db.GameLogsDirection;
import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.stub.StreamObserver;

/**
 * A DelegatingStreamObserver acts as a proxy in connections from the Architect to the Client.
 * All messages are logged into the database and forwarded.
 */
class DelegatingStreamObserver implements StreamObserver<TextMessage> {
    private StreamObserver<TextMessage> toClient;
    private int gameId;
    private Broker broker;

    public DelegatingStreamObserver(int gameId,
                                    StreamObserver<TextMessage> toClient,
                                    Broker broker) {
        this.toClient = toClient;
        this.gameId = gameId;
        this.broker = broker;
    }

    @Override
    public synchronized void onNext(TextMessage value) {
        if (value.getNewGameState() == NewGameState.SuccessfullyFinished) {
            broker.startQuestionnaire(gameId, this);
        }
        // Text message for logging purposes only, log and don't forward.
        if (value.getForLogging()) {
            broker.log(gameId, value.getText(), value.getLogType(),
                GameLogsDirection.LogFromArchitect);
            return;
        }
        toClient.onNext(value);
        broker.log(gameId, value, GameLogsDirection.PassToClient);
    }

    @Override
    public void onError(Throwable t) {
        broker.log(gameId, t, GameLogsDirection.PassToClient);
        toClient.onError(t);
    }

    @Override
    public void onCompleted() {
        toClient.onCompleted();
    }
}
