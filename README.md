# Minecraft NLG infrastructure

Infrastructure for connecting a Minecraft server to the NLG system.

### Compilation

```
gradle shadowJar
```


### Start the Architect Server

```
java -cp build/libs/minecraft-all.jar de.saar.minecraft.architect.ArchitectServer
```

### Start the Matchmaker

```
java -jar build/libs/minecraft-all.jar 
```


### Start the dummy client

```
java -cp build/libs/minecraft-all.jar de.saar.minecrt.matchmaker.MatchmakerTestClient
```

Send messages as explained in the Javadoc of MatchmakerTestClient.
