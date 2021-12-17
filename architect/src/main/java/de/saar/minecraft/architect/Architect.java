package de.saar.minecraft.architect;

import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.ProtectBlockMessage;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.stub.StreamObserver;

/**
 * An Architect for generating instructions in Minecraft.
 *
 * <p>Creating an object of a subclass of Architect should be cheap, because
 * it is done several times during the setup of the Architect Server that
 * contains it. Any substantial initialization effort should happen in the
 * {@link #initialize(WorldSelectMessage)} method.</p>
 */
public interface Architect {
    /**
     * Initializes the Architect. Use this method for any computationally
     * nontrivial initialization efforts your Architect implementation required.
     * This method is guaranteed to be called before any of the other methods
     * of this interface.
     *
     * @param request is the message containing request.id = id of the game we create an architect
     *               for and request.name being the scenario we want an architect for.
     */
    void initialize(WorldSelectMessage request);

    /**
     * the player is ready now and you can start giving instructions.
     **/
    void playerReady();

    /**
     * Called when a game is finished.  Should de-initialize everything
     * and close the messageChannel.
     */
    void shutdown();

    /**
     * initialize message channel used for sending and receiving messages from the MC server.
     *
     * @param messageChannel the message channel
     **/
    void setMessageChannel(StreamObserver<TextMessage> messageChannel);

    /**
     * initialize control channel for block changes the MC server should do.
     *
     * @param controlChannel the control channel
     **/
    void setControlChannel(StreamObserver<ProtectBlockMessage> controlChannel);

    /**
     * Handles the regular status updates from the Minecraft server.
     * This method is called frequently, and should thus return quickly.
     * Spawn off a separate thread if you need to perform an expensive
     * computation, and then send any strings you like to the
     * responseObserver.
     *
     * @param request contains: id, being the game ID; x,y,z are the coordinates' location
     *               where the player is standing;
     *       x-, y-, zDirection is a unit-vector that points into the direction the player is facing
     */
    void handleStatusInformation(StatusMessage request);

    /**
     * Handles updates when a block is placed in the Minecraft world.
     *
     * @param request contains game ID, coordinates and block type
     */
    void handleBlockPlaced(BlockPlacedMessage request);

    /**
     * Handles updates when a block in the Minecraft world is destroyed.
     *
     * @param request contains game ID coordinates and block type
     */
    void handleBlockDestroyed(BlockDestroyedMessage request);

    /**
     * Returns a string which identifies this Architect. The string might
     * include the class name and version information.
     *
     * @return the name of the architect
     */
    String getArchitectInformation();
}
