# Development Setup

## What this project does

A from-scratch Minecraft Java Edition client — our own Netty-based protocol implementation, no
Minecraft protocol/NBT/world libraries — built as the substrate for a chain-of-command hierarchy
of AI agents: a **General** (infrequent, strategic LLM calls) issues objectives to **Squad
Leaders** (more frequent, tactical LLM calls), who command **Soldiers** — actual Minecraft
player-bots running a deterministic, non-LLM control loop every game tick. See
[AGENTS.md](AGENTS.md) for the full rationale and module layout.

## Current status

`protocol` can log all the way in: Handshake → Login (with compression) → Configuration (Client
Information, Known Packs, registry/tag sync, Finish Configuration) → Play. Confirmed against the
real local dev server, including the server's own log:
```
ScoutBot[/127.0.0.1:59369] logged in with entity id 19 at (4.5, 81.0, 42.5)
ScoutBot joined the game
```
See `MinecraftClient.observeJoin` and docs/protocol-spec.md § 3.4 for how the Configuration
packet IDs were derived empirically rather than from a written spec.

The pipeline splits connection mechanics along a data-plane/control-plane line, plus a couple of
cross-cutting tiers (frame/packet logging, an idle-connection watchdog) — see AGENTS.md's
"Protocol pipeline architecture" section and docs/protocol-pipeline.md's full tier table for the
exact current stage count and control-plane tenets, rather than restating a number here that only
goes stale. Two Play packets are confirmed now: `PlayLoginPacket` (0x31, "Login (play)"),
seeding a small `PlayState` (entity ID, dimension, game mode) confirmed live against the server's
own "logged in with entity id N" log line each run; and `KeepAliveRequestPacket` (clientbound,
0x2C), confirmed the same by-content way — an 8-byte payload appearing in isolation ~15s after
Play begins, matching vanilla's known cadence. The serverbound reply (`KeepAliveResponsePacket`,
0x1C) is now confirmed too — see "Known gaps" below for how.

`command` and `bot` are no longer placeholders: `command` has `Position`/`Order`/`SoldierStatus`/
`Soldier`; `bot` has `DeterministicSoldier` — a pure, per-tick decision core (trend toward an
`Order.TrendToward` target, stop if `WorldView` reports the next step solid), tested as a black
box with fake `WorldView`s, no live server needed. `WorldView` now also has a real implementation,
`bot.world.ChunkCache` — a reference-counted chunk cache per AGENTS.md's "Behavior modeling"
section, unit-tested with synthetic chunk data. Its solidity check is now backed by a real,
generated registry (`BlockSolidityRegistry`) instead of the old "state 0 = air" placeholder - see
"Known gaps" for the generation method and its confidence caveats. `command-ai` is still untouched.

**Alice and Bob both walk now.** `SoldierRunner` connects a hardcoded list of Soldiers ("Alice",
"Bob"), bridges each one's `MinecraftClient.join` `ChunkUpdate`s into one shared `ChunkCache`,
seeds each one's starting `Position` from the server's own confirmed spawn coordinates, gives each
a real `Order.TrendToward` a short distance away in a different direction, and runs each one's tick
loop at 20Hz on its own plain scheduler thread, reporting each tick's position back to the server.
Confirmed live: both join on independent connections, real chunks load and correctly dedupe
through the shared cache, Keep Alive and position-reporting both keep both sessions alive
indefinitely, and - now that `ChunkCache` has a real solidity registry instead of the old
placeholder - both actually walk toward their targets (one completed the full walk and stopped
correctly on arrival; the other stopped partway, almost certainly at a genuine obstacle - see
"Known gaps"). Host/port still hardcoded (`localhost:25565`, matching this doc's own dev-server
setup) - see "Known gaps" for why that's a deliberate stopgap, not an oversight.

`protocol` decodes the real spawn position and can send movement now: `PlayerPositionPacket`
(clientbound, 0x48) carries the server's authoritative position (`PlayLoginPacket` carries none);
`AcceptTeleportationPacket` (serverbound, 0x00) confirms it, exactly like Keep Alive's
protocol-mandated reply; `MovePlayerPosPacket` (serverbound, 0x1E) reports the client's position
back going forward. All three confirmed both by decompiling the real server jar and live against
the dev server. `PlaySession.reportPosition(...)` is the first thing a caller can *originate*
through a session rather than just observe or react to - docs/protocol-pipeline.md's control-plane
tenet 4 called this moment out specifically; see its "Known gaps" entry below.

`protocol` decodes Configuration's Registry Data now too: `RegistryDataPacket` (0x07) reads each
synced registry's name and entry identifiers (skipping each entry's own NBT payload, whose schema
differs per registry and isn't needed yet). Confirmed live across all ~30 registries a real join
sends - see "Known gaps" for the confirmation method and why this is separate from the
block-state-solidity problem below despite both being about "registries."

`protocol` decodes real Chunk Data now: `ChunkDataPacket` (0x2D, "Chunk Data and Update Light")
pulls each vertical section's block-state array out of the bit-packed palette format, confirmed
live against the dev server across many real chunks. See "Known gaps" for how this got fixed after
an initial wiki-derived attempt was wrong, and "Confirming wire formats against the real server"
below for the (very reusable) method that fixed it.

`protocol` also now supports a persistent connection: `MinecraftClient.join` drives the same
Handshake→Login→Configuration→Play sequence as `observeJoin`, but instead of observing Play for a
fixed window and disconnecting, hands back a `PlaySession` the moment Play is reached and stays
connected indefinitely, replying to Keep Alive so the server doesn't time it out. Confirmed live
against the dev server (`PersistentJoinLiveIT`): connects, survives a full Keep Alive round-trip,
closes cleanly on request. `JoinClientHandler`/`observeJoin` are unchanged and remain the
diagnostic tool for cataloging packets — `PlayClientHandler`/`join` is the new, separate control
handler for actually staying in the game (see its javadoc for why the join-sequence logic is
intentionally duplicated between the two rather than shared). `PlaySession` itself is deliberately
minimal for now — `playState()`/`isOpen()`/`close()`, plus (new) a `ChunkUpdate` listener passed
into `join` itself - no `onTick`/`sendPosition` (originating movement/chat) yet, since the packets
those need aren't decoded.

### Known gaps

**Resolved this session:**
- **Serverbound Keep Alive reply ID.** Confirmed as `0x1C` via minecraft.wiki's protocol page for
  our pinned version (776), cross-checked live: the round-trip completes with no disconnect or
  decoder error. Reusable lesson: when active probing alone gives an ambiguous result, a wiki
  cross-check is a lot cheaper than building a packet-capture proxy — `AGENTS.md`'s "no third-party
  protocol library" rule is about not depending on someone else's *implementation* (MCProtocolLib
  etc.), not about refusing to consult documentation to cross-check an empirical result.
- **Persistent Play control handler.** `PlayClientHandler` + `MinecraftClient.join` (above)
  resolve both this and the "`MinecraftClient` persistent-session entry point" item that used to be
  listed separately below — they were really the same piece of work.
- **`bot.world`'s real `WorldView` implementation.** `ChunkCache` (above) - reference-counted,
  `ConcurrentHashMap.compute()`-based, unit-tested. Its solidity check was a placeholder at first;
  see the block-state registry item below for how that got resolved too, later in this session.
- **Chunk data decoding.** `ChunkDataPacket` + `PalettedContainer` now correctly decode real Chunk
  Data packets - confirmed live against the dev server across many chunks, all 24 sections each,
  no decoder errors. The first attempt (built from `WebFetch`-summarized wiki pages) was wrong in
  two ways, found by decompiling the actual server jar rather than guessing further (see
  "Confirming wire formats against the real server" below): each section writes **two** shorts
  before its block-states container (`nonEmptyBlockCount` *and* `fluidCount`, not one "block
  count"), and **the bit-packed data array has no length prefix at all** - it's a fixed size
  computed from `entryCount`/`bitsPerEntry`, not read from the wire. The wiki's account implied a
  single count field and an explicit data-array length; neither is real. Heightmaps also isn't NBT
  as assumed - it's `VarInt count` + per-entry `[VarInt type, length-prefixed long array]`.
- **`MinecraftClient.join` now delivers chunk updates.** A new overload takes a
  `Consumer<ChunkUpdate>`, invoked once per decoded Chunk Data packet on the connection's own
  Netty event loop thread - registered at connect time (not on the returned `PlaySession` later)
  specifically so there's no window where an early chunk could arrive before a listener is
  attached. `ChunkUpdate` is a new, Netty/packet-free result type (chunk X/Z + per-section
  block-state arrays), confirmed live: `PersistentJoinLiveIT` asserts a real 24-section update
  arrives within ~1-2s of joining. This answers the "is there a pipeline/world/bot actor"
  question raised this session with "no" - see the architecture note below.
- **`app` wiring: one Soldier, connected and receiving live world data.** `SoldierRunner`
  connects "Alice" via `MinecraftClient.join`, bridges her `ChunkUpdate`s into a `ChunkCache`, and
  runs `DeterministicSoldier.tick()` at 20Hz. Confirmed live for 2.5 minutes via
  `java -jar app/target/app.jar` - real chunks loaded, session stayed open, clean shutdown, zero
  errors. The tick loop runs on its own plain `ScheduledExecutorService` thread, not the
  connection's Netty event loop - `PlaySession` doesn't expose that (deliberately; see AGENTS.md's
  Netty-confinement rule), and nothing about `tick()` touches the network yet, so there's no need
  for it to share a thread with the connection. Only the `ChunkUpdate` listener itself runs on the
  event loop.
- **Real spawn position and movement-reporting, confirmed live.** `PlayerPositionPacket` (0x48),
  `AcceptTeleportationPacket` (0x00), and `MovePlayerPosPacket` (0x1E) - IDs and field layouts
  confirmed by decompiling the real server jar (see "Confirming wire formats against the real
  server" below), then verified live: real spawn coordinates decoded correctly, the server didn't
  disconnect us after accepting teleportation or after receiving reported positions.
  `PlaySession.reportPosition(...)` is new - the first thing a session can *originate* rather than
  react to or observe (docs/protocol-pipeline.md's control-plane tenet 4). `SoldierRunner` now
  seeds `DeterministicSoldier`'s starting position from this instead of a placeholder, gives her a
  real `Order.TrendToward`, and reports her position every tick.

- **A real block-state registry, replacing `ChunkCache`'s "state 0 = air" placeholder - Alice
  actually walks now.** The placeholder was empirically confirmed to be a hard blocker, not just an
  accuracy gap: live diagnostic (`ChunkCache.stateIdAt`) showed the block at Alice's own feet on
  real terrain was state ID **2248** (ordinary grass sitting on solid ground, ID 9 one block down) -
  the placeholder marks anything nonzero solid, and vanilla Overworld terrain is overwhelmingly
  covered in exactly this kind of non-air-but-walkable decoration, so she was blocked in every
  direction from spawn.

  The block-state ID → block-name/properties mapping still isn't sent over the network by any
  packet (confirmed against minecraft.wiki and the real server source - `docs/world-data-spec.md`
  is corrected). A real client gets it from the server's own `--reports` data-generator output, so
  that's what this project now does too: `tools/block-solidity-generator/GenerateBlockSolidity.java`
  (checked in, run manually, not part of the Maven build) reads the pinned server jar's own
  `generated/reports/blocks.json` and emits
  `bot/src/main/resources/.../non-solid-block-states-776.txt` - every block's `definition.type`
  (its real internal behavior class, e.g. `minecraft:tall_grass` for `short_grass`) checked against
  a curated allowlist of ~75 types confirmed (from well-established vanilla mechanics - flowers,
  torches, rails, tall grass, saplings, coral, and similar) to have no collision. `bot.world`'s new
  `BlockSolidityRegistry` loads that resource and is what `ChunkCache.isSolid` now actually calls.

  **Deliberately not exhaustive** - anything not on the curated list defaults solid, the same
  "unknown = blocked" policy `ChunkCache` already used for unloaded chunks. Confirmed live across
  two independent runs: one Soldier walked the full distance to her target and stopped correctly on
  arrival (next-step block was air, distance was within the tick's own stop threshold); another
  walked partway and stopped ~5 blocks short - almost certainly a genuine obstacle (tree, structure,
  or a chunk-loading edge), which is `DeterministicSoldier`'s intended behavior ("stop at a tree
  instead of walking through it," not path around it), not a bug. Expected to grow incrementally as
  real testing surfaces specific wrong classifications - the same methodology this project already
  uses for its packet catalogs.

- **Registry Data decoding (Configuration state).** `RegistryDataPacket` (0x07) - confirmed two
  independent ways: minecraft.wiki states Configuration ID 7 for this packet, and decompiling the
  real server jar's `ConfigurationProtocols` puts it at clientbound registration index 7, the same
  list whose index 14 lands on `Select Known Packs` - matching this codebase's own
  already-confirmed `0x0E` for that packet exactly (docs/protocol-spec.md's table). Decodes the
  registry name and every entry's identifier; each entry's own NBT payload is skipped, not
  parsed - its schema differs per registry (dimension_type's height/min_y, biome's climate params,
  etc.) and nothing consumes any of it yet, so parsing was scope beyond what's needed right now.
  Restored a small NBT skip-only reader (`Nbt` - the same shape tried and removed during Chunk
  Data's investigation, genuinely needed this time: confirmed via `NbtIo.writeAnyTag`/`readAnyTag`
  that this packet's NBT values omit the root name the same way, even though it turned out Chunk
  Data's Heightmaps field wasn't NBT at all). Confirmed live: all ~30 registries a real join sends
  decode cleanly with correct names and plausible entry counts (`minecraft:worldgen/biome` 66,
  `minecraft:dimension_type` 4, `minecraft:damage_type` 51, `minecraft:enchantment` 43, etc.) -
  these no longer show up as `UnknownPacket` WARN spam during Configuration.

  Separate from, and does not solve, the block-solidity problem above (which needed the *global*
  block-state ID mapping - never sent over the network at all, by this packet or any other - not
  the *data-driven* registries this packet actually syncs).

- **`app` config decision, and a second Soldier - Alice and Bob both visibly move now.**
  Resolved the "app config shape" question with the simplest thing that satisfies
  AGENTS.md's revisit-on-real-pressure tenet: `SoldierRunner.SOLDIERS` is a hardcoded
  `List<SoldierDefinition>` (name + which direction to walk from spawn), not `application.yml` -
  nothing yet needs to reconfigure soldiers without recompiling (no real deployment, one dev
  server, always localhost). Revisit if that changes. No new `PlaySession` API was needed to make
  Bob behave differently from Alice - a different `Order.TrendToward` target per
  `SoldierDefinition` was already enough, so the second "designed and agreed" item from last
  session (`PlaySession` growing to let Bob diverge from Alice) turned out to be satisfied by
  existing surface area rather than needing new code.

  Confirmed live via `java -jar app/target/app.jar`: Alice (entity ID 2475, spawn (7.5, 82.0,
  34.5)) and Bob (entity ID 2476, spawn (1.5, 80.0, 40.5)) both joined on independent connections,
  each ticking on its own thread and reporting position independently - Bob completed his full walk
  and stopped correctly on arrival, Alice stopped early at what stayed a stationary position for the
  rest of the run (consistent with a genuine nearby obstacle, matching `DeterministicSoldier`'s
  intended "stop at a tree" behavior, not a bug). Both consistently logged "9 chunk(s) loaded" from
  the one shared `ChunkCache` - since their spawns are only ~8.5 blocks apart, this is the
  reference-counted cache correctly deduplicating chunks both can see, not each soldier loading its
  own copy. Full `mvn test` reactor run stayed green throughout (all 6 modules).

- **Connection watchdog, and reconnect - a silently-dead connection no longer hangs forever.**
  Decided the placement question ("shouldn't this live in the pipeline, not a handler?") by adding
  two new stages right after Framing: Netty's own `IdleStateHandler` (a real tick generator - no
  custom scheduler needed, it's built in) fires a `READER_IDLE` event if no frame arrives within 30s
  (double vanilla's ~15s Keep Alive cadence, as margin); `ConnectionWatchdogHandler` reacts by
  closing the channel. Framed as the same category of thing as this module's existing
  resource-exhaustion guards (AGENTS.md's no-fallbacks tenet) - a time-based invariant instead of a
  size-based one, terminate rather than guess. `docs/protocol-pipeline.md` and AGENTS.md's pipeline
  diagram are both updated (nine stages → eleven).

  Closing alone isn't useful without a way for a caller to notice and react, so
  `PlayClientHandler.channelInactive` now surfaces every unexpected close (watchdog timeout,
  exception, or the server closing its end) through a new `PlaySessionListeners.onDisconnect`
  callback - deliberately *not* fired for a caller's own `PlaySession.close()`, told apart via a
  Netty channel attribute (`INTENTIONAL_CLOSE`) `NettyPlaySession.close()` sets before closing. This
  was `MinecraftClient.join`'s own previously-flagged trigger for a refactor ("if a third listener
  parameter shows up, replace these with a callbacks holder") - `PlaySessionListeners` is that
  holder, replacing the old two-`Consumer`-parameter overloads.

  `SoldierRunner.onDisconnected` is the reconnect itself: stops the dead soldier's tick-loop
  scheduler and calls `connect(...)` again from scratch, re-seeding a brand new
  `DeterministicSoldier` from wherever the server places that username on rejoining - a single
  immediate attempt, not a retry loop with backoff (AGENTS.md's revisit-on-real-pressure tenet: add
  that if a real deployment ever needs it).

  Confirmed three ways: a new `ConnectionWatchdogHandlerTest` (`EmbeddedChannel`, fires a synthetic
  `IdleStateEvent`, asserts the channel closes) and a new `PlayClientHandlerTest` (asserts
  `onDisconnect` fires for an unannounced close, does *not* fire for a deliberate
  `PlaySession.close()`, and does not fire if Play was never reached) cover the new logic
  deterministically, no live server needed. Live confirmation via `app.jar` at both a temporarily
  lowered 5s idle window and the real 30s one showed zero false-positive disconnects across single-
  and dual-soldier runs - the dev server evidently sends enough incidental traffic (time sync and
  similar) that even 5s was never actually idle in practice, which is reassuring for 30s in
  production but meant the idle-triggered close itself couldn't be forced to fire live in the time
  available; the unit tests are what actually exercise that path today.

- **`General`/`SquadLeader`/`Objective` - `command-ai` is no longer untouched.** `command` gained
  `Objective` (freeform, unlike the closed `Order` set - an LLM-backed `SquadLeader` interprets it,
  so there's no fixed vocabulary to close over), `SquadStatus` (mirrors `SoldierStatus` one level
  up), and the `General`/`SquadLeader` interfaces themselves - both `decide(...)` methods return a
  list positionally matched to their input, no map/key needed since the shell already owns the
  ordered list it built the input from. `command-ai` implements both
  (`SpringAiSquadLeader`/`SpringAiGeneral`) following AGENTS.md's `buildContext -> callModel ->
  parseOrders` split exactly: `callModel` is the only line touching Spring AI's `ChatClient`,
  proven untested-but-isolated by unit tests that wire a `ChatModel` which fails the test if it's
  ever actually invoked. Neither implementation calls the model when there's nothing to decide
  (`SquadLeader.decide` before any `receiveObjective`, `General.decide` on an empty squad list) -
  both return `Order.Idle()`/no objectives instead, the same "no goal yet" default `Order.Idle`
  already models, not a fallback masking a bug. The LLM's response is a small line-based text
  protocol (`"<name>: IDLE"` / `"<name>: TREND <x> <z>"` for orders, `"<squadName>: <text>"` for
  objectives) - any malformed line throws with the raw response included, per AGENTS.md's
  no-fallbacks tenet; nothing here guesses its way past a bad response. Full `mvn test` reactor
  stays green, including `ArchitectureTest`'s existing module-boundary rules against the new types.

  **Deliberately not wired into `app` yet** - scoped out for this pass. Needs: grouping Alice/Bob
  (or however many soldiers exist) into a squad, a decision on `SquadLeader`/`General` invocation
  cadence (AGENTS.md's "Latency architecture" section says "start conservative," not a specific
  number), and a real `ANTHROPIC_API_KEY` to actually run - none of today's unit tests make a live
  model call.

- **Entity awareness, and a "break contact" reflex.** `protocol` decodes Add Entity (`0x01`) and
  Remove Entities (`0x4D`) - IDs from `GameProtocols`' clientbound registration order (the same list
  whose 0x2C/0x2D/0x31/0x48 match this codebase's earlier confirmations), layouts from the server's
  own wire constructors (see "Confirming wire formats" below). Add Entity's velocity field is
  `LpVec3`, a *variable-length* encoding (1 byte if zero, else 6, plus a VarInt if a continuation
  bit is set), so `AddEntityPacket.decode` rejects trailing bytes as a direct check on that skip.
  Both surface through new `PlaySessionListeners.onEntitySpawn`/`onEntitiesRemoved`; confirmed
  live by `PersistentJoinLiveIT` (28 real entities around spawn, no decode errors - the first was
  type 10, which the registry report names `bat`, at y≈-4: underground, as expected).

  `bot.world` gains `HostileMobRegistry` (same generated-resource pattern as
  `BlockSolidityRegistry`, via `tools/entity-hostility-generator` reading `--reports`'
  `registries.json`; the generator fails if a curated name isn't in the registry, so a typo can't
  silently drop a mob), `EntityTracker` (shared `ConcurrentHashMap` by entity ID, no reference
  counting - removal names exact IDs), and `WorldView.nearbyHostiles`. `WorldView`'s two methods are
  both abstract on purpose: a default "no threats" would let a terrain-only implementation (like
  `ChunkCache` alone) compile and silently blind every soldier. The real one is `WorldModel`
  (`ChunkCache` + `EntityTracker`), and `SoldierRunner` ticks every soldier against one shared
  instance - a hostile seen by Bob's connection makes Alice react too. `DeterministicSoldier.tick` checks for a hostile within
  `CONTACT_RADIUS` (8 blocks, untuned) before following its order; if one is there it steps
  directly away from the nearest, and stands still if that step is blocked rather than falling back
  to an order that might lead toward the threat. That's the deterministic half of infantry
  actions-on-contact; the squad-level half (regroup/hold/disperse) belongs in `SquadLeader` once
  `command-ai` is wired in.

  **Confirmed live:** Bob detected a real zombie inside contact range and retreated directly away
  from it, with the zombie's tracked position following it as it chased him (see "Entity movement"
  below).

- **Entity movement.** `protocol` decodes Move Entity Pos/Pos Rot (`0x35`/`0x36`), Entity Position
  Sync (`0x23`), and Teleport Entity (`0x7D`); layouts in `MoveEntityPacket`/`EntityPositionPacket`.
  The move packets carry *changes* in 1/4096-block units relative to the last position the server
  sent for that entity (`ServerEntity` resets its `VecDeltaCodec` base after every send, change or
  absolute). `PlayClientHandler` resolves them against a per-connection position map and surfaces
  only absolute positions (`PlaySessionListeners.onEntityMove`). Per connection, not in the shared
  tracker, because every connection watching an entity receives the same changes - applying them
  to one shared position would move it once per watching soldier. `EntityTracker` is now
  reference-counted by owner like `ChunkCache`, so an entity leaving Alice's view stays tracked
  while Bob sees it; a disconnect drops that connection's whole view. Confirmed live by
  `PersistentJoinLiveIT` (1,800 resolved moves across 101 entities in 10s, every step within a few
  blocks of the last) and in `app.jar` (a chasing zombie's tracked position followed it).

- **Soldier physics: gravity, jumping, a real body.** `DeterministicSoldier` now moves like a
  vanilla player closely enough that the server accepts it: per-tick gravity with vanilla's
  constants (velocity -= 0.08, then *= 0.98), a jump (0.42) to climb a one-block rise, and
  collision against the full 0.6 x 1.8 x 0.6 body rather than the single block under its centre.
  `onGround` is real and reported with every move. Before this the server kicked soldiers for
  "floating too long" (they walked at constant Y over lower ground) and fought them with ~20
  position corrections/s (the server checks the box; we checked a point). Live: zero floating
  kicks and 4 corrections in 90s, down from 5 kicks and 955 corrections.

  Still approximate: every non-air, non-curated block is a full cube (slabs, stairs, fences),
  there's no swimming, and a fall can hurt. Fine until a live run shows otherwise.

  **Open follow-ups - in priority order:**
  - **Fighting withdrawal - confirmed live.** Retreat alone couldn't break contact (zombies chase at
    a soldier's walking speed; Bob died 4 times in one 90s run), so the reflex also strikes the
    nearest hostile within `ATTACK_REACH` (3 blocks) every `ATTACK_COOLDOWN_TICKS` (10) while still
    backing away. `tick` returns the entity to attack; `SoldierRunner` sends it via
    `PlaySession.attack` (Attack `0x01` + Swing `0x3F`). The server ignores attacks until the client
    sends Player Loaded (`0x2C`, empty), so `PlayClientHandler` sends it after accepting the first
    Player Position. Live at night: Alice and Bob each killed a hostile (the server awarded both
    "Monster Hunter") and neither died in 4 minutes. Still bare-fisted; the squad-level answer to
    bigger fights (regroup, mutual support) needs `command-ai` wired in.

    To find night for a live check: Set Time (`0x71`, not decoded) carries the day time as a VarInt
    after the game-time long - night is day time mod 24000 in 13000-23000.
  - **Obstacles stop soldiers dead.** By design (no pathing), but with real terrain it now happens
    within a block or two of spawn - Alice stops at a tree. Pathing belongs with the squad layer
    deciding *where* to go.
  - **One stale report after a respawn.** The tick loop can report the pre-respawn position once
    before the server's correction lands, logged by the server as "moved too quickly." Harmless:
    the correction wins on the next tick.

- **Respawn, and accepting server position corrections.** Soldiers killed between runs stayed dead
  forever - a dead player is sent no chunks or entities until it respawns, so a dead Alice/Bob
  joined, loaded nothing, and stood still (0 chunks, unknown terrain = blocked) with no error
  anywhere. Found via Set Health (`0x68`) reporting `health=0.0` on join. `protocol` now decodes Set
  Health (surfaced as `PlaySessionListeners.onHealth`) and sends Client Command (`0x0C`,
  `PERFORM_RESPAWN`) via `PlaySession.respawn()`; *deciding* to respawn is `SoldierRunner`'s call
  (respawn immediately on `health <= 0`), keeping game policy out of the control plane. Every Player
  Position after the first is now handed to `DeterministicSoldier.correctPosition` - the server is
  authoritative, and the correction is applied on the next tick so the tick loop stays the sole
  writer of position. Previously only the first Player Position was used and every later one was
  ignored, so a server correction never reached the soldier. Confirmed live: both soldiers detected
  death, respawned, and resumed walking from the server's new position.

**Needs a decision:**
- **`PlaySession`'s remaining shape** — chat, or anything beyond position, is still only sketched
  in conversation; reading chunk data and reporting position are both done now (above).

Alice and Bob both exist, walk, and stop at real obstacles today, live-confirmed together on a
shared world model - AGENTS.md's stated objective-framing milestone ("Alice and Bob visibly move
in the game") is reached. What's left is `PlaySession`'s remaining shape above, plus whatever the
curated block-solidity list still gets wrong as more terrain gets walked.

### Data architecture: no pipeline/world/bot actor needed

Raised explicitly this session ("is there a world actor that receives events from all the
pipelines? a bot actor? a pipeline actor?") - answered by reusing what AGENTS.md's "Behavior
modeling" section already settled, not by adding anything new:
- **No pipeline actor.** Each Soldier's connection already runs on its own Netty `EventLoop`,
  which *is* the serialization boundary an actor's mailbox would give you - one thread owns that
  connection's state, no framework needed.
- **No bot actor.** A `Soldier`'s tick loop being the sole writer of its own state already *is*
  the actor discipline AGENTS.md describes, independent of any runtime - `SoldierRunner`'s tick
  loop runs on its own plain scheduler thread (see above), separate from the connection's event
  loop, and that's fine precisely because nothing else ever touches that soldier's state.
- **No world actor.** `ChunkCache` is the answer to "who receives world events from all
  pipelines" - any soldier's connection calls `chunkCache.load(...)` directly from its own event
  loop thread, because the cache (`ConcurrentHashMap.compute()`-based) is already safe for
  concurrent writers. `MinecraftClient.join`'s `onChunkUpdate` listener is what makes this
  concrete, and `SoldierRunner` is now a real, live-confirmed example of it: the listener runs on
  the connection's event loop and calls straight into `ChunkCache` with no additional
  synchronization, while the *tick* loop reading that same cache runs on a completely different
  thread - safe only because `ChunkCache` was built to be a safe concurrent read target in the
  first place, not because of anything special about who's calling it.

### Confirming wire formats against the real server

**A genuinely reusable finding, not specific to Chunk Data:** this project's pinned dev server jar
(`versions/<version>/server-<version>.jar` inside the bundler `server.jar`) ships with real,
unobfuscated `net.minecraft.*` class and method names - despite Mojang not publishing official
`client_mappings`/`server_mappings` for this version yet (checked directly against the version
manifest JSON; the field is simply absent). So the JDK's own `javap` gives readable bytecode with
real names - the sequence of `FriendlyByteBuf.readX`/`writeX` calls in a packet's wire constructor
*is* its field layout. Point it straight at the jar; no extraction, no third-party tool:

```bash
J='C:\mc-dev-server\versions\26.2\server-26.2.jar'
javap -p -c -classpath "$J" net.minecraft.network.protocol.game.ClientboundAddEntityPacket
javap -p -c -classpath "$J" net.minecraft.network.protocol.game.GameProtocols   # registration order = packet IDs
javap -v ...   # when a codec is a method reference: the BootstrapMethods/MethodHandle lines name its target
```

Extracting the jar on Windows is worse than unnecessary: deep `net/minecraft/...` paths under a
long base directory exceed `MAX_PATH`, and `unzip` silently skips them. When bytecode isn't enough
(heavy control flow), a decompiler like CFR gives Java source - same jar, same no-mapping
advantage:

```bash
# one-time: grab a decompiler (CFR is a single jar, no install)
curl -sL "https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar" -o cfr.jar

# extract the versioned jar (the outer server.jar is just a bootstrap/bundler)
unzip -oq path/to/server.jar   # only needed once, to find versions/<ver>/server-<ver>.jar
unzip -oq versions/<ver>/server-<ver>.jar -d extracted

# decompile whatever class you need to check, e.g.:
java -jar cfr.jar extracted/net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket.class \
    --extraclasspath extracted
```

This is strictly better than either wiki lookups or blind byte-guessing for anything beyond the
simplest packets (a bare packet ID like Keep Alive's is fine to confirm via wiki/probing; a
multi-field, nested wire format like Chunk Data's is not) - it's ground truth from the exact
version this project targets, not a possibly-stale or summarization-lossy secondary source. Worth
reaching for *before* extensive empirical probing next time, not after, for anything with real
internal structure: Registry Data decoding and `PlaySession`'s eventual movement-packet work are
both good candidates.

**Smaller loose threads:**
- `SessionStateManager.transitionTo` → narrow to package-private plus a `beginHandshake(ctx,
  nextState)` entry point (docs/protocol-pipeline.md's control-plane tenet 2 footnote) — small,
  not urgent.
- `Objective`/`SquadLeader`/`General` — deliberately not started, out of scope for the current
  milestone, not forgotten.
- A pass to check whether more of AGENTS.md should shrink to a pointer per its own "context
  economy" tenet — only the protocol-pipeline section has been audited against that so far.

## Prerequisites

- Java 25 (Amazon Corretto or another distribution) — matches [AGENTS.md](AGENTS.md#stack)
- Maven
- An Anthropic API key, only once `command-ai` actually calls Claude (not needed for the
  connectivity smoke test in this doc)

## 1. Download and run a local dev server

We target a single pinned Minecraft version — see
[AGENTS.md § Target environment](AGENTS.md#target-environment) for the current pin (it's bumped
deliberately, so treat that section as the source of truth rather than anything below). Get the
server jar from Mojang's own version manifest rather than a third-party mirror, so it's guaranteed
to match:

1. Fetch `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` and find the entry
   under `versions` whose `id` matches the pinned version.
2. Fetch that entry's own `url` — a per-version JSON document.
3. In that document, `downloads.server.url` is the server jar's download URL and
   `downloads.server.sha1` is its checksum. The server's own `javaVersion.majorVersion` field
   tells you the minimum JRE it needs.

```bash
SERVER_JAR_URL="<downloads.server.url from step 3>"
SERVER_JAR_SHA1="<downloads.server.sha1 from step 3>"

mkdir -p ~/mc-dev-server && cd ~/mc-dev-server   # or e.g. C:\mc-dev-server on Windows
curl -Lo server.jar "$SERVER_JAR_URL"

# verify the download
echo "$SERVER_JAR_SHA1  server.jar" | sha1sum -c -

# first run: generates eula.txt and server.properties, then exits
java -jar server.jar nogui
```

Keep the server directory **outside this repo, and outside any cloud-sync client** (OneDrive,
Dropbox, etc., if your working copy happens to be under one — see AGENTS.md's note on `target/`
for why). It's a large third-party binary with its own world save that gets rewritten
continuously while the server runs; a live server's region files under active cloud sync is a
worse version of the same file-locking problem, up to save corruption.

Edit the two generated files before the second run:
- `eula.txt` — change `eula=false` to `eula=true`
- `server.properties` — set `online-mode=false` (this is the offline-mode dev setup AGENTS.md
  commits to; skips Mojang/Xbox auth entirely)

```bash
java -jar server.jar nogui
```

Server listens on `localhost:25565` by default.

### Starting it again later, with a log file (so join/leave events are captured)

Server log lines like `ScoutBot joined the game` only show up in whichever console it's running
in. Tee stdout+stderr to a file too, so a live IT run's join/leave/disconnect lines can be
`grep`/`tail`ed after the fact without keeping the server's own terminal window around:

```powershell
# Windows (PowerShell) — Tee-Object is PowerShell's equivalent of Unix tee
cd C:\mc-dev-server
java -jar server.jar nogui 2>&1 | Tee-Object -FilePath run.log
```

```bash
# macOS/Linux
cd ~/mc-dev-server
java -jar server.jar nogui 2>&1 | tee run.log
```

`2>&1` merges stderr into stdout before the pipe — the server's crash/error output goes to the
log too, not just normal INFO lines. Use `-Append`/`>>` instead of overwriting (`Tee-Object
-Append -FilePath run.log`, `tee -a run.log`) to keep history across restarts rather than
clobbering the previous run's log.

**Don't stop stray `app.jar`/test processes with a bare `Get-Process java | Stop-Process`** - it
matches every process named `java.exe`, the dev server included, and will kill it too (this
happened once - recovered by just restarting the server, nothing was corrupted, but it wasted a
debugging cycle chasing a symptom that was really "the server isn't running"). Filter by command
line instead: `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*app.jar*' }`.

## 2. Connect with the real Minecraft Java client

Multiplayer → Add Server → `localhost:25565`. Your client must be on the same version as the
server — offline mode means any username works, no Microsoft login needed for this server.

This is today's actual smoke test: confirms the server runs and is reachable, independent of
anything in this repo.

## 3. Ping the server from this project's client

Today this only exercises Status state (no Login, no joining the world yet):

```bash
mvn -pl protocol test -Dtest=StatusPingLiveIT -Dmc.logLabel=status-ping
```

This connects to `localhost:25565`, runs a real Handshake/Status/Ping exchange, and prints the
server's JSON status response plus round-trip time. It's named `*IT` (not `*Test`) specifically so
`mvn verify` never runs it by default — it needs a live server this project doesn't start for you,
so it must not be able to fail a plain build on a machine without one running (see the class's own
javadoc in `protocol/src/test/java/.../StatusPingLiveIT.java`).

## 4. Watch this project's client actually join

```bash
mvn -pl protocol test -Dtest=JoinObservationLiveIT -Dmc.logLabel=join-observation
```

Logs in as `ScoutBot`, drives the full join sequence through Play, prints every unrecognized
Configuration/Play packet ID/length/hex it observed (once per distinct kind — Play alone sends
hundreds of the same unrecognized IDs per second), then disconnects: after ~1 second of quiet
during Login/Configuration (a genuine pause), or a fixed ~2 second window once Play is reached
(Play is continuous — there's no quiet gap to wait for there; see `JoinClientHandler`'s
`scheduleFixedPlayObservationWindow` — the client closes the connection itself once that window
elapses, not the server). Check the server's own console/log for `ScoutBot joined the game` to
confirm it from the server's side too. Same `*IT` naming reasoning as step 3 — needs a live
server, so `mvn verify` skips it by default.

### Client-side logs

`protocol/src/test/resources/log4j2.xml` writes every IT run's log output to both the console and
an hourly file — `{logPath}/yyyy/MM/dd/yyyy-MM-dd-HH-{label}.txt` — written directly at that final
path (no active-file-then-rename-at-rollover step, so a restart mid-hour appends to the one
correctly-named file instead of mixing into a stale "current" file). Defaults to `logs/` under
whichever directory Maven forked the test JVM from (normally the module directory, e.g.
`protocol/logs/...`); override with `-Dmc.logPath=...`. `{label}` defaults to `protocol` — pass
`-Dmc.logLabel=...` (as in the two commands above) so different IT runs don't share one file
within the same hour. `logs/` is already in `.gitignore`.

## 5. Hold a persistent connection open

```bash
mvn -pl protocol test -Dtest=PersistentJoinLiveIT -Dmc.logLabel=persistent-join
```

Runs three separate joins, each under its own username (`KeepAliveTestBot`, `ChunkTestBot`,
`PositionTestBot` — see that class's javadoc for why each needs a distinct one), driving the full
join sequence through Play the same way step 4 does but staying connected instead of disconnecting
after a fixed window: proves the Keep Alive reply keeps the server from timing it out, that real
chunk data arrives, and that the real spawn position decodes and can be reported back — then closes
cleanly on request. This is `MinecraftClient.join`, not `observeJoin` — the entry point a Soldier's
connection actually uses. Same `*IT` naming reasoning as steps 3–4.

## 6. Run two Soldiers that stay connected, build a real world model, and walk

```bash
mvn package -DskipTests
java -jar app/target/app.jar
```

Connects as `Alice` and `Bob` (hardcoded in `SoldierRunner.SOLDIERS` for now - see "Known gaps"),
each on its own connection sharing one `ChunkCache`, and logs each one's status every 5 seconds:
position, current order, how many chunks are loaded, and whether the session is still open. Both
walk toward a short `Order.TrendToward` target in a different direction from their real spawn
position, stopping correctly on arrival or at a genuine obstacle. Reconnects on its own if a
connection dies unexpectedly (see "Known gaps"). Stop it with Ctrl-C, or find and kill the `java`
process filtered by command line — see the caution above (under "Starting it again later") about
not matching by bare process name.
