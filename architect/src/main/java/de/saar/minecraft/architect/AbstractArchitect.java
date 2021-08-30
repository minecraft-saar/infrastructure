package de.saar.minecraft.architect;

import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.ProtectBlockMessage;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.stub.StreamObserver;

public abstract class AbstractArchitect implements Architect {
    protected StreamObserver<TextMessage> messageChannel;
    protected StreamObserver<ProtectBlockMessage> controlChannel;
    protected int gameId;

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

}
