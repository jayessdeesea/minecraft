---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camp layout, perimeter sectors); FM 3-90 (sectors of responsibility)
---

# Cells: the camp as a grid of chunk-aligned, fenced, lit compounds

## Why
The camp today is one ring of dirt wall eight blocks out from its centre (docs/military-doctrine.md
§ 15), plus each squad's hut. It doesn't grow, and outside the ring is dark ground that spawns
monsters right up to it. The operator expected something else: the ground divided along chunk
boundaries into cells, each levelled, fenced, gated and lit, with open sky over the moat between
them. Monsters don't spawn on the lit ground, and any that do burn at dawn. The camp grows by
adding cells outward.

## Design sketch
- **Cells are chunk-aligned.** Fences run along chunk boundaries, the unit the server loads and
  the chunk cache keys on. A cell is one chunk or a block of them. The cell at the camp's centre is
  the keep; the ring round it is the lodgment.
- **Lit with the fewest torches, on fence posts.** A torch on every few fence posts along the
  boundary, and one on a single fence post in the middle of each cell. That's the least light that
  keeps every floor cell above block light 0, the only light monsters spawn at.
- **The moat:** the lit, open ground between and round the cells, under clear sky, so anything
  that does spawn in the night burns at dawn. Nothing is built over it.
- **Level at sea level.** Ground flattened to y=63 (feet at 64): cut high ground, fill low ground
  with what was cut. That makes the moat easy to keep open and the ground easy to walk.
- **Fenced and gated:** fence on each cell's edges, with a fence gate on each side that has a
  neighbouring cell. Spiders still climb fences, so the keep keeps its wall.
- **A purpose for each cell:** keep, barracks (beds), farm, pumpkins and melons, a pen (sheep for
  wool and beds, cows for food), tree farm, mine head. The general picks a purpose. Laying it out
  is deterministic, like `Formations`.
- **Ownership:** each squad owns cells. The general assigns cells to squads with a purpose, and
  the squad leader's `Task` is that cell's next unmet standard. Owner, task and standard are data
  that the soldiers' routines read, never prose.
- **Checked from the world**, like the patrol base, and reported cell by cell in the dawn AAR.

## What it costs
- **Wood** is the bottleneck. 64 fence posts round a chunk, at 3 fences per 4 planks and 2 sticks,
  is about 30 logs a cell. The tree farm (§ 12) has to supply it.
- **Torches:** coal or charcoal.
- **Levelling** to y=63 on uneven ground can be thousands of blocks a chunk. The ground by the
  water is closest to it.
- It replaces the ring wall as FORTIFY's work. The keep's existing wall stays, as the keep's.

## Done when
- The keep and the 8 cells round it are lit, fenced, gated and level at y=63, each with a purpose,
  and the dawn AAR reports them cell by cell.
- No monster spawns inside the fence line through a night, measured over a multi-day garrison run.

## Notes
- **Needs a decision (operator):** where the keep goes. The operator keeps a main base near
  (0, 63, 0), within 100 blocks in each direction, because it's easy to find. The camp is now at
  (-48, 44), with a wall 113 of 170 blocks built. Moving it there starts the keep again.
- Depends on wood that grows back (backlog/renewables.md), and on torches made from charcoal or
  mined coal (backlog/mining.md).
