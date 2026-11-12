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
- ripe wheat at (-53, 63, 57) - air over farmland on the server (field109, within an hour of a
  fresh start, so not a chunk kept from before): three farmers tried to harvest it 168 times,
  most of that run's "harvests", the walk to it failing each time.

Block Update (0x08) and Section Blocks Update (0x56) are both decoded, and the only block the
client writes itself is a pillar step's cobblestone (`SoldierRunner`, `takePlacement`).

## Done when
- The cause is found and a test shows it: which change the server made that the model missed.
- Live, a console sweep of the camp's wall and stockpile matches the model.

## Notes
- Built: a chunk sent again is compared with the one kept, and each difference logged
  (`[World] a chunk sent to ... differs from the one kept at N blocks, e.g. [x, y, z, kept, sent]`).
  A difference that a block update on its way explains is short-lived; one in a chunk kept for
  long is the stale case. What's left: read the live `[World]` lines for which blocks and what
  changed them, then the cause and its test. The camp's chunks are held the whole time and never
  sent again, so for them the runner logs its wall, the soil and the two cells over it of the camp's farm and
  each soldier's own, and the stockpile chest, as the model holds them every 10 minutes (`[Sweep]`), for a console sweep against the server (`execute
  unless block`, which speaks only on a mismatch). Ruled out as well: Section Blocks Update's
  coordinates (sign-extended), and a reconnect (its chunks are let go, as on a respawn).
- First look: what removed each block and how the server told the clients - a dig by one of our
  soldiers (its own block-changed ack and update), an explosion, an enderman, water. A sweep of
  the camp comparing the model to the server, on a schedule, would say how often it happens.
- Open lead (2026-11-12): tills refused on a squad's FARM plot by (-61, 62, 50), the model
  holding the cell open, the server showing cobblestone two high over it - stale, or put down
  after; the sweep now covers those plots to tell.
- `WorldProbeLiveIT` reads the world as one connection sees it, with the garrison stopped.
- Fixed: a chunk sent again had replaced the cache, and the connections are independent streams -
  a chunk one was sent before a block update could arrive after another's update and undo it. A
  held chunk now keeps its blocks, and a soldier that respawns lets go of its chunks
  (architecture.md). The chest predates the replace, so it isn't the only cause. Ruled out:
  shared section arrays - every palette decodes to a fresh one.
- Not stale: the camp wall's last dirt block, (-40, 64, 39), is dirt on the server too. A probe
  sweep sent 0.3 s apart drops answers: a negative from one proves nothing.
