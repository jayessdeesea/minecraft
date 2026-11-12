---
status: ready
priority: P1
depends-on: []
doctrine: ATP 4-41 (class supply point: a place soldiers can get to)
---

# A soldier carrying a load to the stockpile walks to the chest itself

## Why
Live (field102), "can't reach the stockpile" was most of a day's CANTCOs - 53 to 60 each for
Alice, Bob, Carol and Gus. `Depositor.standAt()` is the chest's own cell, solid, so the walk's
goal is a cell nobody can stand in. It only succeeds when the route happens to bring the
soldier within reach on the way. The camp's chest at (-47, 70, 45) has solid blocks on three
sides and below, with old huts' blocks round it, and the workshop's own stand (-47, 69, 44)
probes solid too. So no route ends within reach, and every load stays in the soldiers' packs,
the miners' stone among them, which the wall's repointing wants.

## Done when
- Live, "can't reach the stockpile" CANTCOs fall to near none. (Built: a stockpile trip walks to
  the nearest cell within reach of the chest the soldier can stand in and get to -
  `SoldierRunner.chestStand`.)
- With no such cell, its CANTCO says the chest is shut in, so the camp's upkeep or the squad
  leader can open it up.
- A scenario that fails without the stand: in the open the walk's partial route ends within
  reach anyway, so none does yet. Live, the chest at (-47, 70, 45) has open stands beside it at
  (-47, 70, 44) and (-48, 70, 44), and a way in from the west.

## Notes
- `Depositor.tick` has no world; `SoldierRunner.stockpileFor` does, and can hand the stand with
  the chest.
- What buried the workshop's stand is open: the workshop's cells are kept out of the camp's
  ground work (`campKeep`); an old hut's wall is the likelier cause (`cells.md`).
