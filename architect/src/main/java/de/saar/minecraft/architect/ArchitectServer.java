package de.saar.minecraft.architect;

import com.google.rpc.Code;
import com.google.rpc.Status;
import de.saar.minecraft.shared.BlockDestroyedMessage;
import de.saar.minecraft.shared.BlockPlacedMessage;
import de.saar.minecraft.shared.GameId;
import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import de.saar.minecraft.shared.None;
import de.saar.minecraft.shared.WorldSelectMessage;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A server which provides access to Architects of one type.
 * This server responds to gRPC requests from the Broker, creates
 * and destroys Architect instances as needed, and routes further
 * requests from one player on the Minecraft server to their
 * corresponding Architect instance.
 *
 */
public class ArchitectServer {
    private static Logger logger = LogManager.getLogger(ArchitectServer.class);
    private Server server;
    private Map<Integer, Architect> runningArchitects;
    private ArchitectFactory factory;
    private int port;

    /**
     * Constructs an ArchitectServer which is configured to listen to a given port and
     * uses the given ArchitectFactory to spawn new architects for each connecting client.
     */
    public ArchitectServer(int port, ArchitectFactory factory) {
        this.factory = factory;
        this.port = port;
        runningArchitects = new HashMap<>();
    }

    /**
     * Actually starts the ArchitectServer.
     */
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new ArchitectImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                ArchitectServer.this.stop();
            }
        });

        String info = factory.build().getArchitectInformation();

        logger.info("Architect server running on port {}.", port);
        logger.info(info);
    }

    /**
     * Stops the grpc service if it is running.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private class ArchitectImpl extends ArchitectGrpc.ArchitectImplBase {
        /**
         * This method is called once when the Broker connects to the Architect server
         * for the first time. This is to make sure the Architect server is
         * actually available. The method returns an information string about
         * the type of architect (including perhaps its version) that is served
         * by this ArchitectServer.
         */
        @Override
        public void hello(None request, StreamObserver<ArchitectInformation> responseObserver) {
            Architect arch = factory.build();
            // no need to initialize it, it will disappear right away

            responseObserver.onNext(
                ArchitectInformation
                    .newBuilder()
                    .setInfo(arch.getArchitectInformation())
                    .build()
            );
            responseObserver.onCompleted();
        }

        /**
         * Creates a new architect instance for the new game.
         */
        @Override
        public void startGame(WorldSelectMessage request, StreamObserver<None> responseObserver) {
            Architect arch = factory.build();
            arch.initialize(request);
            runningArchitects.put(request.getGameId(), arch);

            responseObserver.onNext(None.newBuilder().build());
            responseObserver.onCompleted();

            logger.info("architect for id {}: {}", request.getGameId(), arch);
        }

        @Override
        public void getMessageChannel(GameId request,
            StreamObserver<TextMessage> responseObserver) {
            var architect = runningArchitects.get(request.getId());
            if (architect == null) {
                Status status = Status.newBuilder()
                    .setCode(Code.INVALID_ARGUMENT.getNumber())
                    .setMessage("No architect running for game ID " + request.getId())
                    .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
                return;
            }

            architect.setMessageChannel(responseObserver);
        }

        /**
         * Marks the given game as finished and shuts down its corresponding
         * architect instance.
         */
        @Override
        public void endGame(GameId request, StreamObserver<None> responseObserver) {
            var architect = runningArchitects.get(request.getId());
            if (architect != null) {
                architect.shutdown();
            } else {
                responseObserver.onError(new RuntimeException("Incorrect ID"));
                return;
            }
            runningArchitects.remove(request.getId());
            logger.info("architect for id {} finished", request.getId());
            responseObserver.onNext(None.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void endAllGames(None request, StreamObserver<None> responseObserver) {
            for (var a: runningArchitects.values()) {
                a.shutdown();
            }
            runningArchitects.clear();
            responseObserver.onNext(None.getDefaultInstance());
            responseObserver.onCompleted();
        }

        /**
         * Delegates the status message to the architect for the given game ID.
         */
        @Override
        public void handleStatusInformation(StatusMessage request,
                                            StreamObserver<None> responseObserver) {
            Architect arch = runningArchitects.get(request.getGameId());

            if (arch == null) {
                Status status = Status.newBuilder()
                        .setCode(Code.INVALID_ARGUMENT.getNumber())
                        .setMessage("No architect running for game ID " + request.getGameId())
                        .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
            } else {
                arch.handleStatusInformation(request);
            }
        }

        /**
         * Delegates the block placed message to the architect for the given game ID.
         */
        @Override
        public void handleBlockPlaced(BlockPlacedMessage request,
                                      StreamObserver<None> responseObserver) {
            Architect arch = runningArchitects.get(request.getGameId());

            if (arch == null) {
                Status status = Status.newBuilder()
                    .setCode(Code.INVALID_ARGUMENT.getNumber())
                    .setMessage("No architect running for game ID " + request.getGameId())
                    .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
            } else {
                arch.handleBlockPlaced(request);
            }
        }

        /**
         * Delegates the block destroyed message to the architect for the given game ID.
         */
        @Override
        public void handleBlockDestroyed(BlockDestroyedMessage request,
                                         StreamObserver<None> responseObserver) {
            Architect arch = runningArchitects.get(request.getGameId());

            if (arch == null) {
                Status status = Status.newBuilder()
                    .setCode(Code.INVALID_ARGUMENT.getNumber())
                    .setMessage("No architect running for game ID " + request.getGameId())
                    .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
            } else {
                arch.handleBlockDestroyed(request);
            }
        }
    }


    /**
     * Starts an architect server.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        ArchitectFactory factory;
        if (args.length == 2) {
            int waitTime = Integer.parseInt(args[0]);
            boolean endAfterFirstBlock = Boolean.parseBoolean(args[1]);
            logger.info("waitTime: {}", waitTime);
            logger.info("endAfterFirstBlock: {}", endAfterFirstBlock);
            factory = () -> new DummyArchitect(waitTime, endAfterFirstBlock);
        } else {
            factory = DummyArchitect::new;
        }
        ArchitectServer server = new ArchitectServer(10000, factory);
        server.start();
        server.blockUntilShutdown();
    }
}
