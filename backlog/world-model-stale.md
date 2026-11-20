---
status: ready
priority: P1
depends-on: []
doctrine: FM 2-0 (intelligence: confirm what's reported)
---

# The world model holds blocks the server no longer has


Seen (field534-535): Eli at (-19.6, 20, 91.5) in his gallery at y=20, sent 2 blocks to a stand at (-18, 20, 90),
failed the walk three times ("expected standing beside the STONE within 400 ticks; it didn't come"). His ground,
kept from his world model (app/src/test/resources/worlds/eli-gallery-field535.json), replayed in the squad
simulation (`RoutingSimulationTest.eliOnHisOwnGroundGetsToHisBranch`): there he walks it. The model and the server
part at that spot - the lead for this item.

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

- the commonest live CANTCO of field148-152 (2026-11-13), "no route to within 0 of" a stand a block
  down and diagonal: at one, (-25, 69, 7), a probe's own fresh chunks showed the stand solid, a
  gatherer having walked at it for a minute. A dig failing at once on a stand read as filled was
  tried and dropped: the farm-in-a-hollow simulation trampled its crop with it. Not yet known
  whether the soldiers' model had the stand open (stale) or solid (the walk's own fault).

- field506: the chunk farm's wheat at (30, 68, 60) and (30, 68, 63), beside its chest at (29, 68, 61), "started on 3
  times and never done" - Olga's CANTCO all morning; stale wheat, or a stand by the chest it can't use.

Block Update (0x08) and Section Blocks Update (0x56) are both decoded.

The other way round too (field326): the model held air at (-11, 65, 3) where the server had
dirt - probed - and Dana's step there was refused nine times, "the model has air PASSABLE there,
grass_block SOIL under it", while she stood at y=66 on it. No stand-in was taken back in the run
("[World] ... stand-in block(s) ... taken back" never logged), so it's a block update missed or
misread, not a placement the model rolled back. Most "refused step at air" lines in `[Blocks]`
(10 in that run) are likely this. A refused step at a cell the model has open could be taken as
the server's word that it's solid, and the model told so. Not every one: a squadmate's body in the cell is refused
too (field333, Bob pillaring into the cell Alice stood in), and a step now waits for a body in its
cell to move rather than being sent ("[Bob] its step at ... waits for Alice to move out of it").

- **Built:** a snag either breaker can't break - the doorway's or the step cutter's - is taken back, every soldier's
  snags kept garrison-wide (`SoldierRunner.takeBackSnag`), and not marked again for two minutes. Before it:
  **a snag marked solid where the server has air was never broken, and never let go.** Live (field443), Pete
  on his stair up at (23, 37, 15) failed "break (23, 37, 16): expected broken within 40 ticks" 46 times in
  five minutes, the dig's break time waited out each time; Bob, boxed in at (43, 55, -10), had 45 cells round
  him made snags in a night ("the model takes it as a block, and breaks it") and broke none. A break the
  server never answers says the model's block isn't there: wanted, after a few unanswered breaks of one
  cell, the cell put back to what the server last sent (or probed), and what really stops the body looked for -
  an entity, a part block the model reads as passable.

## Done when
- The cause is found and a test shows it: which change the server made that the model missed.
- Live, a console sweep of the camp's wall and stockpile matches the model.

## Notes
- Found and fixed (2026-11-13): the one block the client writes itself, a pillar step's cobblestone
  stand-in, stayed when the server refused the placement - no update ever came to replace it.
  Live, two such blocks at the top of a keep builder's strip (15, 85, -16) held every strip's floor
  to y=84 for a day. Now `ChunkCache.standIn`: written over an open cell only, taken back after two
  seconds unconfirmed (`ChunkCacheTest`). The chest, the dirt and the wheat above predate it and
  are something else: a block the server changed with no update the model applied.
- Mitigated (2026-11-13), not explained: a soldier stuck short of the same cell three times - stalled
  at it, or put back by the server - marks it as stone in the model and breaks it
  (`DeterministicSoldier.takeSnag`, the runner's `[World] ... stuck short of ... - the model takes
  it as a block, and breaks it`), so it plans round what it can't see, or through it. Open
  (field164): the third try never comes when, between tries, the squad's order moves to a goal the
  planner finds no route to at all from where it stands (Bob at (-40, 31, 42), ordered to the keep's
  mine 70 blocks off and 25 up: "no route", no riskier route either), so it never walks at the
  cell again; the count should also grow when a plan fails outright with the last snag cell on
  the only way, or the far goal needs the wide search first. The log line
  names the cells: read them against the server for the cause. Live, Bob stood 15 minutes at
  (-40, 31, 42) in the camp's mine, stuck short of (-42, 31, 42) 161 times.
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
- `WorldProbeLiveIT` reads the world as one connection sees it, with the garrison stopped.
- Fixed: a chunk sent again had replaced the cache, and the connections are independent streams -
  a chunk one was sent before a block update could arrive after another's update and undo it. A
  held chunk now keeps its blocks, and a soldier that respawns lets go of its chunks
  (architecture.md). The chest predates the replace, so it isn't the only cause. Ruled out:
  shared section arrays - every palette decodes to a fresh one.
- Not stale: the camp wall's last dirt block, (-40, 64, 39), is dirt on the server too. A probe
  sweep sent 0.3 s apart drops answers: a negative from one proves nothing.
