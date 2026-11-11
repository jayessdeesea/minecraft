---
status: proposed
priority: P2
depends-on: []
doctrine: ATP 3-34.81 (engineer reconnaissance; standard routes)
---

# Stairwells: one shape, at the centre of a chunk

## Why
The way up or down is wherever a staircase could be sited: the 3x3 spiral round a pillar
(docs/military-doctrine.md § 16), within 12 blocks of the order, moved off water and caves. Each
one is somewhere new, and the steps a soldier cuts or builds to get round an obstacle are
somewhere else again. The operator can't find them, or follow them in-game; the squads' own
record of them is per staircase. A standard place and a standard shape make every way up or down
legible: the operator walks to a chunk's centre and it's there, and a soldier reaching a chunk
knows where its stairwell is or would be.

## Done when
- **One shape.** A stairwell is a 2x2 shaft centred in its chunk - cells 7-8 of the chunk in x and
  z - with stairs round it going straight up or down, never a straight dig up or down (a soldier
  never digs the block it stands on, nor the one over its head). Stairs for now; ladders or
  scaffolding are another item.
- **Sited by chunk, not by search.** The mine at an objective uses the stairwell of the objective's
  chunk. The camp's mine is its chunk's stairwell.
- **An obstacle doesn't move it.** A cave, a drop or lava in the stairwell's column: the diggers
  find another way down - a staircase beside it, or the cave itself - and cut or build the
  stairwell's missing stretch in its own column from below or from the side, so the shape stays
  whole. Lava is never dug into; it's walled off or the stretch is left, and reported.
- **A kept structure:** the stairwell's column, every height of it, is one every dig, fill and
  pave skips, kept on disk wherever it is - not only the camp's (backlog/cells.md, the floor's
  inspection).
- A squad-simulation scenario for each: a clear column, a cave across it, lava beside it, a
  stairwell already part-cut.

## Notes
- `bot.supply.Staircase` today is a 3x3 spiral round one pillar, a block down a step, lit as it's
  cut, persisted for the camp (`WallStore` kind `stairs`); its siting (`Staircase.site`, 12 blocks)
  is what this replaces. backlog/mining.md's levels would hang off the stairwell.
- 2x2 with stairs round it means a spiral one cell wide outside the 2x2 core - a 4x4 footprint -
  or the core itself as the stairs. Which, and whether the core is open (a drop through the middle)
  or solid (a pillar), is the design's first question.
