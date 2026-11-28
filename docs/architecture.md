# Architecture

How the system is shaped, and why: the timescale split between echelons, how each echelon's
behaviour is modelled, the module boundaries, and the protocol pipeline.

## Latency architecture — why the hierarchy is stratified by timescale, not just rank

Minecraft ticks at 20/s (50ms/tick). LLM inference takes low-single-digit seconds. Those two
facts drive the entire control-loop design:

- **Soldiers run deterministic, non-LLM code every tick.** Movement, pathing, combat reflexes —
  plain Java, no model call, full stop. This loop cannot afford LLM latency.
- **A leader is paced by what decides for it** (`app.Pace`, built once for each leader from
  `minecraft.squad-leader.decides-with` and `minecraft.platoon-leader.decides-with`: a leader that
  is `command.DecidesByRules` gets the rules' pace, any other a model's). The waits belong to the
  model, not to the rank:

  | | Standing rules (the default) | A model |
  |---|---|---|
  | Squad leader's routine look | every SITREP - the squad loop's second | every 20 seconds |
  | Squad leader, on a soldier's new CANTCO or contact | that SITREP | not within 8 seconds of its last decision |
  | Platoon leader, on a request (a task done or stalled, Class I RED, a squad leader's CANTCO) | that SITREP | not within a minute of its last decision |
  | Where the decision runs | the squad leader's where it's asked, on the squad loop's thread: its order stands before that SITREP's orders go out. The platoon leader's on its own thread, started at once: its objectives are there for the next SITREP | on the echelon's own thread, queued, timed and alarmed on (`app.ModelCalls`); a routine one is shed while 3 are in flight |
  | The squad leader's terrain survey (a flood of the ground its point man can reach) | on the survey's own clock (`app.Surveyed`), on the squad leader's thread: every 20 seconds, or sooner once the point man is 16 blocks from where the last was taken or the objective is another. A decision reads the last one taken | one each decision, taken as it decides, on its thread |
  | The platoon leader's resource surveys (what each squad sees to gather) | one each decision, on its thread | the same |

  A rule answers in microseconds, so nothing is gained by making it wait and a report waits for
  nothing: a soldier's CANTCO, a squad's task done, are answered in the SITREP that carries them.
  A model's answer takes seconds and is paid for, so it's asked on a slow clock - the higher the
  echelon, the less often - and the soldiers keep executing the last order between its answers.
- **A decision waits on nothing, but what it reads that costs a search has a clock of its own.**
  The pathfinder's flood is the costliest thing a decision asks for, and the squad loop gives
  every soldier its orders each second: no search runs on its thread, and none runs once a
  decision. So a rule's decision between two surveys reads ground up to 20 seconds old, taken up
  to 16 blocks from where the point man now stands. The move is measured from where the survey
  says it was taken to the soldier nearest the objective across the ground; the squad loop picks
  the point man it surveys from the same way and within a height of the objective besides, so
  the two can differ by a soldier's place in the formation - or, a squad spread over heights, by
  more: so another is due for a move only once that soldier is 16 blocks from where the survey
  was taken and from where he stood when it was asked for. A survey is never read for another
  objective than the one it was taken for: under a new objective, and for a squad's very first
  decision, the decision is left to the next SITREP, when that survey is in - one order, from
  that objective's ground. A survey that fails is tried again on the same 20 seconds, not each
  SITREP; meanwhile the last one taken for the objective is read, and with none there's no
  decision and the squad keeps its last order, as a model's has none when its survey fails -
  ground not surveyed isn't ground that can't be reached. What changes faster than the ground -
  the soldiers' reports, their positions, the objective - is read fresh from the SITREP.
- **A rule asked every second gives an order only when it differs.** A rule's order equal to
  the standing one is neither sent, logged nor handed to the squad loop again (`Pace.stands`;
  the loop works an order's plan changes once for each order it's handed), as the platoon
  leader's objective equal to the standing one "holds". A model's every decision is said, as it
  always was. Two orders alternating second by second are a rule's bug, to be fixed in the rule:
  no wait is put back to hide one. A contact that comes and goes each second is one contact,
  reported once and ordered on at its start and its end (`command.SquadContact`); a task state
  or a squad leader's CANTCO that flickers each second still wakes the platoon leader each time
  (`DecisionPaceTest` pins each count).
- **A decision that fails, fails alone.** A rule's is the chain of command's `app.Faults`':
  its stack once, counted after, an alarm a minute while it lasts, in the dawn review. A model's
  is logged each time, "no decision - ...", as it's asked seconds apart.

This is deliberate: intelligence is stratified by decision timescale as much as by rank. Don't
collapse this — e.g. don't have a soldier call an LLM per-tick "just for this one decision"; that
reintroduces the exact latency mismatch the architecture exists to avoid.

## Behavior modeling: functional core, imperative shell

Each level of the hierarchy is built as a **pure decision core** wrapped by a thin **imperative
shell** that supplies real dependencies and executes real effects. `Soldier.receiveOrder`/`status`
and `DeterministicSoldier.tick` (see `command`/`bot`) accept and return plain, framework-free value
types (`Order`, `Position`, `SoldierStatus`) and a narrow read-only dependency (`WorldView`) passed
in as an argument, never held as ambient/global state. This is deliberate, not incidental:

- **Testability without a live server or an LLM.** A Soldier's per-tick decision — "keep trending
  toward the current Order's target, unless `WorldView` says the next step is solid" — is exercised
  by plain JUnit `*Test`s (not `*IT`) that construct a fake `WorldView` (a one-line lambda: "always
  clear," "always solid") and assert on `status()` across ticks. No Netty, no live dev server, no
  network at all — a deliberate contrast with `StatusPingLiveIT`/`JoinObservationLiveIT`, which
  stay necessary for exercising the actual wire protocol, but shouldn't be the only way to verify
  decision logic.
- **`SquadLeader`/`PlatoonLeader` follow the identical shape one level up** (`SpringAiSquadLeader`/
  `SpringAiGeneral` in `command-ai`), with one real difference from `Soldier`: an LLM call is
  inherently non-deterministic, so that decision core can't be fully pure. Each `decide(...)`
  splits into three pieces so only the middle one is impure: `buildContext(...) -> PromptContext`
  (pure: the situation, as the user prompt template's parameters) → `callModel(PromptContext)` (impure — the one
  call into Spring AI's `ChatClient`, returning a structured-output reply record) →
  `toSquadOrder`/`toObjectives(reply)` (pure validation into domain types; anything invalid
  throws). Only `callModel` needs faking in a test — with a hand-written `ChatModel` that fails if
  invoked, or one that answers canned JSON to exercise the whole structured-output path. This split
  isn't just for tests — it's the same boundary that keeps provider-specific shapes out of the
  decision logic (see [stack.md](stack.md)). The squad leader decides *what* (objective, formation, interval, intent - a `SquadOrder`);
  turning that into per-soldier slots is geometry, done deterministically by `command.Formations`,
  so the model spends judgement only where judgement is needed. Neither echelon calls the model
  when there's nothing to decide yet (no objective, no squads). `app`'s `ChainOfCommand` decides
  *when* each echelon thinks (see its javadoc). How prompts are written: [prompting.md](prompting.md).
- **A routine is an expect script: act, then wait for the confirming event - never for time.**
  Anything that acts and then needs the server's answer (crafting, placing, opening a window,
  smelting) is a `bot.script.Script` of steps. Each step names the event that confirms it, read
  from the soldier's own world/inventory model, and a deadline derived from server timing. The
  rules:
  - **Resumable by construction.** A step whose result is already there is skipped, so a routine
    is restarted rather than resumed. Its state lives in the world and the inventory, not in a
    program counter.
  - **Guards interrupt; faults fail.** A guard that stops holding (a hostile near, the work no
    longer wanted) *interrupts*: windows close, there's no backoff, and the next run picks up where
    this one stopped. A missed deadline, or an answer that rules the step out, *fails* loudly with
    the step and what was expected, then backs off. An answer already in is recorded before guards
    are checked. Exceptions to a guard belong in the guard itself: the unarmed soldier at an open
    table finishes its sword (`bot.craft.Crafter`).
  - **Plan, then act.** Work out the whole job first, then batch it so each station is visited once
    (`bot.craft.CraftPlan`): 20 torches are one furnace load and one round of crafting, and a sword
    and a pickaxe come from one opening of the table. Use what is already standing within reach;
    a station that isn't is missing from the plan, never made and put down: it's the workshop's
    ([military-doctrine.md § 11](military-doctrine.md#11-pre-combat-checks)).
  - **Tested against a fake server** that answers as the real one does (`FakeCraftingServer`,
    `FakeChestServer`), a tick late, including one that never answers.
  - **Sending back to back is fine when the server applies in order.** Several clicks, or a swap,
    select and place, can go out in one tick without waiting between them; one answer confirms
    them all (`SupplyRun`, `Footing`).

- **One routine has the hands at a time, by one priority order** (`bot.Hands`). Routines tick in
  that order, each told whether it's free: free while no other is under way. Nothing preempts;
  a routine that should give way mid-run says so in its own guard. The routine holding the hands
  also says where the soldier stands, whatever its squad's order - a dig's included; another's
  stand counts only when none is held.
- **A soldier's activity is one value, and each change of it a log line.** `bot.SoldierActivity` is a sealed
  type - fighting, recovering, on a task, unassigned - so two at once can't be written down. It's the task, not its
  step: the report and the routine with the hands change many times within one task, and are kept beside it. Each
  soldier's tick reads its activity off its report and the task the squad's register has it on, and
  `bot.ActivityTrack` logs each change as `[State]` and counts the changes and the returns - back within ten seconds to
  the activity just left - live and in a squad simulation alike. The activity names what was decided; it doesn't
  decide yet (backlog/soldier-state-machine.md). A soldier's state between ticks is `app.SoldierState`, built with its
  connection, so a reconnect starts it empty.
- **Borrowed from the actor model's discipline, not its runtime.** An actor's single-threaded
  mailbox processing is *one* way to guarantee a unit of state is never touched by two threads at
  once; a per-tick loop that's the sole writer of its own Soldier's state is another. We get the
  guarantee `Soldier` needs (no shared mutable state, no locking) from the tick loop already being
  the sole caller, without pulling in an actor runtime (Akka/Pekko) that would sit awkwardly
  alongside Spring Boot's own threading model and isn't needed to solve a problem this narrow.
  The one cross-thread seam is container updates arriving on Netty's I/O thread for a routine the
  tick is driving; routines `synchronize` on it. If that seam ever races, a per-soldier mailbox
  (Netty enqueues, the tick drains) is the fix. Running ticks on Netty's event loop is not: that
  puts pathfinding on an I/O thread shared by several connections, and Netty outside `protocol`.

**Where world state fits:** a soldier's `tick` depends on `WorldView` (a narrow, read-only query —
is this block solid, which hostiles are near), not on the shared structures behind it. Its real
implementation, `bot.world.WorldModel`, combines a `ChunkCache` and an `EntityTracker`, both fed by
every Soldier's own connection. The chunk cache — loaded as chunks arrive and unloaded as they leave — is a *shared*
structure (the block data is the same ground truth for every soldier; there's no reason to decode
or store it per-connection), but chunk *visibility* is per-connection, so eviction has to be
reference-counted (loaded by which connections). A chunk another connection holds keeps its blocks when sent again -
its block updates keep it current, and a chunk sent earlier can arrive after another stream's update - and a soldier
that respawns lets go of every chunk it held, since the server never unloads what it saw before. A chunk sent again is
also the model's check on itself: where it differs from the one kept, the log says how many blocks and the first few
(`[World]`, `ChunkCache.Divergence`). The camp's chunks are held the whole time and never sent again, so every 10 minutes the
runner logs the camp's wall, the farm's soil and the two cells over it, and the stockpile chest as the model holds them
(`[Sweep]`, `SoldierRunner.SWEEP_EVERY_MILLIS`), for a console sweep against the server. `ConcurrentHashMap.compute()`
gives atomic, per-chunk-key read-modify-write for that bookkeeping directly — no dedicated writer
thread or event queue needed; reads (`isSolid`, called every tick, from every soldier) stay
lock-free `get()`s, which matters because the tick loop is the one place in this system latency is
never supposed to creep in.

## Maven module layout

Multi-module reactor rooted at `minecraft-parent` (root `pom.xml`, `packaging=pom`). Current
modules:

- **`protocol`** — the from-scratch Netty-based wire protocol client (framing, packet codecs,
  compression, encryption, the Handshake/Status/Login/Configuration/Play state machine). Netty
  types are confined to this module — nothing outside it imports `io.netty` directly (enforced,
  see below).
- **`command`** — the chain-of-command *interfaces*, deliberately framework-free (no Spring, no
  Netty). This is the "interface package" both `command-ai` (an implementation of
  `PlatoonLeader`/`SquadLeader`) and `bot` (a consumer of those interfaces, and the implementation of
  `Soldier`) depend on, so that neither has to know about the other.
- **`command-ai`** — Spring AI-backed implementations of `PlatoonLeader`/`SquadLeader` from `command`
  (`SpringAiPlatoonLeader`/`SpringAiSquadLeader`), and the platoon leader's
  conversation with the operator (`PlatoonLeaderChat`). Depends on `command`, not the other way around.
- **`bot`** — the deterministic per-tick soldier runtime. Depends on `protocol` (to actually
  connect and act) and `command` (interfaces only) — never on `command-ai`. A soldier must not
  know or care whether its orders came from an LLM or a hand-typed test order.
- **`landing`** — the local merge queue that lands changes on main. It depends on no other module
  and none depends on it (only `app`'s tests, to import it for ArchUnit), since it runs from an
  installed jar to gatekeep this code. No Spring, no Netty; code outside the package
  `landing.shell`, kept for its shell, does no I/O and reads no ambient clock or environment.
- **`app`** — the Spring Boot composition root: `main()`, DI wiring of concrete beans from every
  other module, and the ArchUnit tests that enforce the boundaries above.

**This is a first cut, not a commitment** — the platoon leader form of this test is stated once, in
[conventions.md](conventions.md#how-this-project-stays-legible-as-it-grows) (tenet 3); here's what it looks like applied to module
granularity specifically. Per-module granularity was chosen deliberately small: package-level
interface/impl separation (`command` vs `command.ai`) gives most of the decoupling benefit already,
so a package only graduates into its own module when something real forces it (reuse, independent
versioning, or a build-time reason to forbid a dependency edge that a package boundary can't
express). Don't pre-split modules speculatively.

**Enforcement: ArchUnit, not JPMS.** `app`'s `ArchitectureTest` encodes the boundary rules above
(Netty confined to `protocol`; `command` never depends on `command.ai`; `bot` never depends on
`command.ai`; `command` never depends on Spring or Netty; `landing` stands alone and its core does
no I/O) and runs as part of the normal test
suite. Real `module-info.java`/JPMS module-path enforcement was considered and rejected: Spring
Boot applications almost universally run on the classpath rather than the module path, because
Spring's proxying/reflection-heavy DI has real friction under JPMS's strong encapsulation.
ArchUnit gives compiler-adjacent enforcement (a broken rule fails the build) without that
friction.

## Protocol pipeline architecture

`protocol`'s Netty pipeline is deliberately eleven stages, each with exactly one responsibility,
not the two merged handlers ("frame+compress" / "decode+dispatch") the module started with:

```
bytes → FrameDecoder → IdleStateHandler → ConnectionWatchdogHandler → FrameStatsHandler → CompressionDecoder → MarshallingDecoder → PacketLoggingHandler → SessionStateManager → control handler → bot
bytes ← FrameEncoder ← IdleStateHandler ← FrameStatsHandler ← CompressionEncoder ← MarshallingEncoder ← PacketLoggingHandler ← SessionStateManager ← control handler ← bot
```

(Netty runs outbound handlers in the reverse of their pipeline-registration order, which is why
registering all eleven in the order shown above produces the correct order in *both* directions —
see `MinecraftClient.addPipeline`'s javadoc. `ConnectionWatchdogHandler` has no outbound line: it's
inbound-only, so Netty skips it entirely when walking the pipeline for a write.)

Splits into two planes plus the application above them — **data plane** (Framing, the idle
watchdog, Compression, Marshalling: zero *game* decisions, applies identically to every packet or
timing event regardless of content — the watchdog's "close on no traffic" is the same
terminate-not-degrade guard as the resource-exhaustion checks elsewhere in this plane, just keyed
on time instead of size), **control plane** (session management + protocol reply: protocol-level
decisions, never game decisions), **application** (`bot`, entirely outside the pipeline). One
concrete violation to watch for: `ClientInformationPacket.defaults()` is currently hardcoded inside
`JoinClientHandler` — application content living in control-plane code, tolerated for now because
nothing yet needs to vary it (backlog/client-information-override.md).

See [protocol-pipeline.md](protocol-pipeline.md) for the full plane/tier table (including
`FrameStatsHandler`/`PacketLoggingHandler`, the two cross-cutting logging handlers this framing made
room for, and the idle watchdog pair), the tenets each control-plane handler is held to, a worked
walkthrough of an actual join, and the reasoning behind every structural choice — marshaller-per-
state, swappable delegates over pipeline surgery, unrecognized-packet handling, resource-exhaustion
guards. Keep that doc in sync whenever a stage's responsibility changes — this section states the
rule, that doc is where the detail and the "why" for each stage lives.

## Squad simulation: the app's own code, with no server and no model

`app`'s squad simulation runs a whole squad through a task with the real runner, chain of
command, drills and routines, in seconds - its scenarios a class a family (`SquadSimulationTest`, and
beside it the mine's, the keep's and its builders', the wall's, the camp's light, food, recovery, the
farm's and the wood's duties, routing), each under the 1000-line limit:
- **Time and threads** come from `Clockwork`: the wall clock and real threads in the app, and in
  the test `SimClock`, which runs every scheduler on one simulated clock, in time order, on the
  test's thread. A run is the same every time, and a task that throws fails it.
- **The server** is `SimServer`: one world, and a `PlaySession` per soldier answering as the
  vanilla server does - blocks broken and placed, drops picked up, slots swapped, the time of day,
  ground tilled and sown, wheat that ripens a stage every 10 seconds (far faster than the server's
  random ticks) - and refusing what's out of reach. A dig is timed by the tool in hand, as the server times it, and
  one finished too fast is refused (`SimItems.dugTooFast`); a bucket's pour traces its ray as the server does, stopping
  at the first block that's neither air nor water - a crop or grass included (`SimBuckets`). Its animals stand where
  they spawn till a player within 10 blocks holds their kind's food, then walk after it, a block up or down, never
  into water or off a drop (`SimAnimals`); `AnimalScoutEvalTest` walks `bot.supply.Herder` against them, and
  `ScoutMissionSimulationTest` a squad on the operator's mission. A soldier is sent the chunks in view of where it
  joins and those that come into view as it walks, each once, none unloaded behind it (`SimChunks`). What it
  doesn't model throws, naming the action.
- **The platoon leader and squad leader are scripted**: a `PlatoonLeader` that hands out a fixed objective, and
  a `ChatModel` that answers a fixed `SquadOrder`.
- **One builder wires the squad**: `SimSquad` takes the ground's server and clock, the roster, the kit, the scripted
  leaders and the runner's options, and builds the chain of command and the runner as the app does; a scenario is
  its world, its squad and its outcome. A sim wires its own only where `SimSquad` has no option for it.

A live failure becomes a scenario there first: the world that caused it, and the outcome the
squad should reach (cobblestone and raw iron carried, say), not the steps it should take. Each
scenario's app log goes to `app/target/sim/<scenario>.log`; scenarios run in parallel
(docs/conventions.md, "Tests run in parallel").

A stuck soldier is a case in the registry of stuck soldiers (`StuckEvalsTest`; operator, 2026-11-20): one file
under `app/src/test/resources/stuck/` per live failure (the cases the garrison drafts, `garrison-state/worlds/`, are
triaged first by `StuckTriageIT`: the newest replayed with the soldier alone, and a table of which reach their goal -
docs/watches/garrison.md) - the recorded ground (`garrison-state/worlds/`, copied to
`worlds/`), where the soldier stood, what it carried, where it was going, and what correct is there (stands within
so far of its goal within so many minutes). Each is replayed and printed `[Eval] stuck/<case>: PASS|FAIL`. A case
not fixed yet is marked `knownFailure`: reported, not failed - and failed once it passes still marked, so the mark
comes off with the fix and the case holds it from then on. Live, a soldier stuck a minute - or killed, a contact gone wrong (field598: Jack's death
over a pit didn't replay in a world built by hand) - has its case drafted beside its kept ground (`app.StuckCase`, `garrison-state/worlds/<soldier>-<time>.case.json`): where it stood,
its kit rebuilt from the counts it reports, the goal of its walk, marked known - what correct is there is the
filer's to confirm or rewrite.
