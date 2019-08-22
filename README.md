# Minecraft NLG infrastructure

Infrastructure for connecting a Minecraft server to the NLG system.

### Compilation

```
./gradlew build
```


### Start the Architect Server

```
./gradlew architect:run
```

(the shadow Jar is in architect/build/libs/architect-0.1.0-SNAPSHOT-all.jar)


### Start the Matchmaker

Make a copy of `example-matchmaker-config.yaml` within the `broker` subdirectory, named `matchmaker-config.yaml`, and edit it as needed. Then start the matchmaker as follows:

```
./gradlew broker:run
```

(the shadow Jar is in broker/build/libs/broker-0.1.0-SNAPSHOT-all.jar)

### Start the dummy client

```
cd broker
java -cp build/libs/broker-0.1.0-SNAPSHOT-all.jar de.saar.minecraft.matchmaker.MatchmakerTestClient
```

Send messages as explained in the Javadoc of MatchmakerTestClient.
