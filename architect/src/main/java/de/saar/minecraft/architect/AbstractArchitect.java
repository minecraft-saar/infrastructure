package de.saar.minecraft.architect;

import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.ProtectBlockMessage;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractArchitect implements Architect {
    protected StreamObserver<TextMessage> messageChannel;
    protected StreamObserver<ProtectBlockMessage> controlChannel;
    protected int gameId;
    private static Logger logger = LogManager.getLogger(AbstractArchitect.class);
    protected boolean playerHasLeft = false;

    @Override
    public void shutdown() {
        if (messageChannel != null) {
            messageChannel.onCompleted();
            messageChannel = null;
        }
        if (controlChannel != null) {
            controlChannel.onCompleted();
            controlChannel = null;
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
     * If the message starts with "{", the text will be interpreted as
     * a json object and only the "message" field will be forwarded
     * to the player.  Use this feature to add metadata you want to have
     * logged into the database by the broker.
     */
    protected void sendMessage(String text, NewGameState newGameState) {
        TextMessage message = TextMessage.newBuilder()
            .setGameId(gameId)
            .setText(text)
            .setNewGameState(newGameState)
            .build();
        synchronized (this) {
            try {
                messageChannel.onNext(message);
            } catch (NullPointerException e) {
                onMessageChannelClosed();
            }
        }
    }

    @Override
    public void setControlChannel(StreamObserver<ProtectBlockMessage> controlChannel) {
        System.err.println("setting control channel");
        this.controlChannel = controlChannel;
    }

    /**
     * send the block to be protected message back to the minecraft server.
     */
    protected void sendControlMessage(int x, int y, int z, String type) {
        ProtectBlockMessage message = ProtectBlockMessage.newBuilder()
                .setGameId(gameId)
                .setX(x)
                .setY(y)
                .setZ(z)
                .setType(type)
                .build();
        synchronized (controlChannel) {
            controlChannel.onNext(message);
        }
    }

    /**
     * Sends a message to the broker which will be stored in the database but
     * not forwarded to the player.
     */
    protected void log(String logMessage, String logType) {
        TextMessage message = TextMessage.newBuilder()
            .setGameId(gameId)
            .setText(logMessage)
            .setForLogging(true)
            .setLogType(logType)
            .build();
        synchronized (this) {
            try {
                messageChannel.onNext(message);
            } catch (NullPointerException e) {
                onMessageChannelClosed();
            }
        }
    }
    
    private void onMessageChannelClosed() {
        if (!playerHasLeft) {
            playerHasLeft = true;
            playerLeft();
        }
    }
    
    /**
     * This function gets called when the messageChannel closes.
     * Implement this function to determine what should happen then.
     */
    protected abstract void playerLeft();
    
}
