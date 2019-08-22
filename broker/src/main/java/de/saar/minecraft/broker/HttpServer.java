package de.saar.minecraft.broker;

import au.com.codeka.carrot.CarrotEngine;
import au.com.codeka.carrot.CarrotException;
import au.com.codeka.carrot.Configuration;
import au.com.codeka.carrot.bindings.MapBindings;
import au.com.codeka.carrot.resource.FileResourceLocator;
import au.com.codeka.carrot.resource.MemoryResourceLocator;
import au.com.codeka.carrot.resource.ResourceLocator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.Games;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import org.jooq.Record;
import org.jooq.Result;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.*;

public class HttpServer {
    private CarrotEngine engine;
    private Broker broker;

    public void start(Broker broker) throws IOException {
        int port = broker.getConfig().getHttpPort();

        if( port == 0 ) {
            throw new RuntimeException("No HTTP port specified in config file.");
        }

        this.broker = broker;

        // start HTTP server
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();

        // set up templating engine
        engine = new CarrotEngine(new Configuration.Builder()
                .setResourceLocator(makeResourceLocator())
                .build());

        System.err.println("HTTP server running on port " + port + ".");
    }

    private ResourceLocator.Builder makeResourceLocator() {
        MemoryResourceLocator.Builder ret = new MemoryResourceLocator.Builder();

        ret.add("index.html", slurp("index.html"));
        ret.add("showgame.html", slurp("showgame.html"));

        return ret;
    }

    private static String slurp(String resourceName) {
        Reader r = new InputStreamReader(HttpServer.class.getResourceAsStream(resourceName));

        try {
            char[] arr = new char[8 * 1024];
            StringBuilder buffer = new StringBuilder();
            int numCharsRead;
            while ((numCharsRead = r.read(arr, 0, arr.length)) != -1) {
                buffer.append(arr, 0, numCharsRead);
            }
            r.close();

            return buffer.toString();
        } catch(IOException e) {
            return null;
        }
    }

    private class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = null;
            String path = t.getRequestURI().getPath();
            System.err.println("HTTP request: " + path);

            if( "/".equals(path)) {
                Map<String, Object> bindings = new TreeMap<>();
                bindings.put("config", broker.getConfig());

                Result<GamesRecord> latestGames = broker.getJooq().selectFrom(Tables.GAMES)
                        .orderBy(Tables.GAMES.ID.desc())
                        .limit(20)
                        .fetch();

                bindings.put("latest", latestGames);

                try {
                    response = engine.process("index.html", new MapBindings(bindings));
                } catch (CarrotException e) {
                    response = "An error occurred when expanding index.html: " + e.toString();
                }
            } else if( "/showgame.html".equals(path)) {
                if( t.getRequestURI().getQuery() == null ) {
                    response = "No game ID specified in HTTP query.";
                } else {
                    Map<String,String> params = queryToMap(t.getRequestURI().getQuery());
                    if( ! params.containsKey("id")) {
                        response = "No game ID specified in HTTP query.";
                    } else {
                        int gameid = Integer.parseInt(params.get("id"));
                        GamesRecord game = broker.getJooq().selectFrom(Tables.GAMES).where(Tables.GAMES.ID.equal(gameid)).fetchOne();

                        Result<GameLogsRecord> gameLog = broker.getJooq().selectFrom(Tables.GAME_LOGS)
                                .where(Tables.GAME_LOGS.GAMEID.equal(gameid))
                                .orderBy(Tables.GAME_LOGS.ID.asc())
                                .fetch();

                        Map<String, Object> bindings = new TreeMap<>();
                        bindings.put("config", broker.getConfig());
                        bindings.put("game", game);
                        bindings.put("log", gameLog);

                        try {
                            response = engine.process("showgame.html", new MapBindings(bindings));
                        } catch (CarrotException e) {
                            response = "An error occurred when expanding showgame.html: " + e.toString();
                        }
                    }
                }
            }

            // send response
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
