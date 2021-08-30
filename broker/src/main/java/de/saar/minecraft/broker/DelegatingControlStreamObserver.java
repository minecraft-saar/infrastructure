package de.saar.minecraft.broker;

import de.saar.minecraft.broker.db.GameLogsDirection;
import de.saar.minecraft.shared.ProtectBlockMessage;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.stub.StreamObserver;

class DelegatingControlStreamObserver implements StreamObserver<ProtectBlockMessage> {

    private final StreamObserver<ProtectBlockMessage> toClient;
    private final int gameId;
    private final Broker broker;

    public DelegatingControlStreamObserver(int gameId,
                                           StreamObserver<ProtectBlockMessage> toClient,
                                           Broker broker) {
        this.toClient = toClient;
        this.gameId = gameId;
        this.broker = broker;
    }

    @Override
    public void onNext(ProtectBlockMessage value) {
        broker.log(gameId, value, GameLogsDirection.PassToClient);
        toClient.onNext(value);
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
