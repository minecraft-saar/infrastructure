package de.saar.minecraft.architect;

import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.Void;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ArchitectServer {
  private Server server;
  private Map<Integer,Architect> runningArchitects;

  public ArchitectServer(){
    runningArchitects = new HashMap<>();
  }

  private void start() throws IOException {
    int port = 10000;

    server = ServerBuilder.forPort(port)
        .addService(new ArchitectImpl())
        .build()
        .start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        ArchitectServer.this.stop();
        System.err.println("*** server shut down");
      }
    });

    System.err.println("Architect server running.");
  }

  private void stop() {
    if( server != null ) {
      server.shutdown();
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  private class ArchitectImpl extends ArchitectGrpc.ArchitectImplBase {
    /**
     * Creates a new architect instance for the new game.
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void startGame(GameDataWithId request, StreamObserver<Void> responseObserver) {
      System.err.println("a");
      Architect arch = new DummyArchitect();
      runningArchitects.put(request.getId(), arch);
      responseObserver.onNext(Void.newBuilder().build());
      responseObserver.onCompleted();

      System.err.printf("architect for id %d: %s\n", request.getId(), arch);
    }

    /**
     * Delegates the status message to the architect for the given game ID.
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void handleStatusInformation(StatusMessage request, StreamObserver<TextMessage> responseObserver) {
      Architect arch = runningArchitects.get(request.getGameId());
      System.err.printf("retrieved architect for id %d: %s\n", request.getGameId(), arch);
      arch.handleStatusInformation(request, responseObserver);
    }
  }

  private static class DummyArchitect implements Architect {

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
  }


  public static void main(String[] args) throws IOException, InterruptedException {
    ArchitectServer server = new ArchitectServer();
    server.start();
    server.blockUntilShutdown();
  }
}
