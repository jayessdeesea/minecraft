---
status: ready
priority: P3
---

# Bump the version pin to 26.3

## Why
Mojang's version manifest lists 26.3 (protocol 777) as the current release, out since
2026-09-15. We're pinned to 26.2 (protocol 776). No present pressure: the only benefit is
watching with a current native client, and the Minecraft launcher can run 26.2 for that. Take
this on when something needs 26.3.

## Scope
Compared with javap against both server jars. The 26.3 jar is in `C:/mc-reference/26.3/`, URL
and SHA-1 below.

- **Packet IDs.**
  - Play clientbound: Add Transient Block, Post Effects and Swing Animation are new, shifting
    104 IDs, including every one we use from Forget Level Chunk onward.
  - Play serverbound: Swing is replaced by Punch, an empty body, which shifts the IDs after it.
  - Configuration clientbound: Post Effects is inserted at index 10, so Update Enabled
    Features goes from 0x0C to 0x0D, Update Tags from 0x0D to 0x0E, and Select Known Packs from
    0x0E to 0x0F.
  - Login and Handshake are unchanged.
- **Wire formats that changed** (narrowed to read and write calls):
  - Accept Teleportation now carries the client's position (3 doubles) and rotation (2
    floats) after the teleport ID.
  - Entity Position Sync replaces its position-and-rotation block with a `PositionPath`, plus
    two floats.
  - Login (Play) and `CommonPlayerSpawnInfo` were rewritten as codec composites. The field
    count matches 26.2, but the order and the game-type encoding need confirming.
  - Chunk Data, Light Data and `LevelChunkSection` changed in bytecode. Not yet narrowed to
    wire-level changes.
  - Update Enabled Features and Update Tags changed. We decode the former.
  - Remove Entities is very likely the same wire format (a VarInt count, then VarInts),
    written as a composite now.
- **Unchanged:** Add Entity, Teleport Entity, Set Health, Player Position, Chunk Batch
  Finished, Forget Level Chunk, Keep Alive, Move Player Pos, Attack, Chunk Batch Received,
  Client Command, Player Loaded, Chat Command, Registry Data, Select Known Packs, Client
  Information, Finish Configuration, `PalettedContainer`, `LpVec3`.
- **Everything else that carries the pin:**
  - `ProtocolVersion.CURRENT`.
  - The Dockerfile's `ARG`s: `https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar`,
    SHA-1 `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c`. The inner jar sits at
    `META-INF/versions/26.3/` in this release.
  - The compose image tag.
  - The generated block and mob lists (DEVELOPMENT.md § 7) and the block state IDs in test
    fixtures.

## Done when
- Unit tests, the live checks (DEVELOPMENT.md § 5) and a full squad run pass against a 26.3
  server.

## Notes
- Starting the 26.3 server converts the dev world in place, and 26.2 can't read it back. Back up
  the `minecraft-data` volume first.
