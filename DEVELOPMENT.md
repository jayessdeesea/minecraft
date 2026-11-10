# Development

How to get from a fresh clone to a running squad. What the project is and why it's shaped this
way: [README.md](README.md) and [docs/architecture.md](docs/architecture.md). What's planned: [backlog/](backlog/README.md). Past changes: `git log`.

## Prerequisites

- Java 25 (Amazon Corretto or another distribution) - matches [docs/stack.md](docs/stack.md#stack)
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
  from Mojang's own manifest URL and verifies its SHA-1. The pin is set in [docs/stack.md § Target
  environment](docs/stack.md#target-environment); bump the Dockerfile's `ARG`s deliberately along with
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
non-interactively. It returns nothing and exits 0 whatever the server makes of the command: the
reply is only in the server's log (`docker compose logs minecraft`, or `logs/latest.log` in the
container). `ChainScenarioLiveIT` reads it there and fails on a command the server rejects. Forcing night or summoning a mob makes a combat check immediate instead of
waiting for night. In Git Bash, prefix any command that passes container paths with
`MSYS_NO_PATHCONV=1`, or the paths get rewritten to Windows ones.

**Choosing the model.** Each echelon picks its own provider in `app/src/main/resources/application.yml`:
`minecraft.general.chat-model` and `minecraft.squad-leader.chat-model`, each `ollama` or `anthropic`.
Both default to `ollama` (`qwen3:14b`, thinking off), so nothing needs an API key. To put the general
on a frontier model, as the project intends, set `ANTHROPIC_API_KEY` and run with
`--minecraft.general.chat-model=anthropic`. The startup log says which each echelon got
(`The general thinks with anthropic`); a name with no provider behind it stops startup.
On a 16 GB GPU, `qwen3:14b` takes 1-2.5 s per decision; with thinking on it takes ~20 s. Larger local
models (`gpt-oss:20b`, `qwen3:30b-a3b`) chose no better on the squad leader's prompt.

## 2. Build and test

```bash
mvn verify                      # every module's unit tests and the ArchUnit boundary rules
mvn package -DskipTests         # just the bootable app/target/app.jar
```

Neither needs the server or the model. Tests that need a live server or model are named `*IT` and
run only when asked for (section 5 and below).

**Scenario sweeps.** `bot`'s `SoldierScenarioTest` sends a soldier across generated terrain (flat,
hills, forest, lake, ravine, walled compound, cliff), 20 seeds per family by default, each run on
foot and again carrying cobblestone to climb with. Each case is named
`<family> seed=<n>` (the climbing run adds `climbing`), and a failure prints a top-down map of the world and the soldier's trail.

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

**General evaluation.** `app`'s `GeneralScenarioIT` does the same for the general: fixed
situations, each asking one policy question (build before dark, hold a sealed base, leave it when
the mission says so), at a few seconds a decision. Try a change to the general's prompt or context
here before spending a whole-chain run on it:

```bash
mvn -pl app test -Dtest=GeneralScenarioIT -Dsurefire.failIfNoSpecifiedTests=false -Deval.runs=5 [-Deval.model=...]
```

**Whole-chain scenarios.** `app`'s `ChainScenarioLiveIT` runs the real general, squad leaders and
soldiers against the dev server on a named scenario, and prints a pass rate. Each run makes these
changes to the dev world:
- builds a stone arena in open air at x=500 z=500, far enough from spawn that nothing there is
  bulldozed and that its soldiers don't see or meet anyone else;
- connects its own squads, Charlie (`Ike`, `Jo`, `Kit`, `Lou`) and Delta (`Max`, `Ned`, `Ola`, `Pip`),
  so a garrison (section 3) can stay connected as Alpha and Bravo meanwhile;
- teleports the soldiers onto it;
- heals and feeds them, and replaces their inventories;
- sets the time of day.

It needs the server and model up and nothing else connected as Charlie's or Delta's soldiers.
For each run it holds a lock file (`app.ExperimentLock`, in the temp directory) that a garrison
yields to. The scenarios and their judges are in the class.

```bash
mvn -q install -DskipTests
mvn -pl app test -Dtest=ChainScenarioLiveIT -Dsurefire.failIfNoSpecifiedTests=false -Dscenario=patrol-base-before-dark [-Dscenario.runs=5]
```

`-Dscenario=hold-base-through-the-night` holds the bases until dawn (about 15 minutes a run).
`-Dscenario=rearm-to-iron` gives the squads a quiet morning, cobblestone, logs and a row of iron ore
to climb the equipment ladder to a shield (up to 9 minutes a run). `-Dscenario=shield-against-skeletons`
sets armed, shielded squads against two skeletons in daylight (a few minutes), and
`-Dscenario=rearm-to-chestplate` gives them ore enough to climb to iron chestplates.
`-Dscenario=climb-soil-terrace` and `climb-stone-terrace` set a 3-high terrace in their way, with no
blocks to pillar with: they cut steps into it, by hand or with wooden pickaxes. Add
`-Dlogging.level.io.github.jayessdeesea.minecraft.command.ai.SpringAiGeneral=DEBUG` to log every
prompt the general sees and its reply.

Add `-Dminecraft.general.chat-model=anthropic` (with `ANTHROPIC_API_KEY`) to run the general on a
frontier model.

## 3. Run the chain of command

```bash
docker compose up -d
mvn package -DskipTests
java -jar app/target/app.jar
```

This connects two squads (`app.Roster`, from `minecraft.roster`): Alpha (`Alice`, `Bob`, `Carol`,
`Gus`) and Bravo (`Dana`, `Eli`, `Fran`, `Hal`), the first of each its point man, each under its own
LLM squad leader, both under one LLM general (`ChainOfCommand`). The log reads as a radio net: `[General -> Alpha] OPORD`,
`[Bravo -> squad] FRAGO`, `[Alpha -> General] CANTCO`, and each squad's SITREP every 5 seconds.
Orders and CANTCOs also appear in game chat (section 4), spoken by Alice as the radio operator. Soldiers reconnect on their own if a connection dies. The general's mission
starts as `minecraft.general.mission` in `application.yml`.

**The models' queue.** Every decision sent to a model is timed (`app.ModelCalls`): `[Model]
General: waited 0 ms, ran 5600 ms, 1 ahead of it when queued`. One that waited 5 seconds to start,
took 20 seconds all told, or was queued behind three others logs as `[Model] ALARM`. With three
or more in flight, a squad leader's routine look - nothing new to answer - is skipped that cycle
(`routine decision shed`), so a contact or a CANTCO isn't queued behind it.

**Giving orders from game chat.** A player named in `minecraft.operators` (comma-separated, empty
by default) can replace the mission by typing `!mission <text>` in plain chat. The general answers
WILCO on the net and re-plans at once. Anything else an operator says - "it's night", "skeletons on
the ridge" - is a remark: the general answers ROGER, hears it at a decision brought forward, and
keeps the last three in view; the mission stays the mission. Commands from anyone else are logged
and ignored, and their chat is just chat. Offline mode
doesn't authenticate names, so the list guards against accidents, not impostors. Give missions in
absolute coordinates ("hold x=-40 z=0"): the general re-reads the mission at every re-plan, so
"40 blocks west of here" moves the objective another 40 blocks each time.

`!escort` attaches Alpha to you as a protective detail, and `!escort <player>` to another
player: the soldiers ring the player and go after hostiles that come near them
(docs/military-doctrine.md § 10). `!mission <text>` ends it.

Any order can be whispered to a soldier instead (`/msg Bob !escort`): the soldier that hears it
relays it up the chain, and the log says who did. A word starting with `!` that isn't one of the two
orders (`!fortify the base`) is answered SAY AGAIN, with the orders there are - say
`!mission fortify the base` instead.

Each order is followed down the chain and reported back on the net as it goes, under an id from
its WILCO (`OP-3`): the general's decision and what each squad was given, each squad leader's
FRAGO, and each squad's soldiers seen at the task - a squad's WILCO is them doing it, not just
being told. A hop that takes too long - the general past 30 s, a squad leader past 45 s, the
soldiers past 3 minutes - is an `ALARM` naming what they're doing instead. The log has the same
lines under `[Trace]`.

```bash
java -jar app/target/app.jar --minecraft.operators=YourName
```

**A garrison.** Alpha and Bravo can live near where you play, making camp, while scenario runs
use the arena. Start them there and give them a mission about it:

```bash
docker compose exec minecraft console "spreadplayers 0 0 2 8 false @a[name=!YourName]"   # once they've joined
java -jar app/target/app.jar --minecraft.operators=YourName --minecraft.yield-to-experiments=true \
  --minecraft.general.shelter-at-night=true --minecraft.camp=0,0 \
  "--minecraft.general.mission=Make camp around x=0 z=0 and stay within 48 blocks of it, and build it up into a lodgment. Each squad spends the night sealed inside a patrol base near there, until dawn."
```

`minecraft.camp` has the general build the camp up phase by phase, each phase measured
(docs/military-doctrine.md § 17), so the mission needn't list the work. What the garrison must
remember across a restart - each camp wall's layout - is kept under `minecraft.state-dir`
(`garrison-state/`, git-ignored).

The log lines to read first: `[Casualty]`, each death with the soldier's last seconds (§ 6);
`[Alert]`, a soldier stuck in place for a minute other than in its patrol base (§ 3); and `[AAR]`, each dawn's review of the
day before (§ 17).

A soldier rejoins where it last logged out, so a squad that last ran a scenario starts on the
arena; `spreadplayers` puts them back on the ground. `minecraft.yield-to-experiments` stands the
squads still, asking no model anything, while a scenario run holds the experiment lock: both apps
share one GPU, and a run's pass rates and decision times assume the model to itself. A lock left
by a run that died is logged as an error and not obeyed. A scenario run sets the time of day it
needs, then puts the world's clock back to where it would have been, so the garrison doesn't
wake to a night nobody gave it.

**When a soldier is stuck.** `app`'s `WorldProbeLiveIT` reads the world as one connection sees it.
With the garrison stopped, `-Dprobe.name=Alice` joins as that soldier, where it last stood; with
`-Droute=fx,fy,fz,tx,ty,tz` it plans the route both ways, prints the blocks round the start and the
ground heights between, and ticks a soldier along it, printing where it gets to. A `SITREP` line
marked `[NO BLOCK DATA HERE]` is a soldier standing where the world model has nothing: it reads
as solid. `ClockLiveIT` prints the time of day as the app works it out, beside the server's own.
To choose where a garrison's camp goes (`minecraft.camp`), `-Dcamp.survey=true` scores centres
within 48 blocks of `site.x`/`site.z` by how much of the 17x17 round each is level ground, the
flattest first, up to `-Dcamp.maxY` (72 by default), with the water each takes in.

```bash
mvn -pl app test -Dtest=WorldProbeLiveIT -Dsurefire.failIfNoSpecifiedTests=false -Dprobe.name=Alice -Droute=-71,62,-47,-40,69,-17
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
format, so read the composite's parts. Compare the constants of every enum written by ordinal
(`writeEnum`) too: an inserted constant shifts the ones after it without changing any read or
write call - 26.3's `PlayerAction` gained `CHANGE_DESTROY_DIRECTION` at ordinal 1. The data generator's `packets.json` report (section 7)
lists every packet ID, a cross-check on the registration order.

Point `javap` straight at the jar. Don't extract it on Windows: deep `net/minecraft/...` paths
exceed `MAX_PATH`, and `unzip` silently skips them.

## 7. Regenerating the generated registries

`bot`'s block classes, hostile-mob list and item classes are generated from the server itself:
its data reports; for block types and hardness, its own registry (the reports dropped block types in
26.3); and for which blocks a pickaxe digs and how good a pickaxe each needs, the block tags in its
jar. Regenerate all three after every pin bump. Run the server's data generator in the version's
reference folder, with the bundler jar from the version manifest: it writes the reports and
unpacks the server and its libraries there.

```bash
V=26.3; P=777; M=C:/mc-reference/$V
(cd $M && java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports --output generated)

SCP="$(cygpath -w $M/versions/$V/server-$V.jar)"                      # the server and its libraries
for j in $(find $M/libraries -name '*.jar'); do SCP="$SCP;$(cygpath -w $j)"; done
java -cp "$SCP" tools/block-type-dumper/DumpBlockTypes.java > $M/generated/block-types.txt
java -cp "$SCP" tools/item-food-dumper/DumpFoodItems.java > $M/generated/food-items.txt

R=bot/src/main/resources/io/github/jayessdeesea/minecraft/bot/world
CP="<jackson-databind>;<jackson-core>;<jackson-annotations>"   # jars from ~/.m2
java -cp "$CP" tools/block-class-generator/GenerateBlockClasses.java $M/generated/reports/blocks.json $M/generated/block-types.txt $R/block-classes-$P.txt $M/versions/$V/server-$V.jar
java -cp "$CP" tools/entity-hostility-generator/GenerateEntityHostility.java $M/generated/reports/registries.json $R/hostile-entity-types-$P.txt
java -cp "$CP" tools/item-class-generator/GenerateItemClasses.java $M/generated/reports/registries.json $M/generated/food-items.txt $R/item-classes-$P.txt
```

Weapon damage and attack speed aren't in the reports, and an item's stats aren't bound until a
world loads, so they're measured on the live server instead. With the dev server and the squads
running, `sh tools/weapon-stats-probe/probe.sh` puts each weapon in Hal's hand and reads his
`attack_damage` and `attack_speed` attributes. Copy its output into `$R/weapon-stats-$P.txt`,
keeping the `hand` row; `WeaponStatsTest` fails if any weapon the item registry knows is missing.

Delete the old version's files and update the resource names in `BlockClassRegistry`,
`HostileMobRegistry`, `ItemClassRegistry` and `WeaponStats`. Test fixtures that use real IDs (items, entity types,
block states) need the new ones: map each old ID to its name in the old reports, then to the new
ID. In Git Bash, give `java` a Windows-style classpath (`cygpath -w`).
