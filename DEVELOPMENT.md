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
is `minecraft.general.mission` in `application.yml`.

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
| `mvn -pl protocol test -Dtest=PersistentJoinLiveIT -Dmc.logLabel=persistent-join` | `MinecraftClient.join`, the entry point soldiers use: stays connected through Keep Alive, receives chunks, decodes and reports the spawn position |

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
docker compose cp minecraft:/data/versions/26.2/server-26.2.jar C:/mc-reference/
J='C:\mc-reference\server-26.2.jar'
javap -p -c -classpath "$J" net.minecraft.network.protocol.game.ClientboundAddEntityPacket
javap -p -c -classpath "$J" net.minecraft.network.protocol.game.GameProtocols   # registration order = packet IDs
javap -v ...   # when a codec is a method reference: the BootstrapMethods/MethodHandle lines name its target
```

Point `javap` straight at the jar. Don't extract it on Windows: deep `net/minecraft/...` paths
exceed `MAX_PATH`, and `unzip` silently skips them.

## 7. Regenerating the generated registries

`bot`'s block classes and hostile-mob list are generated from the server's own data reports.
Regenerate both after every pin bump:

```bash
MSYS_NO_PATHCONV=1 docker compose run --rm --no-deps --entrypoint java minecraft \
    -DbundlerMainClass=net.minecraft.data.Main -jar /opt/minecraft/server.jar --reports --output /data/generated
docker compose cp minecraft:/data/generated/reports C:/mc-reference/reports

R=bot/src/main/resources/io/github/jayessdeesea/minecraft/bot/world
CP="<jackson-databind>;<jackson-core>;<jackson-annotations>"   # jars from ~/.m2
java -cp "$CP" tools/block-class-generator/GenerateBlockClasses.java C:/mc-reference/reports/blocks.json $R/block-classes-776.txt
java -cp "$CP" tools/entity-hostility-generator/GenerateEntityHostility.java C:/mc-reference/reports/registries.json $R/hostile-entity-types-776.txt
```

Rename the output files to match the new protocol number, along with the resource names in
`BlockClassRegistry` and `HostileMobRegistry`.
