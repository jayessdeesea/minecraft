---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camp layout, perimeter sectors); FM 3-90 (sectors of responsibility)
---

# Cells: the camp as a grid of chunk-aligned, fenced, lit compounds

## Why
The camp is one ring of dirt wall round its centre plus each squad's hut (docs/military-doctrine.md, the camp). It
doesn't grow, and outside the ring is dark ground that spawns monsters right up to it. The operator expects the
ground divided along chunk boundaries into cells, each levelled, fenced, gated and lit, with open sky over the lit
ground between them, the camp growing by adding cells outward. Only the production chunks are laid out that way
today (`bot.farm.ChunkFarm`, `TreeChunk`), and a mine's way down is sited by search, somewhere new each time.

## What's left

### The cell grid
- **Chunk-aligned.** A cell is one chunk or a block of them, fenced on the chunk lines with a gate on each side that
  has a neighbouring cell; the fence, gate and post-torch layout is backlog/renewables.md's (the layout).
- **Lit with the fewest torches**: one on every few fence posts and one on a post in the middle of each cell - the
  least that keeps every floor cell above block light 0.
- **The open ground** between and round the cells: lit, under clear sky, nothing built over it, so anything that
  spawns burns at dawn. No trench round it (operator, 2026-11-20: no moat).
- **Level at the plan's height**, never a measured one: sea level, ground top y=63, feet 64. The levelling itself is
  backlog/camp-to-sea-level.md's and backlog/flatten-campaign.md's.
- **A purpose for each cell**: barracks (beds), farm, pumpkins and melons, a pen (sheep for wool and beds, cows for
  food), tree farm, mine head, supply point. The platoon leader picks the purpose; the cell is a blueprint laid out
  and inspected (backlog/echelon-contracts.md). The farm, tree and supply-point cells are backlog/renewables.md's.
- **Checked from the world**, like the patrol base, and reported cell by cell in the dawn AAR.
- **Sizes** (operator, 2026-11-11): a foothold minimal; an outpost one chunk at sea level unless there's a practical
  reason not to; the main base 2x2 chunks at its centre.
- Which squad works which chunks, and which soldier which part, is backlog/authority-and-missions.md's (step 3, areas
  of operations; step 4, sectors).

### The mine-head cell's stairwell
- **One shape:** a 2x2 shaft centred in its chunk - cells 7-8 in x and z - with stairs round it, never a straight dig.
  Open first question: the stairs a one-cell spiral outside the core (a 4x4 footprint) or the core itself, and the
  core open or a pillar. Today's is a 3x3 spiral round one pillar (`bot.supply.Staircase.site`).
- **Sited by chunk, not by search:** the mine at an objective uses its chunk's stairwell. The camp's mine already
  moves between chunk centres (`app.CampMineSites`); its first head and every other mine don't.
  backlog/mining.md's levels hang off it.
- **An obstacle doesn't move it:** a cave, a drop or lava in the column - the diggers cut or build the missing stretch
  in its own column, so the shape stays whole. Lava is never dug into; it's walled off or the stretch left, and
  reported.

### Kept structures
- A stairwell is a kept structure every dig, fill and pave skips, at every height of its column, kept on disk
  wherever it is. The one list is `SoldierRunner.groundExcept`; a mine away from the camp is on disk only while it's
  the camp's, so after a restart another's cells aren't protected.
- A cell's floor is inspected like a wall: a crater is filled and paved again, in stone straight away rather than
  dirt then a second trip, and never over a mine head or stairwell.

### Climbing
- **Bamboo scaffolding** (operator, 2026-11-12: preferred over dirt): climbed inside and sneaked down, so it wants
  its own moves in the route planner; bamboo in any loaded chunk said as a sighting; bamboo a plant in the squad
  simulation's growth model; none seen near the camp (2026-11-21), so grown by hand first.
- **A pillar's stub**: a soldier stranded up its own pillar digs it down (`Recovery`) but leaves the last block or
  two and the step beside them; its episode held until the stub is dug too.
### The floor
- Operator, 2026-11-11: getting about a camp is a measure of whether its abstractions are right. Every
  base - foothold, outpost, main base - has a level floor at one height, of stone, not dirt, and an ideal route round it
  with no jumping; within about 90% of that is good, and it's iterated on. A floor isn't left with potholes: it's
  inspected like the wall, and a crater (a creeper's) is filled and paved again.

## Done when
- The camp's cells are lit, fenced, gated and level at one height, each with a purpose, and the dawn AAR reports them
  cell by cell.
- No monster spawns inside the fence line through a night, measured over a multi-day garrison run.
- A squad-simulation scenario for each stairwell case: a clear column, a cave across it, lava beside it, and a
  stairwell already part-cut.

## Notes
- **Decided (operator, 2026-11-22): both, in order** (backlog/sites.md, the order of building). The camp by the water
  is a temporary base while the food and wood reserves are built up; the permanent structure at the origin comes
  once supply is mature. So the keep's wall against walkers, fence against spiders, ramps with a gate at the top of
  each and ring of 12 chunks (level ground at y=63, x and z from -16 to 16, `command.Keep`, operator 2026-11-15) are
  this item's cells at the origin, later; until then the grid's first cells are the templated farm chunks by the
  camp, and what the temporary base itself is - fenced cells or less - isn't settled.
- Cost a cell: about 30 logs for 64 fence posts (3 fences per 4 planks and 2 sticks), torches from coal or charcoal,
  and levelling that can be thousands of blocks a chunk on a hillside. Depends on wood that grows back
  (backlog/renewables.md) and coal (backlog/mining.md).
