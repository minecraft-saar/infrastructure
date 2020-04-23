package de.saar.minecraft.broker;

import au.com.codeka.carrot.CarrotEngine;
import au.com.codeka.carrot.CarrotException;
import au.com.codeka.carrot.Configuration;
import au.com.codeka.carrot.bindings.MapBindings;
import au.com.codeka.carrot.resource.MemoryResourceLocator;
import au.com.codeka.carrot.resource.ResourceLocator;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import de.saar.minecraft.broker.db.tables.records.QuestionnairesRecord;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.util.Util;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.Result;

public class HttpServer {
    private static Logger logger = LogManager.getLogger(HttpServer.class);
    private CarrotEngine engine;
    private Broker broker;

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
        server.createContext("/", new MyHandler());
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
        ret.add("showquestionnaire.html", slurp("showquestionnaire.html"));

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
            } else if ("/showquestionnaire.html".equals(path)) {
                response = createQuestionnaireResponse(t);
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
            String response;
            if (t.getRequestURI().getQuery() == null) {
                response = "No game ID specified in HTTP query.";
            } else {
                Map<String,String> params = queryToMap(t.getRequestURI().getQuery());
                if (! params.containsKey("id")) {
                    response = "No game ID specified in HTTP query.";
                } else {
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
                            JsonObject object = Json.parse(entry.getMessage()).asObject();
                            String text = object.get("text").asString();
                            object.set("text", StringEscapeUtils.escapeHtml4(text));
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
            }
            return response;
        }

        private String createQuestionnaireResponse(HttpExchange t) {
            String response;
            if (t.getRequestURI().getQuery() == null) {
                response = "No game ID specified in HTTP query.";
            } else {
                Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
                if (!params.containsKey("id")) {
                    response = "No game ID specified in HTTP query.";
                } else {
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
