---
status: ready
priority: P1
depends-on: []
doctrine: FM 3-34 (survivability)
---

# The closed wall's last dirt blocks never get their stone

## Why
"The camp walled in" is the earliest open standard, and has been for days: the wall is closed,
one block of it dirt. FORTIFY sends its masons to replace it, and a mason with no cobblestone is
sent to draw some from the stockpile or quarry it (`Mason.repoint`, military-doctrine.md § 15).
Live, neither happened: the camp's ground work - filling hollows, digging the high ground - ranks
above the quarry and the mason for a soldier's hands (`Hands.Routine`), and was never done, its
last cells out of reach. A soldier that did quarry stone filled the camp's hollows with it.

## Done when
- Live, "the camp walled in" is met. (Built: one soldier is the wall's, off ground work -
  `oneSoldierQuarriesForTheWallWhileTheRestFillTheCamp`; military-doctrine.md § 15.)

## Notes
- **Tried and reverted:** taking every mason off ground work while the wall had dirt - the
  slope scenario, with no stone to be had, never levelled; and filling with dirt only while the
  wall wanted stone - the slope's soldiers carried only cobblestone, and filled nothing.
- The stockpile chest (-47, 70, 45) is boxed in by an old hut's blocks; the miners' stone never
  reaches it to be drawn (`cells.md`, the floor's inspection).
