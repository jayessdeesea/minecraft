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
- **`command`** — the chain-of-command *interfaces*: `General`, `SquadLeader`, `Soldier`,
  `Order`, `Objective`. Deliberately framework-free — no Spring, no Netty. This is the "interface
  package" both `command-ai` (an implementation of `General`/`SquadLeader`) and `bot` (a consumer
  of those interfaces, and the implementation of `Soldier`) depend on, so that neither has to know
  about the other.
- **`command-ai`** — Spring AI-backed implementations of `General`/`SquadLeader` from `command`.
  Depends on `command`, not the other way around.
- **`bot`** — the deterministic per-tick soldier runtime. Depends on `protocol` (to actually
  connect and act) and `command` (interfaces only) — never on `command-ai`. A soldier must not
  know or care whether its orders came from an LLM or a hand-typed test order.
- **`app`** — the Spring Boot composition root: `main()`, DI wiring of concrete beans from every
  other module, and the ArchUnit tests that enforce the boundaries above.

**This is a first cut, not a commitment.** Per-module granularity was chosen deliberately small:
package-level interface/impl separation (`command` vs `command.ai`) gives most of the decoupling
benefit already, so a package only graduates into its own module when something real forces it
(reuse, independent versioning, or a build-time reason to forbid a dependency edge that a package
boundary can't express). Don't pre-split modules speculatively.

**Enforcement: ArchUnit, not JPMS.** `app`'s `ArchitectureTest` encodes the boundary rules above
(Netty confined to `protocol`; `command` never depends on `command.ai`; `bot` never depends on
`command.ai`; `command` never depends on Spring or Netty) and runs as part of the normal test
suite. Real `module-info.java`/JPMS module-path enforcement was considered and rejected: Spring
Boot applications almost universally run on the classpath rather than the module path, because
Spring's proxying/reflection-heavy DI has real friction under JPMS's strong encapsulation.
ArchUnit gives compiler-adjacent enforcement (a broken rule fails the build) without that
friction.

## Protocol pipeline architecture

`protocol`'s Netty pipeline is deliberately seven stages, each with exactly one responsibility,
not the two merged handlers ("frame+compress" / "decode+dispatch") the module started with:

```
bytes → FrameDecoder → CompressionDecoder → MarshallingDecoder → SessionStateManager → control handler → bot
bytes ← FrameEncoder ← CompressionEncoder ← MarshallingEncoder ← SessionStateManager ← control handler ← bot
```

(Netty runs outbound handlers in the reverse of their pipeline-registration order, which is why
registering all seven in the order shown above produces the correct order in *both* directions —
see `MinecraftClient.addPipeline`'s javadoc.)

**Why split this far — three tiers of responsibility, not two:**
1. **Marshal/unmarshal** — bytes ↔ typed `Packet`. Zero state, zero decisions
   (`ProtocolCodec`, each packet type's `encode`/`decode`).
2. **Connection mechanics** — stateful, but *zero-decision*: does replying require any input from
   outside `protocol`? If no, it's mechanics. This tier is itself split by a further seam - mutate
   vs. send (see below) - but both halves are "not business logic just because they're stateful,"
   same category as an HTTP client transparently handling gzip content-encoding.
3. **Business logic** — content reflecting an actual decision or preference (view distance,
   locale, movement, respawn policy). Never belongs in `protocol` — it's `bot`'s job, driven by
   the deterministic per-tick loop described above. One concrete violation to watch for:
   `ClientInformationPacket.defaults()` is currently hardcoded inside `JoinClientHandler`; that's
   tier-3 content living in tier-2 code, tolerated for now because nothing yet needs to vary it,
   but a real Soldier should be able to override it.

**Tier 2 splits into two handlers along a mutate-vs-send seam.** Not every tier-2 mechanic
requires a reply - `SetCompressionPacket` triggers a mutation (toggle the compressor) with no
response at all, while `LoginSuccessPacket` triggers both a mutation (state transition) *and* a
send (Login Acknowledged, Client Information). That distinction is why there are two handlers,
not one:
- **`SessionStateManager`** — owns `ProtocolState`, reacts autonomously to whatever packet implies
  a mutation (`SetCompressionPacket`, `LoginSuccessPacket`, `FinishConfigurationPacket`), swaps
  compression/marshaller state accordingly, logs unrecognized packets (once per distinct kind —
  Play alone repeats the same unrecognized ID hundreds of times a second), and **never sends
  anything** — every packet is forwarded downstream via `ctx.fireChannelRead(packet)`
  unconditionally, whether or not it acted on it.
- **Control handler** (`StatusClientHandler`, `JoinClientHandler`) — sends every reply, and holds
  no `ProtocolState` field of its own. Dispatch here is entirely by packet *type*
  (`instanceof LoginSuccessPacket` → send X), never by querying current state, since a packet's
  type already implies what to do with it. The one place it does query `SessionStateManager.state()`
  is deciding whether to keep resetting its own completion deadline — a control-handler-specific
  concern, not something `SessionStateManager` needs to know about.

The one transition with no inbound trigger — HANDSHAKE → LOGIN/STATUS, since Handshake has no
response at all — can't happen autonomously inside `SessionStateManager`; the control handler's
`channelActive` calls `sessionState.transitionTo(ctx, ...)` once, explicitly, to kick it off.
Everything after that, `SessionStateManager` drives itself by observing the packet stream.

**A real bug this design surfaced, not just moved around:** a first cut had the control handler
reset its completion deadline whenever state was anything other than `LOGIN` - which also matched
`PLAY`, so every one of Play's ~1,000+ packets/2s kept re-canceling the fixed observation window
and the test timed out. Fixed by checking for `CONFIGURATION` specifically. Worth remembering:
Play is continuous traffic with no natural quiet gap, unlike Login/Configuration's genuine
burst-then-pause - a "wait for quiet" strategy silently doesn't apply there.

**Static pipeline structure, swappable delegates — not dynamic pipeline mutation.**
`CompressionDecoder`/`CompressionEncoder` and `MarshallingDecoder`/`MarshallingEncoder` are always
present in the pipeline; what changes is an internal field (`compressionThreshold`, or the active
`PacketMarshaller`), set via a narrow setter `SessionStateManager` calls on each transition. Chosen
over Netty's `ChannelPipeline.replace()`/`addAfter()` for three reasons: the pipeline's shape is
knowable by reading `addPipeline()` once, rather than having to trace every place that might
mutate it at runtime; each handler with a swappable delegate is trivially unit-testable in
isolation (construct it, set the delegate, feed it input); and it avoids `ByteToMessageDecoder`'s
real subtleties around leftover buffered bytes when a decoder is replaced mid-stream. The
criterion for when you'd actually need real pipeline surgery instead: a stage's fundamental
*contract* needs to differ, not just its behavior. Neither stage here does — decompression is
always "`ByteBuf` in, `ByteBuf` out," marshalling is always "packet ID + bytes → `Packet`."

**Per-state `PacketMarshaller` implementations, not one generic `Map`-keyed class.** Each of the
five states (`HandshakeMarshaller`, `StatusMarshaller`, `LoginMarshaller`,
`ConfigurationMarshaller`, `PlayMarshaller`) is its own small class rather than one class
parameterized by a lookup table. Not because it changes who tracks state — `SessionStateManager`
owns that either way — but because a named class is a discoverable, navigable home for "everything
that can arrive during this state," matching how docs/protocol-spec.md is itself organized by
state. `HandshakeMarshaller.decode()` throws unconditionally: nothing is ever clientbound during
Handshake, so if it's ever invoked that's a protocol anomaly worth failing loudly on, not a
missing case to add.

**Unknown packets are logged, not silently dropped.** Every marshaller falls back to
`UnknownPacket(id, rawBytes)` for anything not in its table, and `SessionStateManager` logs that
at WARN (once per distinct state+ID kind). The catalog for Configuration (mostly) and all of Play
is intentionally incomplete right
now — see docs/protocol-spec.md § 3.4 on deriving IDs from real traffic rather than guessing — and
the warning is what drives that toward zero over time instead of it going unnoticed. Play's first
confirmed exception is `PlayLoginPacket` (0x31, "Login (play)"), identified by finding the
server's own reported entity ID inside its raw hex, then cross-checking several other fields
against server.properties - the same by-content method that identified every Configuration ID.
`PlayState` is the seed of the world-model layer this feeds - see its javadoc for why that's
deliberately a small, growing shape rather than a big one designed upfront.

**Never let a wire-supplied length or count size an allocation without a cap.** Applies uniformly:
`ProtocolCodec.readString`/`MAX_STRING_LENGTH`, `CompressionDecoder`'s Data Length check against
`MAX_FRAME_LENGTH` (the "compression bomb" guard from docs/protocol-spec.md § 4.3/§ 8), and
`ProtocolCodec.readVarIntCount` for any count-prefixed list (used by `KnownPacksPacket` today).
The same principle will apply to NBT's nesting *depth* once that's implemented — the recursive
case of the same guard, not implemented yet because nothing we decode today recurses. **On
violation, every one of these terminates the connection — never skip-and-continue.** See the
tenet below for why, and docs/protocol-spec.md § 4.3/§ 8 for the full reasoning per guard.

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
- [docs/world-data-spec.md](docs/world-data-spec.md) — canonical chunk/block-state/light data
  format the client decodes from the server. Explicitly client-decode-only; world generation,
  storage, and light propagation are the (self-hosted vanilla) server's job, not ours.
