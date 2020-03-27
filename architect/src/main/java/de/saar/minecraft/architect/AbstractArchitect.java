package de.saar.minecraft.architect;

import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.stub.StreamObserver;

public abstract class AbstractArchitect implements Architect {
    protected StreamObserver<TextMessage> messageChannel;
    protected int gameId;

    @Override
    public void shutdown() {
        if (messageChannel != null) {
            messageChannel.onCompleted();
            messageChannel = null;
        }
    }

    public void setGameId(int gameId) {
        this.gameId = gameId;
    }

    @Override
    public void setMessageChannel(StreamObserver<TextMessage> messageChannel) {
        System.err.println("setting message channel");
        this.messageChannel = messageChannel;
    }

    /**
     * send the text message back to the client.
     */
    protected void sendMessage(String text) {
        sendMessage(text, NewGameState.NotChanged);
    }

    /**
     * send the text message back to the client.
     */
    protected void sendMessage(String text, NewGameState newGameState) {
        TextMessage message = TextMessage.newBuilder()
            .setGameId(gameId)
            .setText(text)
            .setNewGameState(newGameState)
            .build();
        synchronized (messageChannel) {
            messageChannel.onNext(message);
        }
    }
}
