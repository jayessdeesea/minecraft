# Architecture

How the system is shaped, and why: the timescale split between echelons, how each echelon's
behaviour is modelled, the module boundaries, and the protocol pipeline.

## Latency architecture — why the hierarchy is stratified by timescale, not just rank

Minecraft ticks at 20/s (50ms/tick). LLM inference takes low-single-digit seconds. Those two
facts drive the entire control-loop design:

- **Soldiers run deterministic, non-LLM code every tick.** Movement, pathing, combat reflexes —
  plain Java, no model call, full stop. This loop cannot afford LLM latency.
- **Squad leaders invoke the LLM periodically** (interval TBD — start conservative, e.g. every
  few seconds or triggered by a notable squad-level event) to (re)issue orders to their soldiers.
  Soldiers keep executing the last order between invocations.
- **The general invokes the LLM even less frequently**, issuing higher-level objectives down to
  squad leaders.

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
- **`SquadLeader`/`General` follow the identical shape one level up** (`SpringAiSquadLeader`/
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
  a routine that should give way mid-run says so in its own guard.
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
reference-counted (loaded by which connections), not last-writer-wins. `ConcurrentHashMap.compute()`
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
  `General`/`SquadLeader`) and `bot` (a consumer of those interfaces, and the implementation of
  `Soldier`) depend on, so that neither has to know about the other.
- **`command-ai`** — Spring AI-backed implementations of `General`/`SquadLeader` from `command`
  (`SpringAiGeneral`/`SpringAiSquadLeader`). Depends on `command`, not the other way around.
- **`bot`** — the deterministic per-tick soldier runtime. Depends on `protocol` (to actually
  connect and act) and `command` (interfaces only) — never on `command-ai`. A soldier must not
  know or care whether its orders came from an LLM or a hand-typed test order.
- **`app`** — the Spring Boot composition root: `main()`, DI wiring of concrete beans from every
  other module, and the ArchUnit tests that enforce the boundaries above.

**This is a first cut, not a commitment** — the general form of this test is stated once, in
[conventions.md](conventions.md#how-this-project-stays-legible-as-it-grows) (tenet 3); here's what it looks like applied to module
granularity specifically. Per-module granularity was chosen deliberately small: package-level
interface/impl separation (`command` vs `command.ai`) gives most of the decoupling benefit already,
so a package only graduates into its own module when something real forces it (reuse, independent
versioning, or a build-time reason to forbid a dependency edge that a package boundary can't
express). Don't pre-split modules speculatively.

**Enforcement: ArchUnit, not JPMS.** `app`'s `ArchitectureTest` encodes the boundary rules above
(Netty confined to `protocol`; `command` never depends on `command.ai`; `bot` never depends on
`command.ai`; `command` never depends on Spring or Netty) and runs as part of the normal test
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

`app`'s `SquadSimulationTest` runs a whole squad through a task with the real runner, chain of
command, drills and routines, in seconds:
- **Time and threads** come from `Clockwork`: the wall clock and real threads in the app, and in
  the test `SimClock`, which runs every scheduler on one simulated clock, in time order, on the
  test's thread. A run is the same every time, and a task that throws fails it.
- **The server** is `SimServer`: one world, and a `PlaySession` per soldier answering as the
  vanilla server does - blocks broken and placed, drops picked up, slots swapped, the time of day -
  and refusing what's out of reach. What it doesn't model throws, naming the action.
- **The general and squad leader are scripted**: a `General` that hands out a fixed objective, and
  a `ChatModel` that answers a fixed `SquadOrder`.

A live failure becomes a scenario there first: the world that caused it, and the outcome the
squad should reach (cobblestone and raw iron carried, say), not the steps it should take. The app
log goes to `app/target/test.log`.
