---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camp layout, perimeter sectors); FM 3-90 (sectors of responsibility)
---

# Cells: the camp as a grid of chunk-sized, fenced, lit compounds

## Why
The camp today is one ring of dirt wall eight blocks out from its centre (docs/military-doctrine.md
§ 15), plus each squad's hut. It doesn't grow, and outside the ring is dark ground that spawns
monsters right up to it. The operator expected something else: each chunk a cell - levelled,
fenced, gated and lit - so the lit ground itself keeps monsters off, and the camp grows by adding
cells outward.

## Design sketch
- **A cell is a chunk** (16x16, chunk-aligned), the same unit the server loads and the chunk cache
  keys on. The cell at the camp's centre is the keep; the ring of cells round it is the lodgment.
- **A cell's standard,** checked from the world like the patrol base's:
  - **Lit:** no floor cell at block light 0, the only light monsters spawn at. A torch lights a
    circle about 7 blocks across to light 1 or more, so about 9 torches in a grid per cell.
  - **Fenced:** fence on the cell's edges, with a fence gate on each side that has a neighbouring
    cell. Spiders still climb fences, so the keep keeps its wall.
  - **Level:** no step higher than one block inside the fence, so every cell is walkable. Levelled
    by cutting high ground and filling low ground with what was cut, not flattened to one height:
    a whole chunk on uneven ground is thousands of blocks.
  - **Purpose:** what the cell is for - keep, barracks (beds), farm, pumpkins and melons, a pen
    (sheep for wool and beds, cows for food), a tree farm, the mine head. The general picks a
    purpose. Laying it out is deterministic, like `Formations`.
- **The lit outer cells are the moat.** Monsters spawn only on dark ground 24 or more blocks from
  every player, so a ring of lit cells pushes the spawns past the fence line.
- **Ownership:** each squad owns cells. The general assigns cells to squads with a purpose, and
  the squad leader's `Task` is that cell's next unmet standard. Owner, task and standard are data
  that the soldiers' routines read, never prose.

## What it costs
- **Wood** is the bottleneck. 64 fence posts round a cell, at 3 fences per 4 planks and 2 sticks,
  is about 30 logs a cell. The tree farm (§ 12) has to supply it.
- **Light:** about 9 torches a cell, so coal or charcoal.
- It replaces the ring wall as FORTIFY's work. The keep's existing wall stays, as the keep's.

## Done when
- The keep and the 8 cells round it are lit, fenced, gated and level, each with a purpose, and the
  dawn AAR reports them cell by cell.
- No monster spawns inside the fence line through a night, measured over a multi-day garrison run.

## Notes
- Depends on wood that grows back (backlog/renewables.md), and on torches made from charcoal or
  mined coal (backlog/mining.md).
