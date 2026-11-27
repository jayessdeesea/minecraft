---
status: ready
priority: P1
---

# A stair floor cell that isn't solid but isn't a cave

## Why
A mineshaft's stairs stop for good at a floor cell that holds a torch (or anything passable that no block replaces),
and nothing moves them on. Live (field749-750, the (-2,-2) mineshaft): step 22's floor (-26,41,-23) held a torch over
stone; the crew reported "no floor under step 22 ... an open cave below - not floored: 24 blocks put under it already"
all day at 21 of 112 steps, the miners below waited on it, and the squad held the same order.

The chain, read from the code:
- `Miner.hazard` (bot, ~969) reads any floor that isn't solid as an open cave.
- `Miner.bridge` (~782) floors it with `Footing.place`, which puts against the face below without clearing the cell;
  `Footing.placeClearing` clears first - a torch or flower in the cell refuses the block.
- `bridged` counts ticks the soldier stands on the cell (`merge` each call), not blocks that land, and is keyed by
  `cut.tunnel()` - -1 for the whole stairs - so `BRIDGE_TRIES` (24) burns in about a second, once a run, for every
  step of the shaft at once.
- After it, `Progress.Stopped` with nothing retried; `SoldierRunner` (~4702) pauses a mine only on "water or lava
  beside step", by matching the reason's text. The same order again breaks "A retry changes something"
  (docs/conventions.md).

## Done when
- A `MinerTest` row: a step's floor cell holds a torch over stone - the stairs go on (the cell cleared and floored,
  or read as floored), not Stopped. Written first with today's answer (24 places, Stopped).
- A `MinerTest` row: a floor cell where the block never lands stops after a count of blocks that didn't land at that
  cell, not ticks; another cell's floor has its own count.
- A `MineSimulationTest` scenario: a torch in a stair floor cell; the mission advances (or, if it can't, ends PAUSED
  with the reason said).
- A stairs stop that can't be floored pauses the mission as a flooded step does - by a typed reason, not a string
  match - and the pause rule in docs/echelons/platoon-leader/decisions.md (the mineshaft's "Its stairs stopped ... PAUSED") names it.
- Live: the (-2,-2) mineshaft past step 22.

## Notes
- Not proven live: that the server refuses the block because of the torch. The first row settles it; a console
  `execute if block -26 41 -23 minecraft:torch` checks the world.
- Whose torch: the crew holds two Staircase plans on one head (252 and 256 cuts, 112 and 116 steps, bottoms y=-48 and
  -52), and another torch sits 8 below at (-26,33,-23) - likely the other plan's lighting in this one's floor. Worth a
  look while in here; two plans on one shaft is its own fault if confirmed.
- Jack's "Mining step=1 of 252" is the wait branch's `Progress.Working(0, cuts.size())` - a wait, not progress - and
  Liam's "no route ... to (-25,31,-23)" is below the same stop.
- The pause rule's wording is the operator's to approve (it changes what the platoon leader does).
- Related: backlog/mining.md's gallery-into-a-cave and cave-with-no-blocks items (other cases); backlog/one-home.md
  A2 (reasons compared as text).
