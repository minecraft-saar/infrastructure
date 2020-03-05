# Minecraft NLG infrastructure

Infrastructure for connecting a Minecraft server to the NLG system.


### Start the Architect Server

```
./gradlew architect:run
```

(the shadow Jar is in architect/build/libs/architect-0.1.0-SNAPSHOT-all.jar)


### Start the broker

Make a copy of `example-broker-config.yaml` within the `broker` subdirectory, named `broker-config.yaml`, and edit it as needed. Then start the matchmaker as follows:

```
./gradlew broker:run
```

(the shadow Jar is in broker/build/libs/broker-0.1.0-SNAPSHOT-all.jar)

### Start the dummy client

The dummy client mimicks a Minecraft server and you can send messages
by hand from this dummy client.

```
./gradlew broker:shadowJar
cd broker
java -cp build/libs/broker-0.1.0-SNAPSHOT-all.jar de.saar.minecraft.broker.TestClient
```

Send messages as explained in the Javadoc of TestClient.

## Structure of the RPC interfaces

We use grpc fall all IPC.  First, the broker (br) connects to all
ArchitectServers (as) via `Hello`.  When a user connects to the
Minecraft server (ms), it sends a message to the broker:

`ms -> br startGame(IPAddress, PlayerName): (Scenario, GameId)`

The broker selects a scenario, creates a new ID for this game and
connects to an ArchitectServer:

`  br -> as StartGame(Scenario, GameId): None`

Upon completion, the (still running) `startGame` method call returns
`(Scenario, GameId)`.

The Client then obtains the message channel via
`ms -> br GetMessageChannel (GameId): stream (TextMessage)`.  The broker
calls the same method on the ArchitectServer
`br -> as GetMessageChannel (GameId): stream (TextMessage)`
and returns that message channel to the minecraft server.
the ArchitectServer uses that channel to push instructions
to the user.

(The reason for having this in a separate call and not part of
`StartGame` is that grpc cannot return several values for a single
call.)

Now everything is set up.  `ms` now calls `HandleStatusInformation`,
`HandleBlockPlaced` and `HandleBlockDestroyed` whenever it is
applicable.  The broker forwards these calls by calling the same
methods on the ArchitectServer.

The architect is the component responsible for deciding when a game
changes state (e.g. the task was succsessfully completed).  This is
indicated by a flag in `TextMessage`.  The broker notices this and
takes appropriate action such as starting the post-game questionnaire.

When a player disconnects, the Minecraft server calls 
`ms -> br EndGame (GameId)`
which is again forwarded to the server:
`br -> as EndGame (GameId)`
The architect server shuts down the corresponding architect (in
our implementation at least) and the broker also clears that
game from its data structures and logs this event to the database.

The last method is `EndAllGames()` from the ArchitectServer.  It
terminates all games; this method is meant for an orderly shutdown
initiated by the broker.

