---
status: ready
priority: P1
depends-on: []
doctrine: FM 2-0 (intelligence: confirm what's reported)
---

# The world model holds blocks the server no longer has

## Why
Three times in one day the shared `ChunkCache` showed a block the server didn't (console probes,
`execute if block`), each in a chunk some soldier had loaded the whole time:
- a chest at (-48, 69, 44) - air on the server; a soldier walked its loads to it and failed to
  open it six times running;
- dirt at (-82, 63, 87) - the server's dirt, read as open: soldiers chose it as a stand and
  reported "no route" to it for hours (the reverse case);
- the camp wall's last "dirt" block at (-61, 64, 44) - air on the server, with air below it: the
  wall has a hole there, the camp's standard reads it closed with one block of dirt, and
  FORTIFY's repointing can never reach it. "The camp walled in" has been open on it for days.

A chunk sent again now replaces the cached copy (architecture.md), which fixed none of these:
the chunks are never sent again while a soldier keeps them loaded. Block Update (0x08) and
Section Blocks Update (0x56) are both decoded, and the only block the client writes itself is a
pillar step's cobblestone (`SoldierRunner`, `takePlacement`).

## Done when
- The cause is found and a test shows it: which change the server made that the model missed.
- Live, a console sweep of the camp's wall and stockpile matches the model.

## Notes
- First look: what removed each block and how the server told the clients - a dig by one of our
  soldiers (its own block-changed ack and update), an explosion, an enderman, water. A sweep of
  the camp comparing the model to the server, on a schedule, would say how often it happens.
- `WorldProbeLiveIT` reads the world as one connection sees it, with the garrison stopped.
- Fixed: a chunk sent again had replaced the cache, and the connections are independent streams -
  a chunk one was sent before a block update could arrive after another's update and undo it. A
  held chunk now keeps its blocks, and a soldier that respawns lets go of its chunks
  (architecture.md). The chest predates the replace, so it isn't the only cause. Ruled out:
  shared section arrays - every palette decodes to a fresh one.
