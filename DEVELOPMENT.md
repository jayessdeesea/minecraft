# Development

How to get from a fresh clone to a running squad. What the project is and why it's shaped this
way: [AGENTS.md](AGENTS.md). What's planned: [backlog/](backlog/README.md). Past changes: `git log`.

## Prerequisites

- Java 25 (Amazon Corretto or another distribution) - matches [AGENTS.md](AGENTS.md#stack)
- Maven
- Docker Desktop (WSL2 backend) - runs the dev server and the local model. For GPU inference, an
  NVIDIA driver recent enough for Docker's GPU passthrough.
- An Anthropic API key, only if `command-ai` is pointed at Claude instead of the local model

## 1. Start the dev server and local model

`compose.yaml` runs the two services this project talks to. Our own app runs on the host, for a
fast edit/build/debug loop, and reaches both on `localhost`.

```bash
docker compose up -d --build                           # first run builds the server image
docker compose exec ollama ollama pull qwen3:14b       # once - ~9 GB into the ollama-models volume
```

- **`minecraft`** - `docker/minecraft-server/Dockerfile` downloads the pinned version's server jar
  from Mojang's own manifest URL and verifies its SHA-1. The pin is set in [AGENTS.md § Target
  environment](AGENTS.md#target-environment); bump the Dockerfile's `ARG`s deliberately along with
  it. The first start accepts the EULA and writes `online-mode=false`. The world lives in the
  `minecraft-data` volume. Listens on `localhost:25565`.
- **`ollama`** - pinned `ollama/ollama` image, models in the `ollama-models` volume, GPU passed
  through. Listens on `localhost:11434`. Confirm it found the GPU with
  `docker compose logs ollama | grep "inference compute"`.

Day to day:

```bash
docker compose logs -f minecraft                        # join/leave/death lines, live
docker compose exec minecraft console "time set night"  # any server console command
docker compose exec minecraft console "summon zombie 0 80 0"
docker compose down                                     # stop; volumes (world, models) are kept
```

`console` writes to a FIFO that the server reads as its stdin, so commands work
non-interactively. Forcing night or summoning a mob makes a combat check immediate instead of
waiting for night. In Git Bash, prefix any command that passes container paths with
`MSYS_NO_PATHCONV=1`, or the paths get rewritten to Windows ones.

**Choosing the model.** `qwen3:14b` with thinking off is the default (`app/src/main/resources/application.yml`).
Each decision takes 1-2.5 s on a 16 GB GPU; with thinking on it takes ~20 s. Larger local models
(`gpt-oss:20b`, `qwen3:30b-a3b`) chose no better on the squad leader's prompt. To use Claude
instead, set `spring.ai.model.chat: anthropic` and `ANTHROPIC_API_KEY`.

## 2. Build and test

```bash
mvn verify                      # every module's unit tests and the ArchUnit boundary rules
mvn package -DskipTests         # just the bootable app/target/app.jar
```

Neither needs the server or the model. Tests that need a live server or model are named `*IT` and
run only when asked for (section 5 and below).

**Scenario sweeps.** `bot`'s `SoldierScenarioTest` sends a soldier across generated terrain (flat,
hills, forest, lake, ravine, walled compound), 20 seeds per family by default. Each case is named
`<family> seed=<n>`, and a failure prints a top-down map of the world and the soldier's trail.

```bash
mvn -pl bot test -Dtest=SoldierScenarioTest -Dscenario.seeds=200      # a wider sweep
mvn -pl bot test -Dtest=SoldierScenarioTest -Dscenario.only=forest:7  # replay one case
```

**Combat sweep.** `bot`'s `CombatScenarioTest` pits a soldier against one simulated zombie,
skeleton or creeper, and a squad against a pack (`Battle`: rough approximations, not vanilla mob
AI), and prints how the fights end. Use it to measure a change to combat behaviour: `-Dcombat.seeds=200` for more.

**Squad-leader evaluation.** `app`'s `SquadLeaderScenarioIT` runs the real squad leader, with its
real prompt, against Ollama on the same scenarios. It prints a pass rate per family rather than
pass/fail. Re-run it after any prompt or model change:

```bash
mvn -q install -DskipTests
mvn -pl app test -Dtest=SquadLeaderScenarioIT -Dsurefire.failIfNoSpecifiedTests=false -Deval.seeds=5 [-Deval.model=...]
```

## 3. Run the chain of command

```bash
docker compose up -d
mvn package -DskipTests
java -jar app/target/app.jar
```

This connects `Alice`, `Bob` and `Carol` (`SoldierRunner.SOLDIERS`; the first is the point man) as
squad Alpha, under an LLM squad leader and general (`ChainOfCommand`). The log reads as a radio
net: `[General -> Alpha] OPORD`, `[Alpha -> squad] FRAGO`, `[Alpha -> General] CANTCO`, and a squad
SITREP every 5 seconds. Orders and CANTCOs also appear in game chat (section 4), spoken by
the point man as the squad's radio operator. Soldiers reconnect on their own if a connection dies. The general's mission
starts as `minecraft.general.mission` in `application.yml`.

**Giving orders from game chat.** A player named in `minecraft.operators` (comma-separated, empty
by default) can replace the mission by typing `!mission <text>` in plain chat. The general answers
WILCO on the net and re-plans at once. Lines from anyone else are logged and ignored. Offline mode
doesn't authenticate names, so the list guards against accidents, not impostors. Give missions in
absolute coordinates ("hold x=-40 z=0"): the general re-reads the mission at every re-plan, so
"40 blocks west of here" moves the objective another 40 blocks each time.

`!escort` attaches the squad to you as a protective detail, and `!escort <player>` to another
player: the soldiers ring the player and go after hostiles that come near them
(docs/military-doctrine.md § 10). `!mission <text>` ends it.

```bash
java -jar app/target/app.jar --minecraft.operators=YourName
```

**Setting up a supply point.** Place a chest, stock it with weapons and armour, and start the app
with `--minecraft.supply.point=x,y,z` (the chest's block). Soldiers draw from it when the general
sends the squad there, or when they pass within reach. Stocking from the console, with a player
nearby so the chunk is loaded:

```bash
docker compose exec minecraft console "setblock 6 86 6 chest"
docker compose exec minecraft console "item replace block 6 86 6 container.0 with iron_sword"
```

Stop it with Ctrl-C. **Don't kill stray `app.jar` or test JVMs with a bare
`Get-Process java | Stop-Process`**: that kills every `java.exe` on the host. Filter by command line:
`Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*app.jar*' }`.

## 4. Watch with the real Minecraft client

Multiplayer → Add Server → `localhost:25565`. The client must be on the server's version. Offline
mode means any username works, with no Microsoft login. It's also the quickest check that the
server is up, independent of this repo.

## 5. Live protocol checks

Each of these needs the dev server running:

| Command | What it proves |
|---|---|
| `mvn -pl protocol test -Dtest=StatusPingLiveIT -Dmc.logLabel=status-ping` | Handshake → Status → Ping: prints the server's status JSON and the round-trip time |
| `mvn -pl protocol test -Dtest=JoinObservationLiveIT -Dmc.logLabel=join-observation` | The full join through Play as `ScoutBot`: logs each kind of unrecognised packet once, then disconnects after a short window. Use it to catalogue packets we don't decode yet |
| `mvn -pl protocol test -Dtest=PersistentJoinLiveIT -Dmc.logLabel=persistent-join` | `MinecraftClient.join`, the entry point soldiers use: stays connected through Keep Alive, receives chunks, decodes and reports the spawn position, and hears another client's chat |
| `mvn -pl app test -Dtest=TerrainProbeIT -Dsurefire.failIfNoSpecifiedTests=false -Dprobe=x,y,z` | Diagnoses a stuck soldier: prints how our terrain model classifies the blocks around a spot (solid, hazard, open) slice by slice, and the region reachable from it |

Logs from IT runs go to the console and to `{logPath}/yyyy/MM/dd/yyyy-MM-dd-HH-{label}.txt`
(`protocol/src/test/resources/log4j2.xml`). `logPath` defaults to `logs/` under the module and can
be overridden with `-Dmc.logPath=...`. Give each run its own `-Dmc.logLabel` so runs within the
same hour don't share a file.

## 6. Confirming wire formats against the real server

The pinned server jar ships with readable `net.minecraft.*` class and method names, so the JDK's
own `javap` shows a packet's real field layout. The sequence of `FriendlyByteBuf.readX`/`writeX`
calls in its wire constructor *is* the format. Reach for this before probing live or trusting a
wiki for anything with internal structure:

```bash
# once per pin bump: copy the versioned jar out of the dev-server container
docker compose cp minecraft:/data/versions/26.3/server-26.3.jar C:/mc-reference/26.3/
J='C:\mc-reference\26.3\server-26.3.jar'
javap -p -c -classpath "$J" net.minecraft.network.protocol.game.ClientboundAddEntityPacket
javap -p -c -classpath "$J" net.minecraft.network.protocol.game.GameProtocols   # registration order = packet IDs
javap -v ...   # when a codec is a method reference: the BootstrapMethods/MethodHandle lines name its target
```

For a pin bump, run the same `javap` over both versions' jars and compare: packet IDs move
whenever a packet is inserted, and a codec rewritten as a composite can keep or change its wire
format, so read the composite's parts. The data generator's `packets.json` report (section 7)
lists every packet ID, a cross-check on the registration order.

Point `javap` straight at the jar. Don't extract it on Windows: deep `net/minecraft/...` paths
exceed `MAX_PATH`, and `unzip` silently skips them.

## 7. Regenerating the generated registries

`bot`'s block classes, hostile-mob list and item classes are generated from the server itself:
its data reports, and, for block types, its own registry (the reports dropped block types in
26.3). Regenerate all three after every pin bump. Run the server's data generator in the version's
reference folder, with the bundler jar from the version manifest: it writes the reports and
unpacks the server and its libraries there.

```bash
V=26.3; P=777; M=C:/mc-reference/$V
(cd $M && java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports --output generated)

SCP="$(cygpath -w $M/versions/$V/server-$V.jar)"                      # the server and its libraries
for j in $(find $M/libraries -name '*.jar'); do SCP="$SCP;$(cygpath -w $j)"; done
java -cp "$SCP" tools/block-type-dumper/DumpBlockTypes.java > $M/generated/block-types.txt

R=bot/src/main/resources/io/github/jayessdeesea/minecraft/bot/world
CP="<jackson-databind>;<jackson-core>;<jackson-annotations>"   # jars from ~/.m2
java -cp "$CP" tools/block-class-generator/GenerateBlockClasses.java $M/generated/reports/blocks.json $M/generated/block-types.txt $R/block-classes-$P.txt
java -cp "$CP" tools/entity-hostility-generator/GenerateEntityHostility.java $M/generated/reports/registries.json $R/hostile-entity-types-$P.txt
java -cp "$CP" tools/item-class-generator/GenerateItemClasses.java $M/generated/reports/registries.json $R/item-classes-$P.txt
```

Delete the old version's files and update the resource names in `BlockClassRegistry`,
`HostileMobRegistry` and `ItemClassRegistry`. Test fixtures that use real IDs (items, entity types,
block states) need the new ones: map each old ID to its name in the old reports, then to the new
ID. In Git Bash, give `java` a Windows-style classpath (`cygpath -w`).
