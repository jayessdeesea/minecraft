---
status: in-progress
priority: P1
doctrine: JP 3-18 (the lodgment developed); ATP 3-34.40 (site preparation); FM 4-0 (supply-point distribution)
---

# The camp to sea level: a new camp first, the stores moved, the old hill cut down

## Why
The operator (2026-11-20): the camp is flattened gradually to sea level, its chests with it; a stockpile
that can't be reached has a new one made, scaffolding built to empty the old one, and its contents moved.
Live (field497-499), the camp stood on a hill at y=75, its stockpile chest at (-48, 75, 51), the farm at
sea level 40 blocks south-east and 12 below: 58 harvests in ten minutes and no wheat put in a chest, bread
and wheat 0, harvesters and the hurt stuck on the slope between ("can't reach the stockpile", six tries).

## The plan (operator, 2026-11-20: a new camp first)
The camp stays walled and lit throughout: the new one is laid out at sea level and walled, the stores are
moved into it, and only then is the old hill dug down.
1. **The new camp** at sea level by the farm: chunk (1, 5), the camp at x=24 z=88 (`minecraft.camp`) - its
   west half at y=63 already, the east rising to 65-67 (the camp's ground dig levels it). Its wall, lights,
   workshop and farm follow from the camp as they do now.
2. **The old camp's chests salvaged into the new stockpile.** Built: every chest of an old camp is condemned
   and emptied into the camp's chest by any squad's soldier (docs/military-doctrine.md § 17). A chest under a block
   has it broken first (877); one its salvager has no route to is passed over for the next (879) - the old hill's
   flatten digs to it. Left, if the flatten comes too late: stepping up to it on the blocks it carries.
   Live (field501-506): the camp moved to x=24 z=88 (its ground y=64), the old hill's 22 chests condemned and
   drawn from - a salvager with a full pack hands it on (870), and every soldier but a miner carries the surface
   load (871) so packs have room.
3. **The old hill flattened** to y=62, top level first, as the flatten's next site: `Flatten.next` taking an old
   camp's ground (x -60..-36, z 32..56, the hill) among its candidates once the farm-and-grove site is flat, as it
   takes the farm and grove boxes. Waits on the new camp walled - its SEIZURE phase holds every flatten off
   (field506: "the camp walled in - not begun").

## Done when
- Live: the new camp walled and lit at sea level; the old stockpile's contents in the new camp's chest;
  wheat in the stores; no "can't reach the stockpile" over a day.
- Squad simulation: a camp moved - its old chest, on a 6-high mound, emptied into the new camp's chest (on
  flat ground: built, KeepSimulationTest).
- The old hill down to y=62.
