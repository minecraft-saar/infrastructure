package de.saar.minecraft.architect;

import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractArchitect implements Architect {
    protected StreamObserver<TextMessage> messageChannel;
    protected int gameId;
    private static Logger logger = LogManager.getLogger(AbstractArchitect.class);

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
        logger.debug("setting message channel");
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
        synchronized (this) {
            messageChannel.onNext(message);
        }
    }

    protected void log(String logMessage, String logType) {
        TextMessage message = TextMessage.newBuilder()
            .setGameId(gameId)
            .setText(logMessage)
            .setForLogging(true)
            .setLogType(logType)
            .build();
        synchronized (this) {
            messageChannel.onNext(message);
        }
    }
}
