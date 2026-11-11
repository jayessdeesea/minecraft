---
status: ready
priority: P1
depends-on: []
doctrine: FM 4-0 (field feeding)
---

# Farmland trampled by soldiers coming down onto it

## Why
Live, about half the camp's 113-cell farm stood bare: farmland trampled back to dirt as fast as
it was tilled (a cell that failed planting at 01:19 was dirt by 01:21; console probes). Landing on
farmland from more than half a block tramples it, and stepping off a one-block ledge at a walk
lands up to two blocks on. The camp's farm lies by a pond below the camp, on ground full of
one-block steps, and the farmers walk onto each cell to work it. Food is the earliest open
standard after the wall.

`SimServer` models trampling (a landing from over half a block turns farmland to dirt, logged
"trampled"), so a scenario shows it: a pond in a hollow one block below the ground round it.

## Done when
- A squad farming a pond in a hollow tramples nothing, and the farm keeps most of its cells
  (the scenario asserts both).
- Live, the camp's farm's planted share climbs above half and stays there.

## Notes
- **Tried and reverted:** leaving untilled every cell within 2 blocks of higher ground
  (`FarmPlan.LANDING`). The hollow scenario passed, but live the camp's farm went from 113 cells
  to 1: natural ground by a pond has a one-block step within 2 of nearly every cell. A fix has to
  keep the farm.
- More likely shapes: the soldier's route never drops onto farmland (a step down onto it is
  closed to the pathfinder, as lava is, so it comes in level or not at all); or a farmer works a
  cell from beside it, within reach, rather than standing on it. Probably both.
