# Minecraft Protocol Specification

## Overview

The Minecraft Java Edition protocol is TCP-based, uses a binary packet format, and supports
optional compression and encryption. This document specifies the wire format and connection
state machine we implement in the `protocol` module — see [AGENTS.md](../AGENTS.md) for why it's
hand-rolled on Netty rather than using a third-party protocol library.

This is a wire-format and state-machine spec, not a Java design doc: it deliberately does not
prescribe interface names or class shapes for our implementation. Those are ordinary code design
decisions to make when writing `protocol`, governed by the module boundaries in AGENTS.md, not
something to pre-bake into a spec document.

**Version pin:** see [AGENTS.md § Target environment](../AGENTS.md#target-environment) for the
current protocol version. Not duplicated here to avoid two documents drifting on a number that
changes over time.

## 1. Data Types

### 1.1 Primitive Types

#### VarInt
- Variable-length signed 32-bit integer
- Range: -2³¹ to 2³¹-1 (-2,147,483,648 to 2,147,483,647)
- Maximum length: 5 bytes
- Encoding:
  1. Take the least significant 7 bits of the number
  2. Set the Most Significant Bit (MSB) to 1 if more bytes follow, 0 if this is the last byte
  3. Shift the number right by 7 bits (unsigned/logical shift)
  4. Repeat until the remaining value is 0
- Decoding:
  1. Start with `result = 0`, `position = 0`
  2. For each byte read: `result |= (byte & 0x7F) << position`
  3. If `(byte & 0x80) == 0`, this was the last byte — stop
  4. Otherwise `position += 7` and read the next byte
- Examples:

  | Value | Encoded (hex) |
  |------:|--------------:|
  |     0 |            00 |
  |     1 |            01 |
  |   127 |            7F |
  |   128 |         80 01 |
  |   300 |         AC 02 |

#### VarLong
- Variable-length signed 64-bit integer
- Range: -2⁶³ to 2⁶³-1
- Encoding/decoding: same principle as VarInt, extended to 64 bits
- Maximum length: 10 bytes

#### Boolean
- A single byte: `0x00` = false, `0x01` = true. Any other value should be treated as true on read.

#### Byte / Unsigned Byte
- 8-bit signed: -128 to 127. Unsigned: 0 to 255.

#### Short / Unsigned Short
- 16-bit, big-endian. Signed: -32,768 to 32,767. Unsigned: 0 to 65,535.

#### Int
- 32-bit signed, big-endian. -2,147,483,648 to 2,147,483,647.

#### Long
- 64-bit signed, big-endian. -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807.

#### Float / Double
- 32-bit / 64-bit IEEE-754, big-endian.

#### String
- UTF-8 bytes prefixed with their length as a VarInt (length is in bytes, not characters)
- Maximum length: 32767 bytes

### 1.2 Compound Types

#### Position
- A block position packed into a single Long
- `((x & 0x3FFFFFF) << 38) | ((y & 0xFFF) << 26) | (z & 0x3FFFFFF)`

#### UUID
- 128-bit UUID encoded as two Longs (most-significant bits first), 16 bytes total

#### NBT (Named Binary Tag)
- Used for inventory items, entity metadata, and other structured data
- Full format is its own spec to write when we implement the packets that carry NBT — not
  reproduced here since nothing early in the connection lifecycle (Handshake/Status/Login) needs
  it

## 2. Packet Format

### 2.1 Uncompressed Structure
```
[Length][Packet ID][Data]
  ^        ^         ^
  |        |         +-- Payload bytes
  |        +------------ Packet identifier (VarInt)
  +--------------------- Total size of (Packet ID + Data), as VarInt
```

### 2.2 Compressed Structure
Once compression is enabled (see § 4), every packet uses this structure instead:
```
[Length][Data Length][Packet ID][Data]
  ^          ^            ^        ^
  |          |            |        +-- Payload bytes
  |          |            +----------- Packet identifier (VarInt)
  |          +------------------------ Uncompressed size of (Packet ID + Data), as VarInt
  +----------------------------------- Total size of everything after this field, as VarInt
```
- If Data Length is `0`, the packet body is uncompressed (below the compression threshold)
- If Data Length is `>0`, (Packet ID + Data) is zlib-compressed, and Data Length is the
  decompressed size — verify decompressed output matches it

### 2.3 Reading and Writing

**Reading:**
1. Read `Length` (VarInt), then read exactly that many bytes into a buffer
2. If compression is enabled: read `Data Length` (VarInt); if `>0`, decompress the rest and
   verify the decompressed size matches
3. Read `Packet ID` (VarInt) from the (now-decompressed) buffer
4. Read the remaining fields per that packet's definition

**Writing:**
1. Write `Packet ID` and packet-specific data into a buffer
2. If compression is enabled and the buffer exceeds the threshold: compress it and write the
   decompressed size as `Data Length`; otherwise write `Data Length = 0`
3. Write the final `Length` prefix (VarInt) for everything that follows it
4. If encryption is enabled: encrypt everything **except** the `Length` prefix (see § 5)

## 3. Protocol States

Five states gate which packets are valid at any point in the connection. State only ever moves
forward — there's no going back, and no skipping:

```
              ┌──────────────┐
              │ Handshaking  │
              └──────┬───────┘
                      │
            ┌─────────┴─────────┐
            ▼                   ▼
     ┌──────────────┐    ┌──────────────┐
     │    Status    │    │    Login     │
     └──────────────┘    └──────┬───────┘
                                 │
                                 ▼
                          ┌──────────────┐
                          │ Configuration│
                          └──────┬───────┘
                                 │
                                 ▼
                          ┌──────────────┐
                          │     Play     │
                          └──────────────┘
```

Valid transitions: `Handshaking → Status`, `Handshaking → Login`, `Login → Configuration`,
`Configuration → Play`. Anything else (skipping a state, going backward, or `Status ↔ Login`) is a
protocol violation — disconnect. (Play can also transition back to Configuration for a
server-initiated "reconfigure," but we don't implement that until something actually needs it.)

### 3.1 Handshaking
Single packet, sent immediately on connect, that picks the next state.

| Field | Type | Notes |
|---|---|---|
| Protocol Version | VarInt | |
| Server Address | String | hostname/IP used to connect — servers can use this for virtual-host routing |
| Server Port | Unsigned Short | |
| Next State | VarInt | `1` = Status, `2` = Login |

### 3.2 Status
Used for the server-list ping shown in the multiplayer menu.

| Direction | Packet | Payload |
|---|---|---|
| C→S | Status Request | (empty) |
| S→C | Status Response | JSON: version, player count/sample, description, optional favicon |
| C→S | Ping Request | Long (opaque, echoed back) |
| S→C | Ping Response | same Long as the request |

### 3.3 Login
Authenticates the player and (optionally) sets up encryption before moving to Configuration.

| Step | Direction | Packet | Payload |
|---|---|---|---|
| 1 | C→S | Login Start | player name, optional UUID |
| 2 | S→C | Encryption Request *(optional — see § 5)* | server ID, RSA public key (DER), 4-byte verify token |
| 3 | C→S | Encryption Response *(optional)* | RSA-encrypted shared secret, RSA-encrypted verify token |
| 4 | S→C | Set Compression *(optional — see § 4)* | compression threshold |
| 5 | S→C | Login Success | UUID, username |
| 6 | C→S | Login Acknowledged | (empty) — switches the connection to Configuration |

Encryption Request/Response happens only in online mode; our dev setup targets an offline-mode
server (see AGENTS.md), so this exchange is skipped entirely there but must still be implemented
correctly since it's part of the wire protocol.

### 3.4 Configuration
Sits between Login and Play (applies to 1.20.2+, which includes our pinned version). The server
pushes synchronization data — registry data, tags, feature flags, known resource packs/"packs,"
cookies — and the client acknowledges. Ends when the server sends Finish Configuration and the
client replies with Acknowledge Finish Configuration, which switches the state to Play.

**Packet IDs below are not from any written spec** — none of the usual references
(wiki.vg-successor, minecraft.wiki) were consulted for this section; they were derived by
connecting an instrumented client to our own real, running 26.2 dev server, decoding raw payload
bytes by hand, and observing which packet the server's send sequence stalls on until we reply.
Confirmed this way, against 26.2 specifically — clientbound unless noted:

| ID | Packet | How it was identified |
|---|---|---|
| `0x01` | Plugin Message | payload decodes as two length-prefixed strings: `"minecraft:brand"`, `"vanilla"` |
| `0x07` | Registry Data | ~30 packets of this ID, 67–32,084 bytes each, arrived immediately after our Known Packs reply |
| `0x0C` | Feature Flags | payload decodes as VarInt count `1` + string `"minecraft:vanilla"` |
| `0x0D` | Update Tags | one 35,203-byte packet, arrived last in the registry/tag burst |
| `0x0E` | Select Known Packs | payload decodes as VarInt count `1` + one (namespace, id, version) triple `(minecraft, core, 26.2)`; server sent nothing further until we replied |
| `0x03` | Finish Configuration | empty-body packet; arrived once we'd sent Client Information, and nothing followed until we acknowledged it |
| `0x00` (serverbound) | Client Information | unconfirmed by content — inferred from commonly-documented numbering; confirmed working: sending it is what unblocked Registry Data/Update Tags/Finish Configuration at all |
| `0x07` (serverbound) | Known Packs (reply) | same as above — sending an empty list here is what unblocked Registry Data/Update Tags |
| `0x03` (serverbound) | Acknowledge Finish Configuration | same inference; confirmed working: sending it after Finish Configuration is what put the server into Play (see below) |

**Registry Data (`0x07`) is now actually decoded**, not just identified by size - `RegistryDataPacket`
reads the registry name and every entry's identifier (each entry's own NBT payload is skipped, not
parsed - see that class's javadoc for why). Cross-checked a second, independent way beyond the
by-content method above: decompiling the real server jar's `ConfigurationProtocols` puts this
packet at clientbound registration index 7, and the same list's index 14 lands on `Select Known
Packs` - matching this table's own already-confirmed `0x0E` exactly. Confirmed live against the dev
server: all ~30 registries synced during a real join decode cleanly with plausible names and entry
counts (`minecraft:worldgen/biome` 66 entries, `minecraft:dimension_type` 4, `minecraft:damage_type`
51, etc.) - see DEVELOPMENT.md's "Confirming wire formats against the real server" for the method.

**Play was reached and confirmed end to end.** After acknowledging Finish Configuration, the
server's own log showed:
```
ScoutBot[/127.0.0.1:59369] logged in with entity id 19 at (4.5, 81.0, 42.5)
ScoutBot joined the game
```
— the same broadcast a real player join produces. `JoinClientHandler` replies to Keep Alive but
still disconnects a couple of seconds later (no movement, no persistent session) — it's a
diagnostic tool that proves the join sequence works, not yet a Soldier. Play's packet catalog is
specified separately, in § 3.5.

### 3.5 Play
Main gameplay state — the large majority of packet types live here. Specified incrementally, per
packet, as we implement the features that need them; not enumerated in this document as a whole.

**This table is the authoritative list of confirmed Play packet IDs** - add a row here when a
packet is confirmed; the packet class's javadoc carries its field layout. IDs are positions in the
server's own `GameProtocols` registration lists (read with `javap` - see DEVELOPMENT.md's
"Confirming wire formats against the real server"); each new one is accepted only once
previously-confirmed IDs in the same list land where expected.

| Dir | ID | Packet | Class | Confirmation |
|---|---|---|---|---|
| ← | `0x01` | Add Entity | `AddEntityPacket` | registration order; live (real entities decode with zero trailing bytes) |
| ← | `0x0B` | Chunk Batch Finished | `ChunkBatchFinishedPacket` | registration order; live (chunks keep streaming once acked) |
| ← | `0x23` | Entity Position Sync | `EntityPositionPacket` | registration order; live |
| ← | `0x25` | Forget Level Chunk | `ForgetLevelChunkPacket` | registration order |
| ← | `0x2C` | Keep Alive | `KeepAliveRequestPacket` | by content: 8-byte payload ~15s into Play |
| ← | `0x2D` | Chunk Data and Update Light | `ChunkDataPacket` | decompiled layout; live across many chunks |
| ← | `0x31` | Login (play) | `PlayLoginPacket` | by content: entity ID matched the server console's "logged in with entity id N", other fields matched server.properties |
| ← | `0x35` | Move Entity Pos | `MoveEntityPacket` | registration order; live (resolved positions continuous) |
| ← | `0x36` | Move Entity Pos Rot | `MoveEntityPacket` | registration order; live |
| ← | `0x41` | Player Chat | `PlayerChatPacket` | registration order; live (plain chat from another client) |
| ← | `0x48` | Player Position | `PlayerPositionPacket` | registration order; live spawn coordinates |
| ← | `0x4D` | Remove Entities | `RemoveEntitiesPacket` | registration order |
| ← | `0x12` | Container Set Content | `InventoryPackets` | registration order; live |
| ← | `0x14` | Container Set Slot | `InventoryPackets` | registration order; live (`/give`) |
| ← | `0x68` | Set Health | `SetHealthPacket` | registration order; live (`0.0` for a dead player) |
| ← | `0x69` | Set Held Slot | `InventoryPackets` | registration order |
| ← | `0x7D` | Teleport Entity | `EntityPositionPacket` | registration order |
| → | `0x00` | Accept Teleportation | `AcceptTeleportationPacket` | registration order; server keeps the session after it |
| → | `0x01` | Attack | `AttackPacket` | registration order |
| → | `0x07` | Chat Command | `ChatCommandPacket` | registration order; live (`/me` lines in the server log) |
| → | `0x09` | Chat | `ChatPacket` | registration order; live (relayed to other clients as Player Chat) |
| → | `0x0B` | Chunk Batch Received | `ChunkBatchReceivedPacket` | registration order; live |
| → | `0x0C` | Client Command | `ClientCommandPacket` | registration order; live respawn |
| → | `0x1C` | Keep Alive | `KeepAliveResponsePacket` | wiki + live round-trip |
| → | `0x1E` | Move Player Pos | `MovePlayerPosPacket` | registration order; live movement |
| → | `0x2C` | Player Loaded | `PlayerLoadedPacket` | registration order; empty body |
| → | `0x3F` | Swing | `SwingPacket` | registration order |

**Chat.** Player Chat is decoded only as far as the sender UUID and the message, which come
before the NBT text component and the registry-bound chat type; the rest of the frame is left
unread. In offline mode a player's UUID is `UUID.nameUUIDFromBytes("OfflinePlayer:" + name)`, so
the sender identifies the player without decoding player info. Plain chat from a player without a
chat session arrives as Player Chat with no signature. An unsigned chat *command* such as `/me` -
the squad's own radio lines - arrives instead as Disguised Chat (`0x21`), whose only sender is a
name inside an NBT component; it isn't decoded.

`0x00` clientbound is the bundle delimiter: vanilla brackets related packets (e.g. an entity spawn
and its metadata) in a pair of them but still sends each inner packet as its own frame, so it's
safe to leave as `UnknownPacket`.

Login (play) fields decoded: entity ID, hardcore flag, the 3 dimension names, max players, view/simulation
distance, reduced-debug-info/respawn-screen/limited-crafting flags, dimension type + name, hashed
seed, game mode + previous game mode, debug/flat flags, has-death-location flag. Trailing fields
(portal cooldown and later) aren't decoded yet - see `PlayLoginPacket`'s javadoc. This seeds
`PlayState` (in the `protocol` package), the start of a world-model layer that will grow one
confirmed field at a time, the same way this table will grow one confirmed packet at a time.

**Item stacks** (inside the inventory packets) - VarInt count; if positive, VarInt item ID and a
component patch: VarInt added count, VarInt removed count, each added component as VarInt type
then its value, each removed one as VarInt type. A component's value has its own codec and no
length prefix, so `ItemStacks` decodes only the ones it knows - `max_damage` (2), `damage` (3),
`repair_cost` (19) as VarInts and `unbreakable` (4) as empty - and on any other type keeps the
item, drops the rest of that packet (the frame is length-isolated, so this is safe), and logs the
type. Enchantments (13) are the first such gap seen live.

## 4. Compression

### 4.1 Activation
A `Set Compression` packet, sent during Login before Login Success, carries the threshold as a
VarInt:
- `-1`: compression disabled
- `0`: every packet compressed
- `n > 0`: packets whose (Packet ID + Data) exceeds `n` bytes are compressed; recommended default
  is `256`

Once set, **every** subsequent packet in both directions uses the compressed structure (§ 2.2),
even the ones that end up with `Data Length = 0`.

### 4.2 Algorithm
- zlib (DEFLATE with zlib wrapper), 15-bit window, default compression level and strategy
- Compress: combine Packet ID + Data, deflate, write the pre-compression length as Data Length
- Decompress: inflate, and verify the result's length matches Data Length exactly

### 4.3 Guardrails
- Enforce a maximum compressed **and** maximum decompressed size per packet — an attacker-supplied
  Data Length with a small compressed payload that inflates to gigabytes ("compression bomb") must
  be rejected, not decompressed and then discarded
- **On violation: terminate the connection, never skip-and-continue.** This applies even though a
  compression-bomb frame's exact byte extent is already known (the outer frame length told us) and
  skipping *could* be done without desyncing the stream. We still don't, for two reasons: (1) if
  we skip it, we're left missing whatever it was carrying while the server assumes we have it — an
  unverified, silently-degraded state, which is exactly the kind of thing to avoid; (2) if the cap
  turns out to be wrong (a legitimately large packet, not an attack), the fix is to raise the cap
  deliberately once that's understood, not to add a path that quietly absorbs whatever exceeds it.
  See AGENTS.md's no-fallbacks tenet.
- An **oversized raw frame length** (before compression or packet type is even known) has no safe
  skip option at all, not just an undesirable one: our length-prefixed framing has no
  resynchronization mechanism (unlike, say, newline-delimited text, where you can scan forward for
  the next boundary). Guessing how many bytes to discard risks desyncing the entire rest of the
  connection — every later packet would be silently misinterpreted rather than cleanly failing.
  Terminate immediately.
- This is different from an **unrecognized packet ID** (`UnknownPacket`, § 3.4): that's a known,
  catalogued gap in *our* implementation, not a violated invariant, and it's provably safe to skip
  (the frame decoder already isolated its exact bytes — no guessing) and loud (logged). Continuing
  past that is legitimate; continuing past a guard violation above is not.

## 5. Encryption

### 5.1 Packets

| Direction | Packet | Fields |
|---|---|---|
| S→C | Encryption Request | Server ID (String — empty in offline mode), Public Key (byte array, DER-encoded RSA), Verify Token (byte array, 4 random bytes) |
| C→S | Encryption Response | Shared Secret (byte array, RSA-encrypted), Verify Token (byte array, RSA-encrypted, echoing the request's token) |

### 5.2 Key Exchange
1. Server generates an RSA key pair (2048-bit minimum) and sends the public key + a random verify
   token
2. Client generates a 16-byte shared secret
3. Client RSA-encrypts the shared secret and the verify token with the server's public key, sends
   both back
4. Server RSA-decrypts both, confirms the verify token matches what it sent, then both sides
   derive their AES state from the shared secret
5. From this point every packet body (not the Length prefix) is AES-encrypted independently in
   both directions

### 5.3 Cipher Configuration
- Algorithm: AES/CFB8, no padding (stream cipher mode — padding isn't applicable)
- Key size: 128 bits, taken directly from the 16-byte shared secret
- IV: the shared secret itself (same bytes, reused as the IV — this is a real, if unusual,
  property of this protocol, not a mistake to "fix")
- Java: `Cipher.getInstance("AES/CFB8/NoPadding")`, `KeyPairGenerator.getInstance("RSA")` with a
  2048-bit initialization and a `SecureRandom`

### 5.4 What's Unencrypted
The `Length` VarInt prefix (§ 2.1/2.2) is **never** encrypted, in either the plain or compressed
packet structure — only the bytes after it are.

## 6. Error Handling

| Situation | Required behavior |
|---|---|
| Packet type invalid for current state | Disconnect, log for security monitoring |
| State transition not in the valid set (§ 3) | Disconnect, log for security monitoring |
| Protocol version mismatch at Handshake | Disconnect with a message describing the version requirement |
| Malformed packet (bad length, truncated, bad UTF-8, etc.) | Disconnect immediately |
| Decompression or decryption failure | Disconnect immediately — never attempt to resync mid-stream |

## 7. Implementation Guidelines

### 7.1 Threading
- Handle packets asynchronously per Netty's normal event-loop model
- Preserve packet order within a single connection/state — this protocol has no per-packet
  sequence numbers to reorder by

### 7.2 Buffers
- Prefer pooled/direct `ByteBuf`s over repeated allocation for hot-path packet types
- Release buffers promptly after a packet is fully processed

## 8. Security Considerations

- Validate every length field (VarInt lengths, String lengths, array lengths) against a sane
  maximum **before** allocating or reading based on it
- Enforce the compression guardrails in § 4.3, including their termination policy — never
  skip-and-continue past a violated guard, only past a known-and-tracked gap (§ 3.4)
- **Nested or wide structures aren't caught by a single count cap alone**: a per-level count
  cap (`ProtocolCodec.readVarIntCount`) doesn't stop many individually-small counts multiplying
  into something enormous (map of map of map). The defense is a budget tracked across the whole
  packet's decode, and exceeding it terminates the connection. Today's one recursive decoder,
  `Nbt`, only skips, and every element it skips consumes at least one byte of an already
  length-capped frame, so its total work is bounded by the frame size. Its depth isn't, so it
  enforces vanilla's own limit of 512 (`Nbt.MAX_DEPTH`). A decoder that materialises values
  needs a cumulative element budget as well.
- Use `SecureRandom` for the verify token and the client's shared secret — never `Random`
- Rate-limit packets per connection; a hostile or buggy peer sending packets far faster than the
  protocol expects should be disconnected, not processed indefinitely

## 9. Version Compatibility

Protocol version is negotiated once, in the Handshake packet, and is not renegotiated mid-connection.
We target a single pinned version — see AGENTS.md — and do not implement multi-version
compatibility (no per-version packet ID tables, no negotiation fallback).
