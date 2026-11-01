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
- 4096 block-state entries per section (16³); biomes use the same scheme at a coarser 64 entries
  (4×4×4 cells)
- Bits-per-entry, confirmed against the real (decompiled) server source
  (`net.minecraft.world.level.chunk.Strategy`): for block states, 0 bits = Single Value, 1-8 bits =
  Indirect (a small palette array), 9+ bits = Direct (no palette, global state IDs stored inline);
  for biomes, the same three categories at 0 / 1-3 / 4+ bits
- Packed values never straddle a 64-bit long: each long holds `floor(64 / bitsPerEntry)` values,
  packed starting at the least-significant bits; any leftover high bits in a long go unused
  (padding), rather than splitting one value across two longs
- **The packed long array has no length prefix on the wire** - its size is `ceil(entryCount /
  valuesPerLong)`, computed independently by both sides, not transmitted. (An earlier version of
  this project's decoder assumed a length-prefixed array here, matching a different, genuinely
  length-prefixed long-array type used elsewhere in the same packet - that mismatch broke live
  decoding; see DEVELOPMENT.md's "Known gaps" for the fix and how it was found.)

### What the Client Needs
Decode enough of this to know, for any `(x, y, z)` in a loaded chunk, its current block state ID
— that's the input to pathing/mining/combat decisions in the `bot` module. We do not need to
retain a full authoritative world model beyond what's useful for that; the server remains the
source of truth and will send updates as blocks change. **Implemented**: `protocol`'s
`ChunkDataPacket`/`PalettedContainer` decode exactly this, confirmed live against the dev server.

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
decision-making (walkable vs. solid, hazardous, minable, etc.). This mapping is *not* sent over the
network at all, by Registry Data or any other packet — it's generated per-version by the vanilla
server's own data generator (`java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar
--reports`, producing `generated/reports/blocks.json`), and a real client bundles that mapping
internally, matching whatever version it targets. Registry Data (Configuration state) syncs a
different set of data-driven registries (`dimension_type`, `biome`, `damage_type`, etc.) - useful,
but it does not answer "what does state ID N mean."

**Implemented**: `bot.world.BlockClassRegistry` sources exactly this way -
`tools/block-class-generator/GenerateBlockClasses.java` (checked in, run manually, not part of the
Maven build) reads the pinned server jar's own `blocks.json` and classifies every block state by its
`definition.type` (its real internal behavior class) and properties (`waterlogged`, `open`) into
passable, solid, tall (1.5-high collision: fences, walls, closed gates) or hazard (liquids,
waterlogged and underwater blocks, fire, powder snow), emitting a plain resource file `ChunkCache`
loads at startup. Deliberately not exhaustive - an unlisted type defaults solid, the same "unknown
= blocked" policy used for unloaded chunks.

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
