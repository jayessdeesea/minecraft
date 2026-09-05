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

**No separate Frame abstraction over Netty.** We considered a pure-POJO `Frame`/`FrameCodec`
interface layer (OSI-style: byte stream → frame → packet, independent of Netty) so framing logic
could be unit-tested without a real channel. Decided against it: Netty's own `ChannelPipeline`
already *is* a chain of decoupled handlers (frame decoder → packet codec → application handler),
and Netty is a deliberate choice here, not something we're trying to abstract away. Packet codecs
in `protocol.packet` read/write `ByteBuf` directly rather than going through an extra interface.
Revisit only if framing logic actually needs testing independent of a Netty channel and
`EmbeddedChannel` proves insufficient for that.

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
