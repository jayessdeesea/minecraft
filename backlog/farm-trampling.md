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
- A squad farming a pond in a hollow tramples nothing. (Built: a route never steps down onto
  farmland - `Pathfinder`; `aFarmInAHollowIsFarmedWithoutTramplingIt` tramples one cell where it
  trampled many. Left: a soldier stepping up out of the hollow off farmland that falls back lands
  on it from a jump's height - forbidding the step up would shut a farmer in a hollow with no
  level way out.)
- Live, the camp's farm's planted share climbs above half and stays there.

## Notes
- **Tried and reverted:** leaving untilled every cell within 2 blocks of higher ground
  (`FarmPlan.LANDING`). The hollow scenario passed, but live the camp's farm went from 113 cells
  to 1: natural ground by a pond has a one-block step within 2 of nearly every cell. A fix has to
  keep the farm.
- Built since: the farm's earthwork cuts an apron round the plot to its level, so its cells are
  walked onto on the flat (docs/military-doctrine.md § 12). If trampling stays high live, the next
  shape is a farmer working a cell from beside it, within reach, rather than standing on it.
- **Open (2026-11-12): tills refused by one soldier, not the ground.** Live (field123), with the
  model and the server agreeing on each cell - grass under open air - every refused till was
  Fran's (6 cells) or Alice's (3), each cell three tries; squadmates' tills went through. Fran
  carried a wooden hoe (the server's own `data get entity`). Not yet known why.
- **Open (2026-11-12): farmers crowd one cell.** Live (field138), two farmers of one squad stood
  on one spot with the same stand as their goal, their escorts a block off, and each reported
  "no route ... to within 0" for two minutes: the route is fine (the probe reaches the cell from
  either side), but bodies push, the soldier makes no progress, and each re-plan counts as a
  failure. Claims are by plot cell; what's left is to keep any two farmers' stands - and an
  escort's place - apart.
