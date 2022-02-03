package de.saar.minecraft.architect;

import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.ProtectBlockMessage;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.stub.StreamObserver;
import org.tinylog.Logger;

/**
 * defines some basic functionalities of the architect such as logging and passing messages along.
 **/
public abstract class AbstractArchitect implements Architect {
    /**
     * channel for message exchange with the MC server.
     **/
    protected StreamObserver<TextMessage> messageChannel;
    /**
     * channel messages about block type changes the MC server should do.
     **/
    protected StreamObserver<ProtectBlockMessage> controlChannel;
    /**
     * id of the game we are connected to.
     **/
    protected int gameId;
    //    private static final Logger logger = LogManager.getLogger(AbstractArchitect.class);
    /**
     * has the player left?.
     **/
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

    /**
     * Save ID of the game we are connected to.
     *
     * @param gameId id of the game
     **/
    public void setGameId(int gameId) {
        this.gameId = gameId;
    }


    @Override
    public void setMessageChannel(StreamObserver<TextMessage> messageChannel) {
        Logger.debug("setting message channel");
        this.messageChannel = messageChannel;
    }


    /**
     * send the text message back to the client.
     *
     * @param text the message to send
     */
    protected void sendMessage(String text) {
        sendMessage(text, NewGameState.NotChanged);
    }

    /**
     * send the text message back to the client.
     *
     * @param text         If the message starts with "{", the text will be interpreted as
     *                     a json object and only the "message" field will be forwarded
     *                     to the player.  Use this feature to add metadata you want to have
     *                     logged into the database by the broker.
     * @param newGameState new state of the game
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
     *
     * @param x    x coordinate of block
     * @param y    y coordinate of block
     * @param z    z coordinate of block
     * @param type the block will be turned to this type. Types are listed in org.bukkit.Material
     */
    protected void sendControlMessage(int x, int y, int z, String type) {
        ProtectBlockMessage message = ProtectBlockMessage.newBuilder()
                .setGameId(gameId)
                .setX(x)
                .setY(y)
                .setZ(z)
                .setType(type)
                .build();
        synchronized (this) {
            try {
                controlChannel.onNext(message);
            } catch (NullPointerException e) {
                onControlChannelClosed();
            }
        }
    }

    /**
     * Sends a message to the broker which will be stored in the database but
     * not forwarded to the player.
     *
     * @param logMessage the message to be logged
     * @param logType    the type of message
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

    private void onControlChannelClosed() {
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
