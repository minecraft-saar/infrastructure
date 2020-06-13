package de.saar.minecraft.broker;

import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.DSLContext;
import org.jooq.Result;

public class ScenarioInformation {
    String scenario;
    DSLContext jooq;
    List<GameInformation> games;

    private static final Logger logger = LogManager.getLogger(GameInformation.class);

    public ScenarioInformation(String scenario, DSLContext jooq) {
        this.scenario = scenario;
        this.jooq = jooq;
        games = new ArrayList<>();
        Result<GamesRecord> result = jooq.selectFrom(Tables.GAMES)
            .where(Tables.GAMES.SCENARIO.equal(scenario))
            .orderBy(Tables.GAMES.ID.asc())
            .fetch();
        for (GamesRecord record: result) {
            games.add(new GameInformation(record.getId(), jooq));
        }
    }

    public int getNumGames() {
        return games.size();
    }

    public float getAverageGameDuration() {
        List<Long> durations = new ArrayList<>();
        for (GameInformation info: games) {
            if (info.wasSuccessful()) {
                durations.add(info.getTimeToSuccess());
            }
        }
        float sum = (float)durations.stream().reduce((long) 0, Long::sum);
        return (sum / durations.size());
    }

    public float getFractionSuccessfulGames() {
        int numSuccessFul = 0;
        for (GameInformation info: games) {
            if (info.wasSuccessful()) {
                numSuccessFul++;
            }
        }
        return (float)numSuccessFul / games.size();
    }

    public float getAverageNumMistakes() {
        int totalMistakes = 0;
        for (GameInformation info: games) {
            // TODO: count all games or only successful games?
            totalMistakes += info.getNumMistakes();
        }
        return (float)totalMistakes / games.size();
    }

    public float getAverageNumBlocksPlaced() {
        int totalBlocks = 0;
        for (GameInformation info: games) {
            // TODO: count all games or only successful games?
            totalBlocks += info.getNumBlocksPlaced();
        }
        return (float)totalBlocks / games.size();
    }

    public float getAverageNumBlocksDestroyed() {
        int totalBlocks = 0;
        for (GameInformation info: games) {
            // TODO: count all games or only successful games?
            totalBlocks += info.getNumBlocksDestroyed();
        }
        return (float)totalBlocks / games.size();
    }

    /**
     * Fraction of players that made at least one mistake
     * @return
     */
    public float getFractionMistakes() {
        int withMistakes = 0;
        for (GameInformation info: games) {
            // TODO: count all games or only successful games?
            if (info.getNumMistakes() > 0) {
                withMistakes++;
            }
        }
        return (float)withMistakes / games.size();
    }
}
