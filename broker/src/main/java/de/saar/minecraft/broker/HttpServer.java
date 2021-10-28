package de.saar.minecraft.broker;

import au.com.codeka.carrot.CarrotEngine;
import au.com.codeka.carrot.CarrotException;
import au.com.codeka.carrot.Configuration;
import au.com.codeka.carrot.bindings.MapBindings;
import au.com.codeka.carrot.resource.MemoryResourceLocator;
import au.com.codeka.carrot.resource.ResourceLocator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import de.saar.minecraft.broker.db.tables.records.QuestionnairesRecord;
import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.ProtectBlockMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.util.Util;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.Result;

public class HttpServer {
    private static final Logger logger = LogManager.getLogger(HttpServer.class);
    private CarrotEngine engine;
    private Broker broker;

    private static class MyAuthenticator extends BasicAuthenticator {
        String user = System.getProperty("HTTPUser", "mcsaar");
        String pass = System.getProperty("HTTPPass", "mcsaar");

        MyAuthenticator() {
            super("enter password");
        }

        @Override
        public boolean checkCredentials(String user, String pwd) {
            return user.equals(this.user) && pwd.equals(this.pass);
        }
    }

    /**
     * Starts the HTTP server, displaying information about the broker.
     */
    public void start(Broker broker) throws IOException {
        int port = broker.getConfig().getHttpPort();

        if (port == 0) {
            throw new RuntimeException("No HTTP port specified in config file.");
        }

        this.broker = broker;

        // start HTTP server
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        var context = server.createContext("/", new MyHandler());
        context.setAuthenticator(new MyAuthenticator());
        server.setExecutor(null); // creates a default executor
        server.start();

        // set up templating engine
        engine = new CarrotEngine(new Configuration.Builder()
                .setResourceLocator(makeResourceLocator())
                .build());

        logger.info("HTTP server running on port " + port + ".");
    }

    private ResourceLocator.Builder makeResourceLocator() {
        MemoryResourceLocator.Builder ret = new MemoryResourceLocator.Builder();

        ret.add("index.html", slurp("index.html"));
        ret.add("showgame.html", slurp("showgame.html"));
        ret.add("showprettygame.html", slurp("showprettygame.html"));
        ret.add("showquestionnaire.html", slurp("showquestionnaire.html"));
        ret.add("showgamestatistics.html", slurp("showgamestatistics.html"));
        ret.add("allgames.html", slurp("allgames.html"));
        return ret;
    }

    private static String slurp(String resourceName) {
        Reader r = new InputStreamReader(HttpServer.class.getResourceAsStream(resourceName));
        return Util.slurp(r);
    }

    private class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = null;
            int statusCode = HttpURLConnection.HTTP_OK;
            String path = t.getRequestURI().getPath();

            if ("/".equals(path)) {
                response = createOverviewResponse();
            } else if ("/showgame.html".equals(path)) {
                response = createGameResponse(t);
            } else if ("/showprettygame.html".equals(path)) {
            response = createPrettyGameResponse(t);
            } else if ("/showquestionnaire.html".equals(path)) {
                response = createQuestionnaireResponse(t);
            } else if ("/showgamestatistics.html".equals(path)) {
                response = createStatisticsResponse(t);
            } else if ("/allgames.html".equals(path)) {
                response = createAllGamesResponse(t);
            } else {
                // undefined URL
                response = "404 (not found)";
                statusCode = HttpURLConnection.HTTP_NOT_FOUND;
            }

            // send response
            t.sendResponseHeaders(statusCode, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private String createOverviewResponse() {
            Map<String, Object> bindings = new TreeMap<>();
            bindings.put("config", broker.getConfig());
            try {
                Result<GamesRecord> latestGames = broker.getJooq().selectFrom(Tables.GAMES)
                    .orderBy(Tables.GAMES.ID.desc())
                    .limit(20)
                    .fetch();

                bindings.put("latest", latestGames);
            } catch (Exception e) {
                var error = "Could not fetch latest games.  Is the DB schema up to date?\n"
                    + e.toString();
                logger.error(error);
                return error;
            }

            try {
                return engine.process("index.html", new MapBindings(bindings));
            } catch (CarrotException e) {
                return "An error occurred when expanding index.html: " + e.toString();
            }
        }

        private String createGameResponse(HttpExchange t) {
            String response = checkHttpQuery(t, "id");
            if (response == null) {
                Map<String,String> params = queryToMap(t.getRequestURI().getQuery());
                int gameid = Integer.parseInt(params.get("id"));
                GamesRecord game = broker.getJooq()
                    .selectFrom(Tables.GAMES)
                    .where(Tables.GAMES.ID.equal(gameid))
                    .fetchOne();

                Result<GameLogsRecord> gameLog = broker.getJooq()
                    .selectFrom(Tables.GAME_LOGS)
                    .where(Tables.GAME_LOGS.GAMEID.equal(gameid))
                    .orderBy(Tables.GAME_LOGS.ID.asc())
                    .fetch();

                // escape Textmessages for html
                for (GameLogsRecord entry: gameLog) {
                    if (entry.getMessageType().equals(TextMessage.class.getSimpleName())) {
                        JsonObject object = JsonParser.parseString(entry.getMessage()).getAsJsonObject();
                        if (! object.has("text")) {
                            continue;
                        }
                        String text = object.get("text").getAsString();
                        object.addProperty("text", StringEscapeUtils.escapeHtml4(text));
                        entry.setMessage(object.toString());
                    }

                }

                Map<String, Object> bindings = new TreeMap<>();
                bindings.put("config", broker.getConfig());
                bindings.put("game", game);
                bindings.put("log", gameLog);

                try {
                    response = engine.process("showgame.html", new MapBindings(bindings));
                } catch (CarrotException e) {
                    response = "An error occurred when expanding showgame.html: "
                        + e.toString();
                }
            }
            return response;
        }

        public static class PrettyMessage {
            private String timestamp, message, color, link;

            public PrettyMessage(String timestamp, String message, String color, String link) {
                this.timestamp = timestamp;
                this.message = message;
                this.color = color;
                this.link = link;
            }

            public String getLink() {
                return link;
            }

            public String getTimestamp() {
                return timestamp;
            }

            public void setTimestamp(String timestamp) {
                this.timestamp = timestamp;
            }

            public String getMessage() {
                return message;
            }

            public void setMessage(String message) {
                this.message = message;
            }

            public String getColor() {
                return color;
            }

            public void setColor(String color) {
                this.color = color;
            }
        }

        private String createPrettyGameResponse(HttpExchange t) {
            String response = checkHttpQuery(t, "id");

            if (response == null) {
                Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
                int gameid = Integer.parseInt(params.get("id"));
                GamesRecord game = broker.getJooq()
                        .selectFrom(Tables.GAMES)
                        .where(Tables.GAMES.ID.equal(gameid))
                        .fetchOne();

                Result<GameLogsRecord> gameLog = broker.getJooq()
                        .selectFrom(Tables.GAME_LOGS)
                        .where(Tables.GAME_LOGS.GAMEID.equal(gameid))
                        .orderBy(Tables.GAME_LOGS.ID.asc())
                        .fetch();

                List<PrettyMessage> prettyMessages = new ArrayList<>();
                LocalDateTime startTime = null;
                int numDestroyed = 0;
                int numMistakes = 0;
                long millisecondsSinceStart = -1;
                String linkBase = "/showgame.html?id=" + gameid + "#";

                for (GameLogsRecord entry : gameLog) {
                    try {
                        JsonObject object = JsonParser.parseString(entry.getMessage()).getAsJsonObject();

                        if (startTime == null) {
                            startTime = entry.getTimestamp();
                        }

                        millisecondsSinceStart = startTime.until(entry.getTimestamp(), ChronoUnit.MILLIS);
                        String timestamp = String.format("%02d:%02d.%03d", millisecondsSinceStart / 60 / 1000, (millisecondsSinceStart / 1000) % 60, millisecondsSinceStart % 1000);
                        String link = linkBase + entry.getId();

                        if (entry.getMessageType().equals(TextMessage.class.getSimpleName())) {
                            String text = object.get("text").getAsString();

                            if (!text.startsWith("|")) {
                                if (text.startsWith("{")) {
                                    object = JsonParser.parseString(text).getAsJsonObject();
                                    text = object.get("message").getAsString();
                                }

                                if( text.contains("Not there! please remove that block again")) {
                                    numMistakes++;
                                }

                                StringEscapeUtils.escapeHtml4(text);
                                if( !prettyMessages.isEmpty() && prettyMessages.get(prettyMessages.size()-1).getColor().equals("green")) {
                                    prettyMessages.add(new PrettyMessage("", "", "black", link));
                                }

                                prettyMessages.add(new PrettyMessage(timestamp, text, "black", link));
                            }
                        } else if (entry.getMessageType().equals(BlockPlacedMessage.class.getSimpleName())) {
                            String text = String.format("block placed at %d,%d,%d", object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
                            prettyMessages.add(new PrettyMessage(timestamp, text, "red", link));
                        } else if (entry.getMessageType().equals(ProtectBlockMessage.class.getSimpleName())) {
                            // recolor correct block-placed messages to green
                            prettyMessages.get(prettyMessages.size()-1).setColor("green");
                        } else if (entry.getMessageType().equals(BlockDestroyedMessage.class.getSimpleName())) {
                            String text = String.format("block destroyed at %d,%d,%d", object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
                            prettyMessages.add(new PrettyMessage(timestamp, text, "orange", link));
                            numDestroyed++;
                        }

                    } catch(IllegalStateException|com.google.gson.JsonSyntaxException e) {
                        // JSON parsing errors, ignore these
                    }
                }

                Map<String, Object> bindings = new TreeMap<>();
                bindings.put("config", broker.getConfig());
                bindings.put("game", game);
                bindings.put("messages", prettyMessages);
                bindings.put("numDestroyed", numDestroyed);
                bindings.put("numMistakes", numMistakes);
                bindings.put("gameDuration", String.format("%02d:%02d.%03d (%d seconds)", millisecondsSinceStart / 60 / 1000, (millisecondsSinceStart / 1000) % 60, millisecondsSinceStart % 1000, millisecondsSinceStart/1000));

                try {
                    response = engine.process("showprettygame.html", new MapBindings(bindings));
                } catch (CarrotException e) {
                    response = "An error occurred when expanding showprettygame.html: "
                            + e.toString();
                }
            }

            return response;
        }

        private String createQuestionnaireResponse(HttpExchange t) {
            String response = checkHttpQuery(t, "id");
            if (response == null) {
                Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
                int gameid = Integer.parseInt(params.get("id"));
                GamesRecord game = broker.getJooq()
                    .selectFrom(Tables.GAMES)
                    .where(Tables.GAMES.ID.equal(gameid))
                    .fetchOne();

                Result<QuestionnairesRecord> questionnaire = broker.getJooq()
                    .selectFrom(Tables.QUESTIONNAIRES)
                    .where(Tables.QUESTIONNAIRES.GAMEID.equal(gameid))
                    .orderBy(Tables.QUESTIONNAIRES.ID.asc())
                    .fetch();

                // escape for html
                for (QuestionnairesRecord row: questionnaire) {
                    row.setQuestion(StringEscapeUtils.escapeHtml4(row.getQuestion()));
                    row.setAnswer(StringEscapeUtils.escapeHtml4(row.getAnswer()));
                }
                Map<String, Object> bindings = new TreeMap<>();
                bindings.put("config", broker.getConfig());
                bindings.put("game", game);
                bindings.put("questionnaire", questionnaire);
                try {
                    response = engine.process("showquestionnaire.html",
                        new MapBindings(bindings));
                } catch (CarrotException e) {
                    response = "An error occurred when expanding showquestionnaire.html: "
                        + e.toString();
                }
            }
            return response;
        }

        public String createStatisticsResponse(HttpExchange t) {
            String response = checkHttpQuery(t, "id");
            if (response == null) {
                Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
                int gameId = Integer.parseInt(params.get("id"));

                GamesRecord game = broker.getJooq()
                    .selectFrom(Tables.GAMES)
                    .where(Tables.GAMES.ID.equal(gameId))
                    .fetchOne();

                GameInformation info = new GameInformation(gameId, broker.getJooq());

                Map<String, Object> bindings = new TreeMap<>();
                bindings.put("config", broker.getConfig());
                bindings.put("game", game);
                bindings.put("info", info);
                try {
                    response = engine.process("showgamestatistics.html",
                        new MapBindings(bindings));
                } catch (CarrotException e) {
                    response = "An error occurred when expanding showgamestatistics.html: "
                        + e.toString();
                }
            }
            return response;

        }

        private String createAllGamesResponse(HttpExchange t) {
            Map<String, Object> bindings = new TreeMap<>();
            try {
                Result<GamesRecord> allGames = broker.getJooq().selectFrom(Tables.GAMES)
                    .orderBy(Tables.GAMES.ID.desc())
                    .fetch();
                bindings.put("games", allGames);
            } catch (Exception e) {
                var error = "Could not fetch games.  Is the DB schema up to date?\n"
                    + e.toString();
                logger.error(error);
                return error;
            }

            try {
                return engine.process("allgames.html", new MapBindings(bindings));
            } catch (CarrotException e) {
                return "An error occurred when expanding allgames.html: " + e.toString();
            }
        }

        private String checkHttpQuery(HttpExchange t, String key) {
            String response;
            if (t.getRequestURI().getQuery() == null) {
                response = "No game ID specified in HTTP query.";
            } else {
                Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
                if (!params.containsKey(key)) {
                    response = "No game ID specified in HTTP query.";
                } else {
                    response = null;
                }
            }
            return response;
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
