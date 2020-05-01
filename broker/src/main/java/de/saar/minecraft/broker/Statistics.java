package de.saar.minecraft.broker;

import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import java.sql.Timestamp;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.Result;

public class Statistics {

    private static final Logger logger = LogManager.getLogger(Statistics.class);
    private final Broker broker;

    public Statistics (Broker broker) {
        this.broker = broker;
    }


    public int getExperimentDuration(int gameId) {
        Result<GameLogsRecord> gameLog = broker.getJooq()
            .selectFrom(Tables.GAME_LOGS)
            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
            .orderBy(Tables.GAME_LOGS.ID.asc())
            .fetch();
        Timestamp startTime = gameLog.get(0).getTimestamp();
        Timestamp endTime = gameLog.get(gameLog.size() - 1).getTimestamp();
        return endTime.compareTo(startTime);
    }

    public Timestamp getEndTime(int gameId) {
        Result<GameLogsRecord> gameLog = broker.getJooq()
            .selectFrom(Tables.GAME_LOGS)
            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
            .orderBy(Tables.GAME_LOGS.ID.asc())
            .fetch();
        return gameLog.get(gameLog.size() - 1).getTimestamp();
    }

    public HashMap getDurationPerInstruction(int gameId) {
        return null;
    }

    public Object getMistakes(int gameId) {
        return null;
    }

}
