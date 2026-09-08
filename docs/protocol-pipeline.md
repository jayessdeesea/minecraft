# The Protocol Pipeline

`protocol` talks to a Minecraft server through a Netty `ChannelPipeline` built from eleven small,
single-purpose handlers instead of a couple of do-everything ones. This document walks through
what each stage does and why it's shaped this way. For the terse, authoritative version of these
decisions, see [AGENTS.md](../AGENTS.md#protocol-pipeline-architecture); this doc is the tour.

## The eleven stages

A diagram was tried here first - Mermaid genuinely can't guarantee "same tier, same row" alignment
across two independent inbound/outbound chains (confirmed by actually rendering several attempts
via `@mermaid-js/mermaid-cli`, not just assumed), so a table replaces it. A table gives that
alignment for free: rows and columns are aligned by construction, no layout engine to fight.

The pipeline splits into two **planes** - a distinction borrowed from networking generally
(control plane vs. data plane), not from OSI layer numbers specifically, since TCP already
collapses OSI's bottom four layers beneath us and doesn't leave a clean seven-way split to map
onto:

- **Data plane** - Framing, Compression, Marshalling. Applies identically to every packet
  regardless of content; never makes a decision.
- **Control plane** - Session management, Protocol reply. Driven by *which* packet arrived and
  *what state* the connection is in; this is where a decision actually gets made.

The control handler (`StatusClientHandler` / `JoinClientHandler` / `PlayClientHandler`) is part of
the control plane, not something outside the pipeline - it's the pipeline's *last* registered
handler. `JoinClientHandler` and `PlayClientHandler` are two separate classes, not one generalized
over a mode flag: they complete their result at different points for different reasons (diagnostic
self-close vs. handing back a persistent `PlaySession`) - see `PlayClientHandler`'s own javadoc.
Only `bot`, past the pipeline entirely, is genuinely "the application" (not wired to a live
connection yet - see AGENTS.md's "Behavior modeling" section).

| Plane | Tier | Type handed to the tier above *(= type received from above, for outbound)* | Inbound | Outbound | Same object (duplex)? |
|---|---|---|---|---|---|
| Application *(outside the pipeline)* | Decision core | - (top of the stack) | `bot`'s `DeterministicSoldier` | not wired to a live connection yet | - |
| Control | Protocol reply | nothing flows up to `bot` yet - `bot` isn't a live consumer of any connection (see the row above); eventually `SoldierStatus`/decoded facts, see AGENTS.md | control handler - decides and sends every reply (Keep Alive included - see the tenets below) | control handler - same object | Conceptually yes; not a registered Netty outbound handler today |
| Control | Session management | typed `Packet`, unmodified (forwarded via `fireChannelRead`) | `SessionStateManager` - owns `ProtocolState`, reacts to state-implying packets, sends nothing | *(none - see below)* | N/A |
| Data | Packet logging (`PacketLoggingHandler`) | typed `Packet`, unmodified (observed, not altered) | logs each decoded `Packet` (excludes `UnknownPacket` - `SessionStateManager` already covers that, deduplicated) | logs each `Packet` pre-encode | **Yes** - `ChannelDuplexHandler`, registered right after `MarshallingEncoder` |
| Data | Marshalling (serialization) | typed `Packet` - the real type change happens here, bytes become a business object | `MarshallingDecoder` - `[packet ID][bytes]` → typed `Packet`, state-dependent | `MarshallingEncoder` - `Packet` → bytes, stateless | No - separate objects |
| Data | Compression | `ByteBuf` - one frame's decompressed payload (`packet ID` + body) | `CompressionDecoder` - applies Data Length + zlib once enabled | `CompressionEncoder` - mirrors it | No - separate objects, but see below |
| Data | Frame stats (`FrameStatsHandler`) | `ByteBuf`, unmodified - one complete frame, boundaries known, content still opaque | counts frames received, logs connect/disconnect | counts frames sent | **Yes** - `ChannelDuplexHandler`, registered right after the idle watchdog |
| Data | Idle watchdog (`IdleStateHandler` + `ConnectionWatchdogHandler`) | `ByteBuf`, unmodified - one complete frame; the pair only observes *timing between* frames, never their content | Netty's built-in `IdleStateHandler` fires a `READER_IDLE` event if no frame arrives within the configured window; `ConnectionWatchdogHandler` reacts by closing the channel | `IdleStateHandler` sits in the outbound chain too (a `ChannelDuplexHandler`) but writer-idle detection is disabled (0), so it passes writes through untouched; `ConnectionWatchdogHandler` is inbound-only and drops out of the outbound chain entirely | `IdleStateHandler`: yes, one instance, both directions (writer-idle side unused). `ConnectionWatchdogHandler`: N/A, inbound-only |
| Data | Framing | `ByteBuf` - one complete frame, boundaries known, content still opaque | `FrameDecoder` - strips the VarInt length prefix | `FrameEncoder` - prepends it | No - separate objects |
| Transport *(outside the pipeline)* | Socket | raw bytes - a TCP stream has no message boundaries at all | TCP socket | TCP socket | Yes - one physical connection |

Notes worth keeping precise, since sloppy naming here is exactly what this table is meant to avoid:

- **"Framing," not "datagram."** TCP is a stream protocol with no message boundaries of its own -
  a datagram implies a self-delimited unit, which is what UDP gives you for free and TCP doesn't.
  Framing exists specifically to impose boundaries on that stream via the length prefix
  (docs/protocol-spec.md's framing section); its output is still just bytes with known edges, not
  yet anything with meaning.
- **Compression is the one data-plane tier that's genuinely symmetric.** `CompressionEncoder` needs
  the same `setCompressionThreshold()` call `CompressionDecoder` does, because one raw byte stream
  either is or isn't compressed regardless of direction. `MarshallingEncoder` needs no equivalent
  call - it's stateless, because encoding is never ambiguous the way decoding is (a raw packet ID's
  meaning depends on which state you're in; a `Packet` object already knows what it is).
- **Session management is inbound-only by protocol design, not by omission.** Every mutation it
  triggers is implied by a *server-sent* packet (`SetCompressionPacket`, `LoginSuccessPacket`,
  `FinishConfigurationPacket`) - nothing about our own outbound writes ever needs to imply a state
  change, so there's nothing on that side to react to. `SessionStateManager` is also the only
  handler that reaches into others (`ctx.pipeline().get(X.class).setY(...)`) - nothing else in the
  pipeline does this, and it shouldn't grow more of these without a real reason.
- **The control plane's outbound side is currently self-triggered, not externally called.** The
  control handler reads a packet and reacts by writing a reply *itself* (e.g. reading
  `LoginSuccessPacket` triggers writing `LoginAcknowledgedPacket`) - nothing outside it calls it
  today. `bot` isn't a live caller yet either: the control handler's only real destination right now
  is a log line and a completed `CompletableFuture` (see `JoinObservationLiveIT`), not a soldier.
  See AGENTS.md's "Behavior modeling" section for what a real `bot`-triggered write path will look
  like once one exists.
- **Registering all eleven real handlers in this one order** produces the correct order in *both*
  directions, because Netty walks outbound handlers in the reverse of their registration order - a
  packet the control handler writes travels marshaller → compressor → framer → socket, the mirror
  of how inbound bytes arrive.

## Walking through a join

Calling `MinecraftClient.observeJoin(host, port, username)` does nothing yet except register a
`ChannelInitializer` and start connecting. The interesting part starts once the socket is actually
up:

1. **`channelActive` fires** — this is the one moment with no packet to react to yet, so it's the
   only place a control handler acts without being told to. `JoinClientHandler.channelActive`
   sends `HandshakePacket`, then makes the *one* explicit cross-handler call in this whole design:
   `sessionState(ctx).transitionTo(ctx, LOGIN)` — because Handshake has no response at all, so
   nothing will trigger this transition on its own.
2. **Bytes come back.** `FrameDecoder` buffers until it has one complete frame.
   `CompressionDecoder` passes it through (still disabled). `MarshallingDecoder` reads the packet
   ID and asks whichever `PacketMarshaller` is *currently installed* — `LoginMarshaller`, because
   of the swap in step 1 — to decode it.
3. **`SessionStateManager` sees it first.** If it's `SetCompressionPacket`, it flips the
   compressor on right there — no send involved, no decision, just a mutation. If it's
   `LoginSuccessPacket`, it transitions to `CONFIGURATION` (swapping the marshaller again) *before*
   forwarding the packet on. Either way, it calls `ctx.fireChannelRead(packet)` unconditionally.
4. **The control handler reacts.** It never asks what state it's in to decide *what* to do —
   `LoginSuccessPacket` unambiguously means "send Login Acknowledged and Client Information,"
   regardless of state. It only queries `SessionStateManager.state()` for one thing: whether to
   keep resetting its own completion deadline.
5. **Repeat through Configuration, reach Play.** `KnownPacksPacket` gets echoed back empty;
   `FinishConfigurationPacket` gets acknowledged and (via `SessionStateManager`) flips the
   marshaller to `PlayMarshaller`.
6. **Completion.** A quiet gap (Login/Configuration — a genuine burst-then-pause) or a fixed
   window (Play — continuous traffic, no natural gap to wait for) completes the `CompletableFuture`
   and closes the channel.

`MinecraftClient.join` (via `PlayClientHandler`) walks the identical steps 1–5 - same Handshake,
same Login/Configuration replies, same marshaller swaps - and only diverges at step 6: reaching
Play completes the `CompletableFuture<PlaySession>` immediately, and the channel stays open
indefinitely (replying to Keep Alive) instead of closing on a quiet-gap/fixed-window timer.

## Why split it this far — the reasoning behind the table above

- **Data plane handlers make zero decisions.** Framing, Compression, and Marshalling behave
  identically regardless of packet content - the same category as an HTTP client transparently
  handling gzip content-encoding. This module's very first cut bundled "frame+compress" and
  "decode+dispatch" into two bigger handlers instead; that collapsed the distinction and made each
  piece harder to test in isolation.
- **Control plane handlers make decisions, but never business ones.** `SessionStateManager` and
  the control handler decide *protocol* things (compress now, transition state, send this reply) -
  never *game* things (view distance, movement, respawn policy). They split further along a real
  seam:
  - *Mutate* (`SessionStateManager`) — `SetCompressionPacket` needs no reply at all, just an
    internal flip. Sends nothing, ever.
  - *Send* (control handler) — `LoginSuccessPacket` needs both a mutation *and* a reply. Only this
    half ever calls `ctx.writeAndFlush`.
- **The application plane is `bot`, entirely outside this pipeline** - driven by a deterministic
  per-tick loop with no LLM in it (see AGENTS.md's latency architecture). One acknowledged
  violation to watch: `ClientInformationPacket.defaults()` is hardcoded in the control handler
  today - application content sitting in control-plane code, tolerated only because nothing yet
  needs to vary it.

## Tenets for the control plane - specifically, the protocol reply handler

`SessionStateManager`'s rule is simple enough to state once (owns `ProtocolState`, never sends).
The control handler's side - `StatusClientHandler` / `JoinClientHandler` / `PlayClientHandler` -
has accumulated enough of its own rules across this design's evolution that they're worth naming
explicitly, not left scattered across commit history and prose:

1. **Dispatch by packet type, never by current state.** A packet's type alone must fully determine
   what gets sent back - `instanceof LoginSuccessPacket` → send Login Acknowledged + Client
   Information, unconditionally. If producing the right reply ever needed a state check instead of
   just the packet's type, that would mean the packet type itself is underspecified - the fix is a
   richer type, not a state-gated `if`.
2. **Never mutate shared session state - that's `SessionStateManager`'s exclusive territory.** The
   control handler may *read* `SessionStateManager.state()` for its own narrow, local bookkeeping
   (today: whether to keep resetting its completion deadline), but it must never be the thing that
   flips `ProtocolState`, touches `compressionThreshold`, or swaps the active marshaller. Reading is
   fine; writing crosses a boundary that belongs one tier down. (Worth doing at some point: shrink
   `SessionStateManager.transitionTo` from `public` to package-private, and expose a narrowly-named
   entry point - e.g. `beginHandshake(ctx, nextState)` - for the one legitimate external caller, so
   the exception is visible in the API itself rather than only in a comment.)
3. **A reply must be fully self-contained - zero external/business input required.** This is the
   same test that draws the control-plane/application-plane line above: "does replying require any
   input from outside `protocol`?" `ClientInformationPacket.defaults()` being hardcoded here today
   is the one acknowledged violation of this tenet, not an exception to it. Initiation direction
   doesn't matter for this test, which is easy to get backwards - Status's `PingPongPacket` is
   client-initiated (sent in reaction to `StatusResponsePacket`, itself sent from `channelActive`
   with nothing to react to yet) while Play's Keep Alive is server-initiated (the server sends
   `KeepAliveRequestPacket`; `PlayClientHandler` echoes `KeepAliveResponsePacket` back unchanged),
   but both are protocol-mandated with zero degrees of freedom once the connection is already open. The decision that actually has real freedom - "should we open a
   Status connection to this server at all, right now" - happens one layer up, outside `protocol`
   entirely, before either handler is ever constructed.
4. **Today's writes are all reflexes; nothing external calls this handler to originate one.** Every
   `ctx.writeAndFlush()` here happens in direct reaction to a packet just read (or, for Handshake /
   Status Request / Login Start, to the connection just opening, the one moment with nothing to
   react to yet). Half-realized now: `PlaySession` exists and is the explicit interface `protocol`
   exposes outward (never `bot` reaching into the control handler or any Netty type directly, per
   the module boundary that confines `io.netty` imports to this module alone) - but it's still
   read-only (`playState()`/`isOpen()`/`close()`). The reflex-only property stays true until
   `PlaySession` grows a way to *originate* something (movement, chat) rather than just observe and
   close.
5. **Diagnostic self-closing behavior is a test artifact, not a shape to preserve.**
   `JoinClientHandler` closing the connection after a fixed window exists because it's a diagnostic
   tool proving the join sequence works. Resolved by addition, not replacement: `PlayClientHandler`
   is a separate control handler for the persistent case, and `JoinClientHandler` stays exactly as
   it was - the self-closing shape was never generalized, it just stopped being the only option.
6. **Prefer enumerating what applies, not what's excluded, when a state check is genuinely
   needed.** A first cut had the control handler reset its completion deadline whenever state was
   anything other than `LOGIN` - which also matched `PLAY`, so every one of Play's ~1,000+
   packets/2s kept re-canceling the fixed observation window and the test timed out. Fixed by
   checking for `CONFIGURATION` specifically. Negation-based state checks are fragile against states
   added later; Play being continuous traffic with no natural quiet gap is exactly the kind of thing
   a "not X" check silently doesn't account for.

Tenets 1, 2, 3, 5, and 6 are true of the code as it stands today - persistent connections now
exist (`PlayClientHandler` / `MinecraftClient.join`), and tenet 5 describes how that was actually
built. Tenet 4 is half-realized: the interface (`PlaySession`) exists, but it can't yet originate
anything - the remaining constraint is on what `PlaySession` grows into next.

## Static structure, swappable delegates — not pipeline surgery

`CompressionDecoder`/`Encoder` and `MarshallingDecoder`/`Encoder` are *always* present in the
pipeline. What changes over a connection's life is an internal field — `compressionThreshold`, or
`MarshallingDecoder`'s active `PacketMarshaller` — set via a narrow setter `SessionStateManager`
calls on each transition, never Netty's `ChannelPipeline.replace()`/`addAfter()`. Three reasons:
the pipeline's shape is knowable by reading `MinecraftClient.addPipeline()` once, rather than
tracing every place that might mutate it at runtime; each handler with a swappable delegate is
trivially unit-testable in isolation; and it sidesteps real subtleties in `ByteToMessageDecoder`
around leftover buffered bytes when a decoder is replaced mid-stream. The rule for when you'd
actually need real pipeline surgery instead: a stage's fundamental *contract* would need to
differ, not just its behavior. Decompression is always "`ByteBuf` in, `ByteBuf` out"; marshalling
is always "packet ID + bytes → `Packet`" — neither stage here ever needs to change shape, only
behavior.

## One marshaller class per state, not one generic table

`HandshakeMarshaller`, `StatusMarshaller`, `LoginMarshaller`, `ConfigurationMarshaller`, and
`PlayMarshaller` are five small classes rather than one class parameterized by a `Map`. This isn't
about who tracks state — `SessionStateManager` owns that regardless — it's that a named class is
a discoverable home for "everything that can arrive during this state," matching how
[docs/protocol-spec.md](protocol-spec.md) is itself organized section-by-state.
`HandshakeMarshaller.decode()` throws unconditionally: nothing is ever clientbound during
Handshake, so an actual invocation would mean something genuinely anomalous happened, not a
missing case to quietly add.

## Everything unrecognized is logged, not swallowed

Every marshaller falls back to `UnknownPacket(id, rawBytes)` for anything not in its table, and
`SessionStateManager` logs that at WARN — once per distinct (state, packet ID) kind, since Play
alone can repeat the same unrecognized ID hundreds of times a second. Configuration's IDs were
reverse-engineered this exact way: decode as `Unknown`, log the hex, read it by hand, add a real
type once confirmed. See [protocol-spec.md § 3.4](protocol-spec.md) for the worked example, and
`PlayLoginPacket` for Play's first one — found by searching captured hex for the server's own
reported entity ID. `PlayState` is the seed of the world-model layer this feeds - see its own
javadoc for why that's deliberately a small, growing shape rather than a big one designed upfront.

## Resource-exhaustion guards terminate, they don't degrade

Never let a wire-supplied length or count size an allocation without a cap. Applies uniformly:
`ProtocolCodec.readString`/`MAX_STRING_LENGTH`, `CompressionDecoder`'s Data Length check against
`MAX_FRAME_LENGTH` (the "compression bomb" guard), and `ProtocolCodec.readVarIntCount` for any
count-prefixed list (used by `KnownPacksPacket` today). The same principle will apply to NBT's
nesting *depth* once that's implemented — the recursive case of the same guard, not implemented yet
because nothing we decode today recurses.

An oversized frame, a compression bomb, or (once implemented) a nesting-depth violation all
terminate the connection outright rather than skip-and-continue — see
[protocol-spec.md § 4.3/§ 8](protocol-spec.md) for the full reasoning, and
[AGENTS.md's no-fallbacks tenet](../AGENTS.md) for why that's a deliberate, general policy and not
specific to this pipeline.

**The idle watchdog (`IdleStateHandler` + `ConnectionWatchdogHandler`) is the same guard, keyed on
time instead of size.** A connection that's gone quiet for longer than a Keep Alive interval should
allow (30s - double vanilla's ~15s cadence, as margin) is a violated invariant exactly like an
oversized frame is: something that's supposed to be bounded (time between packets; frame length)
has exceeded its bound, and the correct response is the same - terminate outright, don't guess at
whether the connection is "probably still fine." `PlayClientHandler.channelInactive` is what turns
that termination into a signal a caller can act on: `PlaySessionListeners.onDisconnect` fires for
this closure (and any other unannounced one) but not for a caller's own deliberate
`PlaySession.close()` - see that record's javadoc and `PlayClientHandler`'s `INTENTIONAL_CLOSE`
channel attribute for how the two are told apart. `protocol` stops at signaling; reconnecting is an
`app`-level decision (see `SoldierRunner.onDisconnected`), same rationale as `ClientInformationPacket`
staying out of the control plane - deciding *whether and how* to rejoin needs input `protocol` has
no business holding.
