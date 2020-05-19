# Minecraft Instruction giving infrastructure

This repository contains components to perform instruction giving
experiments in Minecraft.  Read about the overall software structure
[here](https://minecraft-saar.github.io/mc-saar-instruct) or in our
paper (forthcoming).

All software in this repository is bundled as a single gradle project
with subprojects.  The `architect` subproject contains base classes
for architects, implementing all shared behaviour such as connection
handling and instruction sending.  It also contains the
`ArchitectServer` class, which handles instantiating new architects on
incoming game requests et cetera.  Lastly, it contains a dummy
architect for testing purposes, which only “instructs” by sending the
current user’s position to the user from time to time.

The `networking` subproject builds all libraries needed for – as you
might have guessed – networking.  These libraries are used by all
components of the experiment system.  We use grpc.

The `broker` subproject contains the central piece of the
infrastructure.  The broker manages all connections and logs
everything into a database.

The `integration` subproject runs (dummies of) all components to test
whether the overall system works as expected.


## Start the broker

Make a copy of `example-broker-config.yaml` within the `broker`
subdirectory, named `broker-config.yaml`, and edit it as needed.

The broker uses a database.  Edit the database entry of the
configuration to your needs.  If you remove that file, the broker will
use a non-persistent in-memory h2 database.  The example configuration
assumes a mariadb or mysql database running on localhost.  With a
default installation of mariadb, you can set up the database like this:

 - run `mariadb` as root
 - Create a new user: `CREATE USER 'minecraft'@'localhost';`
 - Grant that user priviliges in the MINECRAFT database:
   `GRANT ALL PRIVILEGES  ON MINECRAFT.* TO 'minecraft'@'localhost'`
   
The broker will automatically set up the database and update the
schema if you update the broker.  ￼

You start the broker as follows:

```
./gradlew broker:run
```

You can also create a jar for the broker with `./gradlew
broker:shadowJar` and run the broker/build/libs/broker-*-all.jar.

## Start the dummy Architect Server

```
./gradlew architect:run
```

(the shadow Jar is in architect/build/libs/architect-0.1.0-SNAPSHOT-all.jar)

`./gradlew architect:run` starts the architect with the default arguments:
- waitTime = 1000 (how long to wait between instructions)
- endAfterFirstBlock = false (only send a single instruction and then end)
- responseFrequency = 1  (architect gives feedback after every nth status update)

Different values can be specified when starting the architect, 
e.g. `./gradlew architect:run --args="100 true 50"`


## Start the dummy client

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

