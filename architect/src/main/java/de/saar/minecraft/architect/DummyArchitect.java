package de.saar.minecraft.architect;

import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.WorldSelectMessage;

/**
 * creates a dummy architect for testing.
 **/
public class DummyArchitect extends AbstractArchitect {
    private final int waitTime;
    private int statusIteration;
    private final int responseFrequency;

    private final boolean endAfterFirstBlock;

    /** creates a dummy architect.
     * @param waitTime time between messages to MC Server in milliseconds
     * @param endAfterFirstBlock stop server after first placed block
     * @param responseFrequency do you want to respond to every status update?
     *                          Use 1, to every second update use 2 etc.**/
    public DummyArchitect(int waitTime, boolean endAfterFirstBlock, int responseFrequency) {
        this.endAfterFirstBlock = endAfterFirstBlock;
        this.waitTime = waitTime;
        this.statusIteration = 0;
        this.responseFrequency = responseFrequency;
    }

    /** creates architect, it responds to every status update, runs for multiple block placements.
     * @param waitTime time between messages to MC Server in milliseconds**/
    public DummyArchitect(int waitTime) {
        this.waitTime = waitTime;
        this.endAfterFirstBlock = false;
        this.statusIteration = 0;
        this.responseFrequency = 1;
    }

    /** basic dummy architect. **/
    public DummyArchitect() {
        this(1000);
    }

    @Override
    public void initialize(WorldSelectMessage request) {
        System.err.println("Got world " + request.getName());
        setGameId(request.getGameId());
    }

    @Override
    public void playerReady() {

    }

    @Override
    public void handleStatusInformation(StatusMessage request) {
        int x = request.getX();
        double xdir = request.getXDirection();

        // send only for every twentieth status update a message
        if (++statusIteration < responseFrequency) {
            return;
        }
        statusIteration = 0;

        // spawn a thread for a long-running computation
        new Thread(() -> {
            String text = "your x was " + x + " and you looked in x direction " + xdir;
            // delay for a bit
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // send the text message back to the client
            sendMessage(text);
        }).start();
    }

    @Override
    public void handleBlockPlaced(BlockPlacedMessage request) {
        String type = request.getType();
        int x = request.getX();
        int y = request.getY();
        int z = request.getZ();

        // spawn a thread for a long-running computation
        new Thread(() -> {
            String text = String.format("A block was placed at %d-%d-%d :%s", x, y, z, type);
            var gameState = NewGameState.NotChanged;
            if (endAfterFirstBlock) {
                gameState = NewGameState.SuccessfullyFinished;
            }
            // delay for a bit
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendMessage(text, gameState);
        }).start();
    }

    @Override
    public void handleBlockDestroyed(BlockDestroyedMessage request) {
        int x = request.getX();
        int y = request.getY();
        int z = request.getZ();
        String type = request.getType();

        // spawn a thread for a long-running computation
        new Thread(() -> {
            var text = String.format("A block was destroyed at %d-%d-%d :%s", x, y, z, type);
            // delay for a bit
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendMessage(text);
        }).start();
    }

    @Override
    public String getArchitectInformation() {
        return "DummyArchitect";
    }

    @Override
    protected void playerLeft() {
        System.err.println("Player disconnected.");
    }
}
