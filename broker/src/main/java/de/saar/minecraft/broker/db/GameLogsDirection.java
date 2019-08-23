package de.saar.minecraft.broker.db;

/**
 * An enum type for the "direction" column of the "game_logs" table.
 * Don't change the names of the enum values without thinking very carefully about it;
 * they will be used literally as strings in the database, so changes may make things
 * inconsistent.
 *
 */
public enum GameLogsDirection {
    FromClient,
    ToClient,
    FromArchitect,
    ToArchitect,
    PassToClient,
    PassToArchitect,
    None
}
