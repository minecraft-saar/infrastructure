/*
 * This file is generated by jOOQ.
 */
package de.saar.minecraft.matchmaker.db.tables.records;


import de.saar.minecraft.matchmaker.db.enums.GamesStatus;
import de.saar.minecraft.matchmaker.db.tables.Games;

import java.sql.Timestamp;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record8;
import org.jooq.Row8;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.12"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class GamesRecord extends UpdatableRecordImpl<GamesRecord> implements Record8<Integer, String, String, Timestamp, GamesStatus, String, Integer, String> {

    private static final long serialVersionUID = -1226532926;

    /**
     * Setter for <code>minecraft.games.id</code>.
     */
    public void setId(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>minecraft.games.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>minecraft.games.client_ip</code>.
     */
    public void setClientIp(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>minecraft.games.client_ip</code>.
     */
    public String getClientIp() {
        return (String) get(1);
    }

    /**
     * Setter for <code>minecraft.games.player_name</code>.
     */
    public void setPlayerName(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>minecraft.games.player_name</code>.
     */
    public String getPlayerName() {
        return (String) get(2);
    }

    /**
     * Setter for <code>minecraft.games.start_time</code>.
     */
    public void setStartTime(Timestamp value) {
        set(3, value);
    }

    /**
     * Getter for <code>minecraft.games.start_time</code>.
     */
    public Timestamp getStartTime() {
        return (Timestamp) get(3);
    }

    /**
     * Setter for <code>minecraft.games.status</code>.
     */
    public void setStatus(GamesStatus value) {
        set(4, value);
    }

    /**
     * Getter for <code>minecraft.games.status</code>.
     */
    public GamesStatus getStatus() {
        return (GamesStatus) get(4);
    }

    /**
     * Setter for <code>minecraft.games.architect_hostname</code>.
     */
    public void setArchitectHostname(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>minecraft.games.architect_hostname</code>.
     */
    public String getArchitectHostname() {
        return (String) get(5);
    }

    /**
     * Setter for <code>minecraft.games.architect_port</code>.
     */
    public void setArchitectPort(Integer value) {
        set(6, value);
    }

    /**
     * Getter for <code>minecraft.games.architect_port</code>.
     */
    public Integer getArchitectPort() {
        return (Integer) get(6);
    }

    /**
     * Setter for <code>minecraft.games.architect_info</code>.
     */
    public void setArchitectInfo(String value) {
        set(7, value);
    }

    /**
     * Getter for <code>minecraft.games.architect_info</code>.
     */
    public String getArchitectInfo() {
        return (String) get(7);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record8 type implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Row8<Integer, String, String, Timestamp, GamesStatus, String, Integer, String> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row8<Integer, String, String, Timestamp, GamesStatus, String, Integer, String> valuesRow() {
        return (Row8) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Integer> field1() {
        return Games.GAMES.ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field2() {
        return Games.GAMES.CLIENT_IP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field3() {
        return Games.GAMES.PLAYER_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Timestamp> field4() {
        return Games.GAMES.START_TIME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<GamesStatus> field5() {
        return Games.GAMES.STATUS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field6() {
        return Games.GAMES.ARCHITECT_HOSTNAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Integer> field7() {
        return Games.GAMES.ARCHITECT_PORT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field8() {
        return Games.GAMES.ARCHITECT_INFO;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer component1() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component2() {
        return getClientIp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component3() {
        return getPlayerName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Timestamp component4() {
        return getStartTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesStatus component5() {
        return getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component6() {
        return getArchitectHostname();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer component7() {
        return getArchitectPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component8() {
        return getArchitectInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer value1() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value2() {
        return getClientIp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value3() {
        return getPlayerName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Timestamp value4() {
        return getStartTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesStatus value5() {
        return getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value6() {
        return getArchitectHostname();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer value7() {
        return getArchitectPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value8() {
        return getArchitectInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value1(Integer value) {
        setId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value2(String value) {
        setClientIp(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value3(String value) {
        setPlayerName(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value4(Timestamp value) {
        setStartTime(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value5(GamesStatus value) {
        setStatus(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value6(String value) {
        setArchitectHostname(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value7(Integer value) {
        setArchitectPort(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord value8(String value) {
        setArchitectInfo(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GamesRecord values(Integer value1, String value2, String value3, Timestamp value4, GamesStatus value5, String value6, Integer value7, String value8) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached GamesRecord
     */
    public GamesRecord() {
        super(Games.GAMES);
    }

    /**
     * Create a detached, initialised GamesRecord
     */
    public GamesRecord(Integer id, String clientIp, String playerName, Timestamp startTime, GamesStatus status, String architectHostname, Integer architectPort, String architectInfo) {
        super(Games.GAMES);

        set(0, id);
        set(1, clientIp);
        set(2, playerName);
        set(3, startTime);
        set(4, status);
        set(5, architectHostname);
        set(6, architectPort);
        set(7, architectInfo);
    }
}
