package de.saar.minecraft.architect;

import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.stub.StreamObserver;

/**
 * An Architect for generating instructions in Minecraft.
 *
 * Creating an object of a subclass of Architect should be cheap, because
 * it is done several times during the setup of the Architect Server that
 * contains it. Any substantial initialization effort should happen in the
 * {@link #initialize(WorldSelectMessage)} method.
 *
 */
public interface Architect {
    /**
     * Initializes the Architect. Use this method for any computationally
     * nontrivial initialization efforts your Architect implementation required.
     * This method is guaranteed to be called before any of the other methods
     * of this interface.
     */
    public void initialize(WorldSelectMessage request);

    /**
     * Handles the regular status updates from the Minecraft server.
     * This method is called frequently, and should thus return quickly.
     * Spawn off a separate thread if you need to perform an expensive
     * computation, and then send any strings you like to the
     * responseObserver.
     *
     * @param request
     * @param responseObserver
     */
    public void handleStatusInformation(StatusMessage request, StreamObserver<TextMessage> responseObserver);

    /**
     * Handles updates when a block is placed in the Minecraft world
     * @param request
     * @param responseObserver
     */
    public void handleBlockPlaced(BlockPlacedMessage request, StreamObserver<TextMessage> responseObserver);

    /**
     * Handles updates when a block in the Minecraft world is destroyed
     * @param request
     * @param responseObserver
     */
    public void handleBlockDestroyed(BlockDestroyedMessage request, StreamObserver<TextMessage> responseObserver);

    /**
     * Returns a string which identifies this Architect. The string might
     * include the class name and version information.
     *
     * @return
     */
    public String getArchitectInformation();
}
