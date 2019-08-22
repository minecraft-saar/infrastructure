package de.saar.minecraft.architect;

import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.stub.StreamObserver;

public class DummyArchitect implements Architect {

    @Override
    public void handleStatusInformation(StatusMessage request, StreamObserver<TextMessage> responseObserver) {
        int x = request.getX();
        int gameId = request.getGameId();

        // spawn a thread for a long-running computation
        new Thread() {
            @Override
            public void run() {
                String text = "your x was " + x;
                TextMessage mText = TextMessage.newBuilder().setGameId(gameId).setText(text).build();

                // delay for a bit
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // send the text message back to the client
                responseObserver.onNext(mText);
                responseObserver.onCompleted();
            }
        }.start();
    }

    @Override
    public String getArchitectInformation() {
        return "DummyArchitect";
    }
}
