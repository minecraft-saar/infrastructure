package de.saar.minecraft.broker;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import de.saar.minecraft.broker.db.GameLogsDirection;
import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.TextMessageOrBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;

/**
 * A DelegatingStreamObserver acts as a proxy in connections from the Architect to the Client.
 * All messages are logged into the database and forwarded.
 */
class DelegatingStreamObserver implements StreamObserver<TextMessage> {
    
    private static final JsonFactory factory = new JsonFactory();

    /**
     * Extracts the value of the "message" field from the json string. Returns null if not found.
     * @throws IOException Passed up from Jackson.
     */
    private static String getMessage(String json) {
        try {
            var parser = factory.createParser(json);
            while (!parser.isClosed()) {
                var jsonToken = parser.nextToken();
                if (JsonToken.FIELD_NAME.equals(jsonToken)) {
                    if ("message".equals(parser.getCurrentName())) {
                        return parser.nextTextValue();
                    }
                }
            }
        } catch (IOException ignored) {
            // Not a great solution, but we just return null
            // and get a Nullpointer exception somewhere
            // I hope that this can just never happen.
        }
        return null;
    }
    
    private final StreamObserver<TextMessage> toClient;
    private final int gameId;
    private final Broker broker;

    public DelegatingStreamObserver(int gameId,
                                    StreamObserver<TextMessage> toClient,
                                    Broker broker) {
        this.toClient = toClient;
        this.gameId = gameId;
        this.broker = broker;
    }

    @Override
    public synchronized void onNext(TextMessage value) {
        if (value.getNewGameState() == NewGameState.SuccessfullyFinished) {
            broker.startQuestionnaire(gameId, this);
        }
        // Text message for logging purposes only, log and don't forward.
        if (value.getForLogging()) {
            broker.log(gameId, value.getText(), value.getLogType(),
                GameLogsDirection.LogFromArchitect);
            return;
        }
        broker.log(gameId, value, GameLogsDirection.PassToClient);
        String text = value.getText();
        if (text.startsWith("{")) {
            // assume that a json object is passed along, get the "message" part
            // and only forward that.
            value = TextMessage.newBuilder(value).setText(getMessage(text)).build();
        }
        toClient.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
        broker.log(gameId, t, GameLogsDirection.PassToClient);
        toClient.onError(t);
    }

    @Override
    public void onCompleted() {
        toClient.onCompleted();
    }
}
