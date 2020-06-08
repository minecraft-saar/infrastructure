package de.saar.minecraft.broker;

import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;

import de.saar.minecraft.broker.db.GameLogsDirection;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.GameLogs;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.shared.TextMessage;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.DSLContext;
import org.jooq.Result;

public class Statistics {

    private static final Logger logger = LogManager.getLogger(Statistics.class);
    private final Broker broker;
    private final DSLContext jooq;

    public Statistics(Broker broker) {
        this.broker = broker;
        jooq = broker.getJooq();
    }

    /**
     * Returns the duration until the user logged out.  Note: This may be much longer than
     * until task completion!
     * @return Seconds elapsed between login and logout
     */
    public long getExperimentDuration(int gameId) {
        Result<GameLogsRecord> gameLog = jooq.selectFrom(Tables.GAME_LOGS)
            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
            .orderBy(Tables.GAME_LOGS.ID.asc())
            .fetch();
        LocalDateTime startTime = gameLog.get(0).getTimestamp();
        LocalDateTime endTime = gameLog.get(gameLog.size() - 1).getTimestamp();
        return startTime.until(endTime, SECONDS);
    }

    public LocalDateTime getEndTime(int gameId) {
        Result<GameLogsRecord> gameLog = jooq.selectFrom(Tables.GAME_LOGS)
            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
            .orderBy(Tables.GAME_LOGS.ID.asc())
            .fetch();
        return gameLog.get(gameLog.size() - 1).getTimestamp();
    }

//    public HashMap<String, Float> getDurationPerInstruction(int gameId) {
//        Result<GameLogsRecord> gameLog = jooq.selectFrom(Tables.GAME_LOGS)
//            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
//            .orderBy(Tables.GAME_LOGS.ID.asc())
//            .fetch();
//
//        HashMap<String, Float> durations = new HashMap<>();
//        String currentInstruction = "";
//        Timestamp instructionTime = gameLog.get(0).getTimestamp();
//        for (GameLogsRecord logEntry: gameLog) {
//            if (logEntry.getMessageType().equals("TextMessage")) {
//                logger.info("is a text message");
//                if (logEntry.getDirection().equals(GameLogsDirection.PassToClient)) {
//                    currentInstruction = logEntry.getMessage();
//                    instructionTime = logEntry.getTimestamp();
//                    continue;
//                }
//            }
//            if (logEntry.getDirection().equals(GameLogsDirection.FromClient)) {
//                if (logEntry.getMessageType().equals("BlockPlacedMessage")) {
//                    // Compute duration
//                    float duration = logEntry.getTimestamp().compareTo(instructionTime);
//                    durations.put(currentInstruction, duration);
//
//                }
//            }
//        }
//        return durations;
//    }

    public List<Instruction> extractInstructions(int gameId) {
        Result<GameLogsRecord> gameLog = jooq.selectFrom(Tables.GAME_LOGS)
            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
            .orderBy(Tables.GAME_LOGS.ID.asc())
            .fetch();

        // An instructions begins when the Architect gives a text message
        // TODO: database does not distinguish between Architect and Broker messages
        // An instruction ends when the player places or breaks a block or sends a text message
        // An instruction ends unsuccessfully if there is a new text message before the player reacts
        String currentInstruction = "";
        LocalDateTime instructionTime = gameLog.get(0).getTimestamp();
        List<Instruction> instructions = new ArrayList<>();
        for (GameLogsRecord logEntry: gameLog) {
            if (logEntry.getDirection().equals(GameLogsDirection.PassToClient)) {
                if (logEntry.getMessageType().equals("TextMessage")) {
                    if (!currentInstruction.equals("")) {
                        instructions.add(new Instruction(instructionTime,
                            logEntry.getTimestamp(),
                            currentInstruction,
                            "",
                            false));
                    }
                    currentInstruction = logEntry.getMessage();
                    instructionTime = logEntry.getTimestamp();
                }
            } else if (logEntry.getDirection().equals(GameLogsDirection.FromClient)) {
                if (logEntry.getMessageType().equals("BlockPlacedMessage")
                    || logEntry.getMessageType().equals("BlockDestroyMessage")
                    || logEntry.getMessageType().equals("TextMessage")) {
                    instructions.add(new Instruction(instructionTime,
                        logEntry.getTimestamp(),
                        currentInstruction,
                        logEntry.getMessage(),
                        true));
                    currentInstruction = "";
                    instructionTime = logEntry.getTimestamp();

                }
            }
        }
        return instructions;
    }

    public static class Instruction {
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public long duration;
        public String text;
        public String reaction;
        public boolean successful;

        public Instruction(LocalDateTime startTime, LocalDateTime endTime, String text, String reaction, boolean successful) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = startTime.until(endTime, NANOS);
            this.text = text;
            this.reaction = reaction;
            this.successful = successful;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public void setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
        }

        public void setReaction(String reaction) {
            this.reaction = reaction;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public void setText(String text) {
            this.text = text;
        }

        public void setSuccessful(boolean successful) {
            this.successful = successful;
        }
    }

    public Object getMistakes(int gameId) {
        Result<GameLogsRecord> gameLog = jooq.selectFrom(Tables.GAME_LOGS)
            .where(Tables.GAME_LOGS.GAMEID.equal(gameId))
            .orderBy(Tables.GAME_LOGS.ID.asc())
            .fetch();
        // Iterate over log entries
        // if a text message to the client contains "Please add that again" or similar
        // -- make a new mistake entry:
        // -- original instruction, which block was placed
        return null;
    }

}
