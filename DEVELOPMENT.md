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

**Choosing the model.** The platoon leader and the squad leaders decide by standing rules,
`minecraft.platoon-leader.decides-with: drills` and `minecraft.squad-leader.decides-with: drills`; set either to
`model` to put that echelon on a model instead. The operator's chat is always a model: the platoon leader's. Each echelon on a model picks its own
provider in `app/src/main/resources/application.yml`: `minecraft.platoon-leader.chat-model` and
`minecraft.squad-leader.chat-model`, each `ollama` or `anthropic`. Both default to `ollama` (`qwen3:14b`, thinking off), so nothing needs an API key. To put the platoon leader
on a frontier model, as the project intends, set `ANTHROPIC_API_KEY` and run with
`--minecraft.platoon-leader.chat-model=anthropic`. The startup log says which each echelon got
(`The platoon leader thinks with anthropic`); a name with no provider behind it stops startup.
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
hills, forest, a pond of water to swim, a lake of hazard to go round, ravine, walled compound, cliff), 20 seeds per family by default, each run on
foot and again carrying cobblestone to climb with. Each case is named
`<family> seed=<n>` (the climbing run adds `climbing`), and a failure prints a top-down map of the world and the soldier's trail.

```bash
mvn -pl bot test -Dtest=SoldierScenarioTest -Dscenario.seeds=200      # a wider sweep
mvn -pl bot test -Dtest=SoldierScenarioTest -Dscenario.only=forest:7  # replay one case
```

**Movement efficiency.** `bot`'s `MovementEfficiencyTest` measures what a block walked, a step up, a step down and a block swum
cost the soldier in ticks, then over every family prices the unbounded search's route at those costs - the reference
best - against the ticks it takes, and prints each family's median and worst efficiency - stairs up a cliff, hills,
a forest, a pond, a lake, a ravine, a walled compound with one gate, and the rest of `Terrain`. Run as the eval it
holds each median to 90%. `app`'s `RecordedGroundEfficiencyTest` does the same on ground kept live - broken camp ground,
Mia's heaped farm (field540), pairs of stands 8 to 24 apart. Left: crowding:

```bash
mvn -pl bot test -Dtest=MovementEfficiencyTest -Dmovement.eval=true -Dmovement.seeds=40
```

The reference is the route planner's own route priced honestly: it measures how well the soldier walks the route it
plans, not whether a cheaper route exists (backlog/simulator-fidelity.md).

**Combat sweep.** `bot`'s `CombatScenarioTest` pits a soldier against one simulated zombie,
skeleton or creeper, and a squad against a pack (`Battle`: rough approximations, not vanilla mob
AI), and prints how the fights end. Use it to measure a change to combat behaviour: `-Dcombat.seeds=200` for more.

**The eval matrices.** `bot`'s `CombatMatrixTest`, `CombatConditionMatrixTest`, `CombatPairsMatrixTest`,
`CombatPairsConditionMatrixTest` and `CombatSquadMatrixTest` (the `scenario` package) hold each cell to a pass rate over
seeds, a cell short of it listed in the class's known set; `app`'s `StuckEvalsTest` replays each recorded stuck soldier
under `stuck/`. What they don't cover yet is backlog/scenario-matrix.md.

**The contact table.** `bot`'s `ContactEvalTest` pins the response `bot.Contact.respond` gives to each combination of
weapon, armour, health, hostile and ground, a row a line, in milliseconds: `mvn -o -q test -pl bot -Dtest=ContactEvalTest`.
The matrices above say whether a fight is won; this says which response was chosen. When a row is added is
docs/conventions.md's ("A contact met live is a row first").

**Squad-leader evaluation.** `app`'s `SquadLeaderScenarioIT` runs the real squad leader, with its
real prompt, against Ollama on the same scenarios. It prints a pass rate per family rather than
pass/fail. Re-run it after any prompt or model change:

```bash
mvn -q install -DskipTests
mvn -pl app test -Dtest=SquadLeaderScenarioIT -Dsurefire.failIfNoSpecifiedTests=false -Deval.seeds=5 [-Deval.model=...]
```

**Platoon leader evaluation.** `app`'s `PlatoonLeaderScenarioIT` does the same for the platoon leader: fixed
situations, each asking one policy question (build before dark, hold a sealed base, leave it when
the mission says so), at a few seconds a decision. Try a change to the platoon leader's prompt or context
here before spending a whole-chain run on it:

```bash
mvn -pl app test -Dtest=PlatoonLeaderScenarioIT -Dsurefire.failIfNoSpecifiedTests=false -Deval.runs=5 [-Deval.model=...]
```

**Whole-chain scenarios.** `app`'s `ChainScenarioLiveIT` runs the real platoon leader, squad leaders and
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
prompt the platoon leader sees and its reply.

Add `-Dminecraft.platoon-leader.chat-model=anthropic` (with `ANTHROPIC_API_KEY`) to run the platoon leader on a
frontier model.

## 3. Run the chain of command

```bash
docker compose up -d
mvn package -DskipTests
java -jar app/target/app.jar
```

This connects four squads of four (`app.Roster`, from `minecraft.roster`): Alpha (`Alice`, `Bob`,
`Carol`, `Gus`), Bravo (`Dana`, `Eli`, `Fran`, `Hal`), Charlie (`Ivy`, `Jack`, `Kate`, `Liam`) and
Delta (`Mia`, `Ned`, `Olga`, `Pete`), the first of each its point man, each under its own squad
leader (standing rules unless `minecraft.squad-leader.decides-with: model`), all under one
platoon leader (the same, by `minecraft.platoon-leader.decides-with`; `ChainOfCommand`). Offline mode needs no accounts, only names; the dev server's
`max-players` is 20. The log reads as a radio net: `[PL -> Alpha] OPORD`,
`[Bravo -> squad] FRAGO`, `[Alpha -> PL] CANTCO`, and each squad's SITREP every 5 seconds.
Orders and CANTCOs also appear in game chat (section 4), spoken by Alice as the radio operator. Soldiers reconnect on their own if a connection dies, and keep trying - every 5 seconds, backing off
to a minute - while the server is down or restarting. The platoon leader's mission
starts as `minecraft.platoon-leader.mission` in `application.yml`.

**The models' queue.** Every decision sent to a model is timed (`app.ModelCalls`): `[Model]
Platoon leader: waited 0 ms, ran 5600 ms, 1 ahead of it when queued`. One that waited 5 seconds to start,
took 20 seconds all told, or was queued behind three others logs as `[Model] ALARM`. With three
or more in flight, a squad leader's routine look - nothing new to answer - is skipped that cycle
(`routine decision shed`), so a contact or a CANTCO isn't queued behind it. A leader deciding by
standing rules isn't in this queue and logs no `[Model]` line: it decides in the SITREP that asks
(`app.Pace`).

**Giving orders from game chat.** A player named in `minecraft.operators` (comma-separated, empty
by default) can replace the mission by typing `!mission <text>` in plain chat. The platoon leader answers
WILCO on the net and re-plans at once. Anything else an operator says - "it's night", "skeletons on
the ridge" - is a remark: the platoon leader answers ROGER, hears it at a decision brought forward, and
keeps the last three in view; the mission stays the mission. Commands from anyone else are logged
and ignored, and their chat is just chat. Offline mode
doesn't authenticate names, so the list guards against accidents, not impostors. Give missions in
absolute coordinates ("hold x=-40 z=0"): the platoon leader re-reads the mission at every re-plan, so
"40 blocks west of here" moves the objective another 40 blocks each time.

`!flatten <x1> <z1> <x2> <z2> <y> [north] [soil]` sets the ground the squads flatten by day (with
`keep-by-day`): that rectangle taken down to y, everything above it dug out - `!flatten -16 16 16 32 63`;
`north` stands its depots off its north edge rather than its west, and `soil` makes its top layer soil -
its rock dug out and the gaps filled with dirt.
It starts as `minecraft.flatten` in `application.yml`; the WILCO on the net names the site taken over.

`!farm <x> <z> [y]` gives the platoon leader a farm on the chunk block (x, z) is in, at y - sea level (63)
unless given: `!farm 40 -20` is a farm on chunk (2, -2). The WILCO on the net names the mission and its three
steps - the chunk flattened a block below y, floored in cobblestone there, the farm template on it - and the log
follows it, `[Mission] a farm at chunk (2, -2) at y=63: ... - DONE` as each step is measured done and
`[Objectives] Alpha: ... under way` as a squad is put on the next (docs/echelons/platoon-leader/decisions.md).

`!escort` attaches Alpha to you as a protective detail, and `!escort <player>` to another
player: the soldiers ring the player and go after hostiles that come near them
(docs/military-doctrine.md § 10). `!mission <text>` ends it.

Any order can be whispered to a soldier instead (`/msg Bob !escort`): the soldier that hears it
relays it up the chain, and the log says who did. A word starting with `!` that isn't one of the
orders (`!fortify the base`) is answered SAY AGAIN, with the orders there are - say
`!mission fortify the base` instead.

**Talking with the platoon leader.** While the app runs, open <http://127.0.0.1:8125/> on the same machine
(`minecraft.operator-chat.port`; the log says `[Chat] the operator's chat with the platoon leader is at ...` on
start). It's bound to the loopback address, so it isn't reachable from another machine. Type a message and press
Enter: the platoon leader answers from its registers, what it was shown at its last decision and the clock now - "what
time of day is it?", "list the missions", "what tasks are open for Charlie?", "what chunk is x=20 z=50 in?" - and from
plain words can give or cancel a farm mission or a tree farm mission ("a tree farm at x=20 z=70"), replace the platoon's mission, escort or set the ground to flatten, said on
the net as from game chat; what each command did is shown under its reply. The strip at the top is the Minecraft time and the time to the next dusk and dawn.
Beside the conversation is the garrison's status, fetched every second (`GET /status`, `app.StatusBoard`): each squad's
objective, and a row a soldier present - where it stands (red when over 256 blocks from the middle of the force, the
median of every soldier's x, y and z), its health and food, what it's doing (a fight, a recovery, its task on the
squad's register or none) and for how long, the routine that has its hands, its order, its report, how often its
activity has changed since it joined, with those that were returns to the one just left in brackets, and what it
carries. Columns are fixed widths and a cell one line, so rows hold still; a cell's whole text, and what a column
means, is its hover. On the clock's row, right, is what the force has at hand: each resource as carried by its soldiers
plus held in every chest known. The page holds the
conversation: reloading it starts a new one. Its parts: [docs/echelons/platoon-leader/decisions.md](docs/echelons/platoon-leader/decisions.md#talking-with-the-operator).

Each order is followed down the chain and reported back on the net as it goes, under an id from
its WILCO (`OP-3`): the platoon leader's decision and what each squad was given, each squad leader's
FRAGO, and each squad's soldiers seen at the task - a squad's WILCO is them doing it, not just
being told. A hop that takes too long - the platoon leader past 30 s, a squad leader past 45 s, the
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
  --minecraft.platoon-leader.shelter-at-night=true --minecraft.camp=0,0 \
  "--minecraft.platoon-leader.mission=Make camp around x=0 z=0 and stay within 48 blocks of it, and build it up into a lodgment. Each squad spends the night sealed inside a patrol base near there, until dawn."
```

`minecraft.camp` (x,z, or x,z,ground to set the camp's ground - its feet height - rather than read it off the
surface: a camp heaped with spoil reads its ground on the heaps) has the platoon leader build the camp up phase by phase, each phase measured
(docs/military-doctrine.md § 17), so the mission needn't list the work. What the garrison must
remember across a restart - each camp wall's layout - is kept under `minecraft.state-dir`
(`garrison-state/`, git-ignored).

**Keeping a garrison running while you change the code** (`tools/ops/`, POSIX `sh`, e.g. Git Bash; runs live in
`GARRISON_RUNS`, default `C:/mc-state/garrison`, outside any cloud-sync folder):
- `hotspots.sh [commits]` is where the code is hard to change, counted: churn x size, files returned to, files that
  change together, long lines, and the last verify's slowest tests - what a refactor pass reads first
  (docs/conventions.md, "Refactor every sixteenth commit, between profiles"). The slowest tests are read from the
  landing queue's worktree, or the one `REPORTS_WORKTREE` names; `hotspots.sh --slowest` prints them alone.
- `python -I tools/ops/test-map.py [--check]` (Python, not `sh`) rewrites the generated tables of
  [docs/test-map.md](docs/test-map.md) from the source tree - the code's tiers, its cycles, and every test class by
  area and kind - and prints any main class it can't place. `--check` writes nothing and exits 1 when the tables are
  stale. Run it after adding or moving tests, and with each refactor pass.
- `quick.sh [mvn arguments]` runs every module's tests but those tagged `slow` - the simulations, the stuck evals and
  the combat matrices, nearly all of the suite's time - for the loop while a change is being made.
- `verify.sh` is the whole suite as the gate runs it - from clean classes, every module, offline. It prints the failures
  and the `BUILD` line; Maven's whole output goes to the file `VERIFY_LOG` names, else to a temp file gone when it exits.
  Its exit is Maven's.
- `install-landing.sh [--dry-run]` installs the landing queue from `origin/main` into `C:/mc-state/landing/` - the jar,
  its `config` (written by the tool alone, what is there kept), `land.sh` - makes the worktree `C:/mc-worktrees/landing`
  and registers the scheduled task `mc-landing` again, its enabled state kept, at normal priority. Run it from a shell
  that is not elevated, when nothing is landing; again after a change under `landing/` lands, a JDK moves, or
  `run.budget.minutes` changes. It asks five questions only the scheduler can answer, the fifth that the task runs at
  normal priority, through a check task made from the same template. It worked when its last line is
  `== installed, the task enabled: every question yes, the audit clean` - or `the task disabled`, on a first install or
  when the task was disabled, which it keeps. At the first verify after an install, read the priority of the whole tree
  by hand - surefire's forks included: `Get-CimInstance Win32_Process | ? Name -match 'java|sh' | select
  ProcessId,ParentProcessId,Name,Priority` shows every process under the task's `javaw` at 8; I/O priority has no
  documented read, and its test is the verify's time. The task's own output is `landing-task.log` there - each verify that ran
  ends with its time against expected and its longest quiet, and an `[Overrun]` line past 1.25 times expected; the last
  verify's log is `verify.log`; one the watchdog ended, `verify.killed.log`.
- `commit.sh <message file> [--timeout <minutes>]` lands this worktree's change through the landing queue: it commits
  the tracked changes (`git add` new files first), squashes the branch into one commit under that message, and waits
  while the queue verifies it on main with the whole suite (`verify.sh`) in the queue's own worktree and pushes it. The
  tree is free to work in meanwhile; what changes during the wait is the next landing's. A change that fails stays
  as committed: fix it and land again. Its exit says how it ended
  (docs/conventions.md, "Landing a change"): 0 is landed, and it then prints the commit count, whether that makes a
  profile or a refactor pass due, and the slowest tests of the last verify. With no queue installed it exits 6 and names
  `install-landing.sh`.
- `profile.sh [seconds]` records the running garrison with Flight Recorder and prints its hot spots;
  `profile.sh --read <file.jfr>` reads one already made (docs/conventions.md, "Profile every sixteenth commit").
- `deploy.sh [<run>]` builds `origin/main` - what's verified and pushed, whatever state the tree here is in - in a
  worktree of its own (`DEPLOY_WORKTREE`, default `C:/mc-worktrees/deploy`), checks the fat jar, stops the run before by the PID in
  `garrison.pid` and starts the new one from a copy of the jar (`start-garrison.sh`). Each run is a line of
  `runs.log` there - its name, when it started (UTC), its commit and its PID; with no name, the new run is the next
  number after the last. Its arguments - your name, the camp, the mission - are yours, one a line in `garrison.args`
  there, not in the repo.
- `run-log.sh [<run>]` prints one run's lines of the log, the last run's by default.
- `round-report.sh [<run>]` is a live round's digest of a run, the last by default: the alerts by soldier and kind,
  the deaths, blocks the tables don't know, the missions and objectives, who was taken off a task most often, the
  changes of activity and what was thrown away - then the chat probe (`--no-chat` leaves it out).
- `chatprobe.sh` asks the operator's chat a fixed set of questions - questions only - and records each
  reply with the register reads it made; each should read and name what's true, with no command given.

**The log** (`app/src/main/resources/log4j2-spring.xml`) is written straight to the file of the hour it's in, never to
one name moved aside after: `<logging.file.path>/yyyy/MM/dd/yyyy-MM-dd-HH_application.log`, the hour in the name and
the time on every line UTC. `start-garrison.sh` puts it under `GARRISON_RUNS/logs`; run by hand, it's `./logs`. At each
hour's turn, files older than seven days go, and the oldest past 100 GB in all. Nothing is printed to the console: follow
the newest file (`tail -f`). Every line carries the app's PID, which is how a run's lines are told from another's
(`run-log.sh`); what the JVM itself prints - a crash, a failure before the log is up - is in
`GARRISON_RUNS/garrison-console.log`.

The log lines to read first: `[Casualty]`, each death with the soldier's last seconds (§ 6); `[Contact]`,
each contact's hostiles, the soldier's kit and the rule that chose its response (§ 8);
`[State]`, each change of a soldier's activity - what it left, after how long, for what, and `(back again)` where
it's a return within ten seconds to the one it had just left (`bot.ActivityTrack`), the same line in a squad
simulation's log;
`[Alert]`, a soldier stuck in place for a minute other than while its squad hides or sleeps (§ 3); `[Idle]`, every ten
minutes the soldier-seconds stood doing nothing past a 10 s pause - under orders, the soldiers' (named), and without
work while the squad has a task, the chain of command's (by squad; `app.StoodStill`); `[Productivity]`, an errand -
any routine's spell with a soldier's hands - that walked 40 blocks or more for 3 items or fewer, with where it started
and the farthest it went, the place whose distance it paid for; and `[Errand]`, every ten minutes each routine's spells,
blocks and items a spell (`app.ErrandLedger`), the day's in the dawn review with the trips that most often didn't
pay and how far out each went; `[AAR]`, each dawn's review of the
day before (§ 17); `[Scoreboard]`, that day's score beside the day before's; and `[Ledger]`, every ten minutes
what each kind of act was planned to take against what it took - a route's ticks a step planned; a farmer's crop-ticks a crop - each ripe crop of its plot a tick, over those brought in:
how long one stands ripe - and cell-ticks a cell sown, each bare cell a tick while it carries seeds - the same line the
squad simulation logs, so the two can be set side by side (`app.Ledger`; backlog/simulator-fidelity.md); and
`[Fidelity]`, a kind's spell more than 1.5 times off the simulation's rate either way (`app.Ledger.BASELINES`: a flat
walk's 4.9 ticks a step, 87.7 crop-ticks a crop, 1045 cell-ticks a cell); and `[Place]`, each block a soldier puts above
sea level with the item and the routine that had its hands ("route step" for its walk's, with the route's goal and
whether a walk round was there), and every ten minutes the
counts by routine / item, and `[Dig]`, each block a soldier breaks at sea level or over it with the block's name and
the routine (`app.PlacementAudit`). Crop-ticks ripe measure harvest hands
against supply as well as the farmer's pace: live (field563) 3,250 a crop, about 22 ripe crops standing on 400-odd
cells, against the simulation's 96 cells for one farmer - the game and the simulation parting. A soldier stuck a minute has its
ground kept for the simulation (`[World]`, `garrison-state/worlds/`): `SimWorlds.replay` makes it a scenario's terrain. The scoreboard's
history, a line a day across restarts, is `garrison-state/scoreboard.jsonl`. The burn of food and wood the supply
gates are set from - a three-day moving average of what's used up: eaten, crafted, built with, as the errand ledger sees
each routine's spells, not the stock's falls, which counted every load put in an uncounted chest - is kept across
restarts in `garrison-state/burn.json` (`app.BurnStore`).

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
with `--minecraft.supply.point=x,y,z` (the chest's block). Soldiers draw from it when the platoon leader
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
| `mvn -pl app test -Dtest=TerrainProbeIT -Dsurefire.failIfNoSpecifiedTests=false -Dprobe=x,y,z` | Diagnoses a stuck soldier: prints how our terrain model classifies the blocks around a spot (solid, hazard, open) slice by slice, and the region reachable from it. `-Dprobe.states=true` names each block round the spot; with it, `-Dprobe.layer=true` names every solid cell at the probed height within `-Dprobe.radius`; `-Dprobe.keep=true` prints what the keep's dig leaves standing, by cell |
| `mvn -pl app test -Dtest=StaircaseProbeIT -Dsurefire.failIfNoSpecifiedTests=false -Dprobe=x,y,z -Ddepth=16` | Diagnoses a mine that will not site: for every head `Staircase.site` tries round (x, z), near ground level y, prints the first step it finds blocked (water, lava, an open cave) or clear |

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
