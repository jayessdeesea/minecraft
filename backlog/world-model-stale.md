---
status: ready
priority: P1
depends-on: []
doctrine: FM 2-0 (intelligence: confirm what's reported)
---

# The world model holds blocks the server no longer has

## Why
The shared `ChunkCache` sometimes shows a block the server doesn't, or misses one it has, in a chunk a soldier has
held the whole time. The symptoms are worked round - a stand-in taken back, a snag marked and taken back
(docs/military-doctrine.md, `ChunkCache.standIn` and the snag; docs/architecture.md) - but which change the server made that the model missed is
not known, and no test shows it.

## Open symptoms (each probed on the console, `execute if block`)
- A chest at (-48, 69, 44), air on the server: a soldier failed to open it six times running.
- Ripe wheat at (-53, 63, 57), air over farmland on the server (field109, within an hour of a fresh start): 168
  harvest tries by three farmers.
- Air at (-11, 65, 3) where the server had dirt (field326): Dana's step there refused nine times; no stand-in was
  taken back in the run, so an update missed or misread. Most "refused step at air" lines in `[Blocks]` are likely this.
- Dirt at (-82, 63, 87) read as open: chosen as a stand, "no route" to it for hours.
- Not yet probed: the chunk farm's wheat at (30, 68, 60) and (30, 68, 63) beside its chest, "started on 3 times and
  never done" all morning (field506) - stale wheat, or a stand by the chest it can't use.

## What's left
- **The cause.** Read the live `[World] a chunk sent to ... differs from the one kept` lines and the `[Sweep]` lines
  (the camp's wall, farm soil and stockpile chest as the model holds them, every 10 minutes) against the server
  (`execute unless block`, which speaks only on a mismatch), for which blocks and what changed them: a dig by one of
  ours, an explosion, an enderman, water. Then its test.
- **A refused step at a cell the model has open** taken as the server's word that it's solid, and the model told so -
  but not when a squadmate's body is in the cell (a step already waits for it to move).
- **The snag count when the soldier never walks at the cell again** (field164: Bob stood 15 minutes at (-40, 31, 42),
  stuck short of (-42, 31, 42), his order moved to a goal with no route at all): the count (`Mover.SNAG_TRIES`) should
  also grow when a plan fails outright with the last snag cell on the only way, or the far goal needs the wide search.
- **After a break the server never answers**, look for what really stops the body - an entity, a part block the model
  reads as passable - beyond taking the snag back (`SoldierRunner.takeBackSnag`).

## Done when
- The cause is found and a test shows it: which change the server made that the model missed.
- Live, a console sweep of the camp's wall and stockpile matches the model.

## Notes
- **Ruled out:** Block Update (0x08) and Section Blocks Update (0x56) not decoded (both are; the latter's coordinates
  sign-extended); a reconnect or respawn (its chunks are let go); shared section arrays (every palette decodes to a
  fresh one); a chunk sent again replacing a held one (a held chunk keeps its blocks); the pillar step's stand-in
  (taken back after two seconds unconfirmed) - the chest, the dirt and the wheat predate both fixes.
- **Reading old evidence:** till commit 934 (2026-11-20) a "no route ... to within N of (x, y, z)" CANTCO named each
  end's centre rounded half up, so a probe of the block it named probed a neighbour: "a stand a block down and
  diagonal" in older runs is that rounding, not a stale model.
- **Probing:** a probe sweep sent 0.3 s apart drops answers - a negative from one proves nothing. `WorldProbeLiveIT`
  reads the world as one connection sees it, with the garrison stopped. A dig failing at once on a stand read as
  filled was tried and dropped: the farm-in-a-hollow simulation trampled its crop with it.
