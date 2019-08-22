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

Make a copy of `example-broker-config.yaml` within the `broker` subdirectory, named `broker-config.yaml`, and edit it as needed. Then start the matchmaker as follows:

```
./gradlew broker:run
```

(the shadow Jar is in broker/build/libs/broker-0.1.0-SNAPSHOT-all.jar)

### Start the dummy client

```
./gradlew broker:shadowJar
cd broker
java -cp build/libs/broker-0.1.0-SNAPSHOT-all.jar de.saar.minecraft.broker.TestClient
```

Send messages as explained in the Javadoc of TestClient.
