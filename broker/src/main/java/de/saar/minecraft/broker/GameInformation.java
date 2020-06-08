package de.saar.minecraft.broker;

import static de.saar.minecraft.broker.db.Tables.GAMES;
import static de.saar.minecraft.broker.db.Tables.GAME_LOGS;
import static java.time.temporal.ChronoUnit.SECONDS;

import de.saar.minecraft.broker.db.Tables;
import org.jooq.DSLContext;

public class GameInformation {
    int gameId;
    DSLContext jooq;

    public GameInformation(int gameId, DSLContext jooq) {
        this.gameId = gameId;
        this.jooq = jooq;
    }

    public String getScenario() {
        return jooq.select(GAMES.SCENARIO)
            .from(GAMES)
            .where(GAMES.ID.eq(gameId))
            .fetchOne(GAMES.SCENARIO);
    }

    public String getArchitect() {
        return jooq.select(GAMES.ARCHITECT_INFO)
            .from(GAMES)
            .where(GAMES.ID.eq(gameId))
            .fetchOne(GAMES.ARCHITECT_INFO);
    }

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

    public boolean wasSuccessful() {
        return jooq.fetchExists(
            jooq.selectOne()
            .from(GAME_LOGS)
            .where(GAME_LOGS.GAMEID.eq(gameId))
            .and(GAME_LOGS.MESSAGE.contains("\"newGameState\":\"SuccessfullyFinished\"")));
    }

    public Object timeToSuccess() {
        assert wasSuccessful();
        var timeFinished = jooq.select(GAME_LOGS.TIMESTAMP)
            .from(GAME_LOGS)
            .where(GAME_LOGS.GAMEID.eq(gameId))
            .and(GAME_LOGS.MESSAGE.contains("\"newGameState\":\"SuccessfullyFinished\""))
            .fetchOne(GAME_LOGS.TIMESTAMP);
        if (timeFinished == null) {
            throw new AssertionError("Can't measure time to success without success");
        }

        var timeStarted = jooq.select(GAME_LOGS.TIMESTAMP)
            .from(GAME_LOGS)
            .where(GAME_LOGS.GAMEID.eq(gameId))
            .orderBy(GAME_LOGS.ID.asc())
            .fetchOne(GAME_LOGS.TIMESTAMP);

        return timeStarted.until(timeFinished, SECONDS);
    }

}
