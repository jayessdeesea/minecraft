# minecraft — project conventions

## Objective

Build a Minecraft Java Edition client from scratch — protocol implemented ourselves, no Minecraft
client/server libraries — that hosts a chain-of-command hierarchy of AI-controlled agents:

- **General** — one instance. The "smarter" model. Long-horizon strategy, low call frequency.
  Issues objectives to squad leaders.
- **Squad Leader** — one per squad. Lightweight AI, tactical decisions, medium call frequency.
  Commands its soldiers. **Not itself a Minecraft entity or protocol connection** — it's a pure
  decision-making role, not something that joins the game world.
- **Soldier** — an actual Minecraft player bot: its own protocol connection, in the world, doing
  things. Executes orders from its squad leader. Does **not** call an LLM itself.

The Minecraft client is the substrate; the actual subject of the project is how a small number
of AI agents at different levels of the hierarchy coordinate. Keep that framing when making
design tradeoffs — e.g. err toward whatever makes the coordination layer legible, not toward
Minecraft feature completeness for its own sake.

## How this project stays legible as it grows

Four tenets about maintaining the project's own self-model, not about Minecraft or AI specifically
— stated early because they govern how to read and maintain everything below:

1. **Context economy: each fact gets exactly one authoritative home.** This file is meant to be
   loaded whole, every time — a big project with a big AGENTS.md means paying that cost on every
   interaction, most of which don't need most of it. The enemy isn't length directly, it's
   *duplication*: the same fact living in two places that can silently drift apart (this happened —
   AGENTS.md and [docs/protocol-pipeline.md](docs/protocol-pipeline.md) both once carried detailed,
   near-identical copies of the same reasoning before a pass consolidated them). The test: does this
   topic need a diagram, a worked example, a war-story, or enough sub-decisions to warrant its own
   headers? If so, it graduates to its own file under `docs/` — this file keeps only the terse,
   authoritative rule plus a pointer. `docs/protocol-pipeline.md`, `docs/protocol-spec.md`, and
   `docs/world-data-spec.md` are the current instances.
2. **Every organizational boundary needs an explicit owner, not just a description.** Borrowed from
   RACI's spirit more than its letter — not every boundary needs all four roles, but every place two
   pieces of code could plausibly both claim responsibility for the same fact or decision needs an
   explicit answer to "which one actually owns this." The Maven module layout below and the
   protocol pipeline's plane/tier table (docs/protocol-pipeline.md) are both instances: each states
   not just what a piece does, but where the line is when two pieces seem like they could both do
   it. Prefer boundaries a build enforces over boundaries that only exist in prose — `ArchitectureTest`'s
   ArchUnit rules make the module boundaries below real, not aspirational; a rule that only lives in
   a paragraph can rot silently the moment someone doesn't re-read it before writing code that
   crosses it.
3. **Revisit a structural decision when real pressure appears — not preemptively, and not never.**
   The test is always the same: is there a specific, present-tense thing forcing the change (a test
   that can't be written without it, a rule violated more than once for good reasons, a real reuse
   need *today*) — or does the new shape just sound cleaner in the abstract? Only the first is a
   reason to act. See the Maven module layout section below for this test applied to a concrete
   case (when a package graduates into its own module).
4. **Record the lesson, not the expedition.** A doc should state what's true now and, only when
   the discovery method itself is reusable elsewhere, that method — not the blow-by-blow of the
   specific investigation that got there. This happened this session: getting the connection
   watchdog's idle-close path verified live took three separate attempts (a 35s run, a 90s run,
   then a single-soldier run at a deliberately shrunk 5s timeout) before concluding the dev
   server's own incidental traffic made forcing a live idle window impractical in the time
   available. None of that sequence belongs in DEVELOPMENT.md — what belongs is the conclusion
   (the idle-close path is covered by `ConnectionWatchdogHandlerTest`/`PlayClientHandlerTest`, not
   a live run, and why) and nothing about the three attempts that got there. Test before writing:
   delete every sentence describing a specific attempt or dead end — if a future reader loses
   nothing they'd act on, it was history, not knowledge, and shouldn't have been written down.

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
- **`SquadLeader`/`General` will follow the identical shape one level up** (not yet implemented —
  `command-ai` is still a placeholder), with one real difference: an LLM call is inherently
  non-deterministic, so that decision core can't be fully pure. Split it into three pieces so only
  the middle one is impure: `buildContext(List<SoldierStatus>) -> PromptContext` (pure) →
  `callModel(PromptContext) -> LlmResponse` (impure — the one line that actually touches Spring
  AI's `ChatModel`) → `parseOrders(LlmResponse) -> List<Order>` (pure). Only `callModel` needs
  faking in a test; context-building and response-parsing get the same fast, deterministic JUnit
  coverage `Soldier` does. This split isn't just for tests — it's the same mechanism that keeps
  Anthropic-specific request/response shapes out of `command-ai`'s decision logic (see the Stack
  section below): the boundary you draw for testability and the boundary you need for
  provider-independence are the same boundary.
- **Borrowed from the actor model's discipline, not its runtime.** An actor's single-threaded
  mailbox processing is *one* way to guarantee a unit of state is never touched by two threads at
  once; a per-tick loop that's the sole writer of its own Soldier's state is another. We get the
  guarantee `Soldier` needs (no shared mutable state, no locking) from the tick loop already being
  the sole caller, without pulling in an actor runtime (Akka/Pekko) that would sit awkwardly
  alongside Spring Boot's own threading model and isn't needed to solve a problem this narrow.

**Where world state fits:** a soldier's `tick` depends on `WorldView` (a narrow, read-only query —
is this block solid), not on the shared chunk cache itself. The cache that backs it — `bot.world`,
not yet implemented, fed by every Soldier's own connection as chunks load/unload — is a *shared*
structure (the block data is the same ground truth for every soldier; there's no reason to decode
or store it per-connection), but chunk *visibility* is per-connection, so eviction has to be
reference-counted (loaded by which connections), not last-writer-wins. `ConcurrentHashMap.compute()`
gives atomic, per-chunk-key read-modify-write for that bookkeeping directly — no dedicated writer
thread or event queue needed; reads (`isSolid`, called every tick, from every soldier) stay
lock-free `get()`s, which matters because the tick loop is the one place in this system latency is
never supposed to creep in.

## Stack

- **Java 25** (Amazon Corretto)
- **Maven**
- **Spring AI 2.0** — all LLM calls go through the `ChatModel`/`ChatClient` abstraction. Starting
  provider is Anthropic Claude, with a deliberate plan to switch to a locally-hosted model later.
  Because of that: keep provider selection in config, never hardcode
  Anthropic-specific request/response shapes, message formats, or tool-call conventions into
  General/Squad Leader logic. If it only works with Claude, it's not written against the
  abstraction correctly.
- **Netty**, hand-rolled protocol implementation. No MCProtocolLib, no PacketEvents, no NBT/world
  libraries of any kind — VarInt/VarLong, packet framing, compression, encryption, and the full
  connection state machine (Handshake → Status/Login → Configuration → Play) are all written here.

## Target environment

- **Protocol version: pinned, not negotiated.** Target current Minecraft Java Edition release —
  26.2 ("Chaos Cubed", protocol 776) as of project start. 26.3 is in pre-release as of September
  2026; bump the pin deliberately once it ships final rather than letting it drift. No
  multi-version support, no version-negotiation/compat layer — one version, one protocol.
- **Auth: self-hosted, offline-mode dev server only.** No Mojang/Xbox Live OAuth device-code flow,
  no session server, no login encryption request/response — offline mode skips that handshake
  entirely. Revisit this decision if bots ever need to join a real online-mode server.

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
  `Soldier`) depend on, so that neither has to know about the other. Built so far: `Soldier`,
  `Order`, `Position`, `SoldierStatus`. `General`/`SquadLeader`/`Objective` are designed (see
  "Objective" above) but not yet written — `command-ai` depends on them existing here first.
- **`command-ai`** — Spring AI-backed implementations of `General`/`SquadLeader` from `command`.
  Depends on `command`, not the other way around.
- **`bot`** — the deterministic per-tick soldier runtime. Depends on `protocol` (to actually
  connect and act) and `command` (interfaces only) — never on `command-ai`. A soldier must not
  know or care whether its orders came from an LLM or a hand-typed test order.
- **`app`** — the Spring Boot composition root: `main()`, DI wiring of concrete beans from every
  other module, and the ArchUnit tests that enforce the boundaries above.

**This is a first cut, not a commitment** — the general form of this test is stated once, up in
"How this project stays legible as it grows"; here's what it looks like applied to module
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
nothing yet needs to vary it, but a real Soldier should be able to override it.

See [docs/protocol-pipeline.md](docs/protocol-pipeline.md) for the full plane/tier table (including
`FrameStatsHandler`/`PacketLoggingHandler`, the two cross-cutting logging handlers this framing made
room for, and the idle watchdog pair), the tenets each control-plane handler is held to, a worked
walkthrough of an actual join, and the reasoning behind every structural choice — marshaller-per-
state, swappable delegates over pipeline surgery, unrecognized-packet handling, resource-exhaustion
guards. Keep that doc in sync whenever a stage's responsibility changes — this section states the
rule, that doc is where the detail and the "why" for each stage lives.

## No fallbacks — fail loud, not gracefully-wrong

Tenet, not a suggestion: when something is ambiguous, missing, or violates an invariant, the
correct response is to fail immediately and visibly — not to silently pick a plausible-looking
default and continue. Two reasons, from experience, not just principle:
1. A fallback path is by definition rarely exercised, which means it's rarely tested and often
   subtly broken by the time it actually triggers — "not well-paved" in exactly the way a
   rarely-driven road develops potholes nobody's noticed yet.
2. A fallback that "works" by picking something reasonable also *hides* the fact that a real
   decision needed to be made and wasn't. The underlying problem doesn't get fixed — it gets
   papered over, usually until it resurfaces somewhere much harder to diagnose than where it
   actually originated.

**Concretely, once `command-ai` has more than one Spring bean satisfying the same interface**
(multiple `ChatModel`s, multiple strategy implementations, etc.): never use `@Primary`. It
silently resolves ambiguity by picking one bean as the default, which means a bean added later can
silently steal precedence at every injection site that didn't ask for something specific — with no
compile-time or startup-time signal that anything changed. Use `@Qualifier("beanName")` at every
injection point instead: an ambiguous or missing qualifier fails to wire at all, loudly, at
startup — exactly the failure mode we want, and exactly the opposite of `@Primary`'s silent
default. (Nothing in the codebase wires multiple beans yet — this is a rule to have ready before
`command-ai` needs it, not a retrofit.)

**This is also why every resource-exhaustion guard in `protocol` terminates the connection rather
than skip-and-continue** (docs/protocol-spec.md § 8): an oversized frame, a compression bomb, or
(once implemented) a nesting-depth violation are violated invariants, not expected gaps. Skipping
one and hoping the byte stream resynchronizes isn't just against this tenet — for the frame-length
case specifically, it's *provably unreliable*: our framing has no resync mechanism, so guessing
how many bytes to discard risks silently misinterpreting everything that follows for the rest of
the connection. That's a fallback that "doesn't always work" in the most literal, dangerous sense.

**Contrast with `UnknownPacket`, which looks similar but isn't the same category of thing.** An
unrecognized packet ID is not a violated invariant — it's a known, catalogued gap in *our*
implementation (Play's catalog is deliberately incomplete right now), not evidence the connection
itself is broken. Continuing past it is safe because the frame decoder already isolated exactly
how many bytes it occupies — no guessing, unlike the frame-length case above — and it's loud, not
silent: every occurrence is logged (docs/protocol-spec.md § 3.4), specifically so the gap gets
noticed and closed, not forgotten. The question that actually matters isn't "did we continue past
an anomaly" — it's "is this an *expected, tracked* gap that's *provably* safe to skip, or a
violated invariant we're guessing our way past?" Only the first is legitimate to continue through.

## Dependency version pinning

The root `pom.xml` is its own parent — it does **not** inherit from `spring-boot-starter-parent`.
Instead its `dependencyManagement` imports `spring-boot-dependencies` and `spring-ai-bom` as BOMs
(`<scope>import</scope>`), which gives Boot's whole tested compatibility matrix "for free" without
inheriting Boot's plugin defaults/parent chain. `log4j-bom`, `junit-bom`, and `netty-bom` are
imported *above* those two platform BOMs specifically so our own pins for those three libraries
win — for two imported BOMs managing the same `groupId:artifactId`, the first declared in
`dependencyManagement` wins, so order in that block is load-bearing, not cosmetic.

Current pins (bump deliberately, not silently): Java 25, Spring Boot 4.1.1, Spring AI 2.0.1
(requires Boot 4.0/4.1 — hard requirement, won't load under Boot 3.x), Netty 4.2.15.Final (the
4.2.x line, not 4.1.x — new project, no migration burden, recommended default), JUnit 6.1.3,
Log4j2 2.26.1, ArchUnit 1.5.0.

**`spring-boot-maven-plugin` needs an explicit `<execution>` binding `repackage`.** Without
`spring-boot-starter-parent`, that goal isn't bound to the `package` phase automatically — omit
the execution block and `mvn package` silently produces a ~3KB thin jar with no dependencies
instead of a bootable fat jar. `app/pom.xml` binds it explicitly; keep that if the plugin
declaration ever gets refactored.

**If your working copy sits inside a cloud-sync folder** (OneDrive, Dropbox, Google Drive, etc.):
`mvn clean` can intermittently fail to delete `target/` because the sync client has a file open
mid-sync. Not a POM problem — if it happens, delete `target/` manually and build without `clean`,
or move the working copy outside the synced folder.

## Explicitly out of scope (for now)

- Supporting more than one protocol version at a time
- Real Microsoft account authentication / online-mode servers
- Any third-party Minecraft protocol, NBT, or world-format library

## Docs

- [DEVELOPMENT.md](DEVELOPMENT.md) — human-facing runbook: download/run a local dev server,
  connect a native client, current build status. Root-level (not under `docs/`) on purpose —
  it's the first-run entry point, not reference material.
- [docs/protocol-spec.md](docs/protocol-spec.md) — canonical wire protocol spec (packet format,
  states, compression, encryption) for the `protocol` module.
- [docs/protocol-pipeline.md](docs/protocol-pipeline.md) — the tour version of this doc's
  "Protocol pipeline architecture" section: full plane/tier table, a worked walkthrough of an
  actual join, and the reasoning behind each stage. Keep it in sync whenever a stage's
  responsibility changes — this file states the rule, that one carries the detail.
- [docs/world-data-spec.md](docs/world-data-spec.md) — canonical chunk/block-state/light data
  format the client decodes from the server. Explicitly client-decode-only; world generation,
  storage, and light propagation are the (self-hosted vanilla) server's job, not ours.
