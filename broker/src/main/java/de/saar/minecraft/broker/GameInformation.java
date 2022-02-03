package de.saar.minecraft.broker;

import static de.saar.minecraft.broker.db.Tables.GAMES;
import static de.saar.minecraft.broker.db.Tables.GAME_LOGS;
import static java.time.temporal.ChronoUnit.SECONDS;

import de.saar.minecraft.broker.db.Tables;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.DSLContext;

/**
 * access to basic information about a game, such as the architect used, the scanerio played,
 * success of the game and completion times.
 */
public class GameInformation {
    int gameId;
    DSLContext jooq;

    /**
     * constructor.
     * @param gameId id of the game
     * @param jooq database
     */
    public GameInformation(int gameId, DSLContext jooq) {
        this.gameId = gameId;
        this.jooq = jooq;
    }

    /**
     * returns the secanrio name.
     * @return scanario
     */
    public String getScenario() {
        return jooq.select(GAMES.SCENARIO)
                .from(GAMES)
                .where(GAMES.ID.eq(gameId))
                .fetchOne(GAMES.SCENARIO);
    }

    /**
     * returns the architect info.
     * @return architect info
     */
    public String getArchitect() {
        return jooq.select(GAMES.ARCHITECT_INFO)
                .from(GAMES)
                .where(GAMES.ID.eq(gameId))
                .fetchOne(GAMES.ARCHITECT_INFO);
    }

    /*  no longer needed here all game analysis is done in experiment-analysis project
    public int getNumBlocksPlaced() {
        return jooq.selectCount()
                .from(GAME_LOGS)
                .where(GAME_LOGS.GAMEID.eq(gameId))
                .and(GAME_LOGS.MESSAGE_TYPE.eq("BlockPlacedMessage"))
                .fetchOne(0, int.class);
    }

    public int getNumBlocksDestroyed() {
        return jooq.selectCount()
                .from(GAME_LOGS)
                .where(GAME_LOGS.GAMEID.eq(gameId))
                .and(GAME_LOGS.MESSAGE_TYPE.eq("BlockDestroyedMessage"))
                .fetchOne(0, int.class);
    }

    public int getNumMistakes() {
        return jooq.selectCount()
                .from(GAME_LOGS)
                .where(GAME_LOGS.GAMEID.eq(gameId))
                .and(GAME_LOGS.MESSAGE.contains("Not there! please remove that block again"))
                .fetchOne(0, int.class);
    }
*/
    /** was the game successful?.
     * @return True if the game was successfully finished, false if stopped early
     */
    public boolean wasSuccessful() {
        var selection = jooq.select()
                .from(GAME_LOGS)
                .where(GAME_LOGS.GAMEID.eq(gameId))
                .and(GAME_LOGS.MESSAGE.contains("\"newGameState\": \"SuccessfullyFinished\""))
                .fetch();
        return (!selection.isEmpty());
    }

    /** get time between the experiment start and successfully completing the building.
     * @return the time in seconds
     */
    public long getTimeToSuccess() {
        assert wasSuccessful();
        var timeFinished = getSuccessTime();
        if (timeFinished == null) {
            throw new AssertionError("Can't measure time to success without success");
        }
        var timeStarted = getStartTime();
        return timeStarted.until(timeFinished, SECONDS);
    }

    /**
     * Returns the duration until the user logged out.  Note: This may be much longer than
     * until task completion!
     *
     * @return Seconds elapsed between login and logout
     */
    public long getTotalTime() {
        var timeStarted = getStartTime();
        var timeFinished = getEndTime();
        return timeStarted.until(timeFinished, SECONDS);
    }

    /** returns the first Timestamp of the game.
     * @return the first Timestamp of the game
     */
    public LocalDateTime getStartTime() {
        return jooq.select(GAME_LOGS.TIMESTAMP)
                .from(GAME_LOGS)
                .where(GAME_LOGS.GAMEID.eq(gameId))
                .orderBy(GAME_LOGS.ID.asc())
                .fetchAny(GAME_LOGS.TIMESTAMP);
    }

    /** returns success time.
     * @return the Timestamp when the game state changed to SuccessfullyFinished
     */
    public LocalDateTime getSuccessTime() {
        assert wasSuccessful();
        return jooq.select(GAME_LOGS.TIMESTAMP)
                .from(GAME_LOGS)
                .where(GAME_LOGS.GAMEID.eq(gameId))
                .and(GAME_LOGS.MESSAGE.contains("\"newGameState\": \"SuccessfullyFinished\""))
                .fetchOne(GAME_LOGS.TIMESTAMP);
    }

    /** returns last timestamp in game.
     * @return the last Timestamp of the game
     */
    public LocalDateTime getEndTime() {
        return jooq.select(GAME_LOGS.TIMESTAMP)
                .from(GAME_LOGS)
                .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
                .orderBy(Tables.GAME_LOGS.ID.desc())
                .fetchAny(GAME_LOGS.TIMESTAMP);
    }

}
