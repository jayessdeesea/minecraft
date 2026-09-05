# World Data Format (Client-Side)

## Scope

This document specifies **only** the wire/data format our `protocol` module needs to *decode*
what a Minecraft server sends about the world — chunk data, block states, and light values.

It deliberately does **not** cover world generation, world storage/serialization, redstone or
game-rule/dimension management, or light *propagation*. Those are server-authoritative concerns:
we're building a client that connects to a self-hosted vanilla server (see AGENTS.md), not a
server. The vanilla server owns the world; our soldiers just need to understand what it tells
them about it.

## 1. Chunks

A chunk is a 16×16-block column. In current versions it spans 384 blocks vertically (Y from -64
to 319), divided into 24 **sections** of 16×16×16 blocks each. Each section independently stores:
- Block states (via a palette — see § 2)
- Block light and, in the Overworld only, sky light (see § 3)
- Biome data (palette-indexed, same general shape as block states, coarser resolution)

### Palette Types
A section's block-state palette is one of:
1. **Single Value** — every block in the section is identical; store one state ID, no array
2. **Linear** — a small number of distinct states; store a small array of state IDs and index
   into it with a bit-packed per-block index
3. **Global** — many distinct states; skip indirection and store the global state ID per block
   directly

### Bit-Packed Block Arrays
- 4096 blocks per section (16³)
- Each block's palette index (or global state ID, for the Global palette) is packed at a
  fixed bit-width between 4 and 15 bits, chosen per-section based on how many distinct values
  that section needs
- Packed values are stored back-to-back across an array of 64-bit longs (no padding between
  values that straddle a long boundary)

### What the Client Needs
Decode enough of this to know, for any `(x, y, z)` in a loaded chunk, its current block state ID
— that's the input to pathing/mining/combat decisions in the `bot` module. We do not need to
retain a full authoritative world model beyond what's useful for that; the server remains the
source of truth and will send updates as blocks change.

## 2. Block States

A block state is a specific combination of a block type and its property values.

- **Block type**: a namespaced identifier, e.g. `minecraft:stone`
- **Properties**: optional key/value pairs that create variants of a block type, e.g.
  `facing: north|south|east|west`, `waterlogged: true|false`, `age: 0..15`. Property value kinds:
  enumerations, booleans, and bounded integer ranges.
- **State ID**: a single integer assigned per distinct (block type, property values)
  combination — this is what actually travels on the wire (§ 2.1)

### 2.1 Network Format
A block state is transmitted as a single VarInt (the global state ID). Block entities that carry
extra data (e.g. a sign's text, a chest's don't-actually-here-inventory) attach NBT alongside the
state ID where the packet definition calls for it — see the protocol spec's NBT note.

### 2.2 Text Format (for logging/debugging only, not wire format)
```
minecraft:stairs[facing=north,half=top,waterlogged=false]
```

### What the Client Needs
A registry mapping state ID → (block type, properties) for whatever blocks matter to bot
decision-making (walkable vs. solid, hazardous, minable, etc.) — populated from the server's own
registry data sent early in the Play state, not hand-authored by us. We do not need a state
*generation* system (computing all N combinations and assigning IDs ourselves) — the server has
already done that and tells us the results.

## 3. Light Data

Two independent light channels, each a 4-bit value (0–15) per block:
- **Sky light** — from the sun/moon; Overworld only; affected by time of day; blocked by opaque
  blocks
- **Block light** — emitted by light-source blocks; not affected by time; blocked by opaque blocks
  (some materials, e.g. water, absorb extra levels per block)

### Storage Format
Per section: two 2048-byte arrays (one for sky light, one for block light), each holding 4096
nibbles — one nibble per block in the section, two nibbles packed per byte.

### What the Client Needs
Only the ability to **read** these values as sent by the server — e.g. for a soldier deciding
whether a spot is dark enough for hostile mobs to spawn. We do not implement light propagation or
recalculation; that's the server's job, and it will send updated light data when it changes.
