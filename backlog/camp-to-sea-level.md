---
status: proposed
priority: P3
doctrine: JP 3-18 (the lodgment developed); ATP 3-34.40 (site preparation); FM 4-0 (supply-point distribution)
---

# The camp to sea level: the old hill cut down, the base in its layers

## Why
The operator (2026-11-20): the camp is flattened gradually to sea level, its chests with it; (2026-11-21) its ground is
dug below sea level and built back in known layers, so every template stands on one floor the force can count on - no
cave under a farm, no water hole draining, no sand or gravel falling. The new camp at sea level and the old camp's
chests salvaged into its stockpile are built (docs/military-doctrine.md § 17), and a wheat chunk gets its layers by the
operator's farm mission. The old hill still stands, and no other chunk of the base is laid.

## What's left
- **The old hill flattened** to y=62, top level first, once the SEIZURE has ended (the camp lit and every soldier
  armed, held a day - it holds every flatten off). How it's tasked is the decision in Notes.
- **A chest its salvager has no route to** is passed over for the next today, the flatten digging to it in time. If the
  flatten comes too late: stepping up to it on the blocks the salvager carries. A squad simulation first - the old
  chest on a 6-high mound (on flat ground it's `KeepSimulationTest.anOldCampsChestIsEmptiedIntoTheNewCampsStockpile`).
- **The base's layers, chunk by chunk, as missions.** Sea level - 1 (y=62) is cobblestone, every cell of the base,
  natural stone included. Sea level (y=63) by what the chunk is for: dirt on a wheat chunk or a tree chunk, so it
  grows (a wheat chunk's water holes sit on the cobblestone and hold); cobblestone on a supply point, on paths and on
  camp ground, so the whole base is walked at y=64, the world's own sea-level ground. Left:
  - the farm mission's steps (flattened to a block below, floored, the template on it) for the other chunks - the
    supply point, a tree chunk, paths and camp ground, cobblestone at 63 in place of the template's dirt;
  - the platoon leader giving them for the camp's ring, not only the operator - as tasks with their areas of
    operations, backlog/authority-and-missions.md step 3;
  - the slope's fills: the ground round a chunk in a hollow raised a block a block out (`FlattenPlan.fill` outside the
    chunk is measured, not worked); and a hollow under the floor - a cave, a pond's bed - filled, not bridged;
  - the template's dirt supplied: its farmers lay what they carry (the flatten's dig gives most of it), with no draw
    of dirt from the stores when they carry none;
  - lit: cobblestone in the dark spawns hostiles - the supply point's and the paths' torches.

## Done when
- Live: wheat in the stores; no "can't reach the stockpile" over a day (field497-499: the stockpile on a hill at y=75,
  58 harvests in ten minutes and no wheat put in a chest).
- Squad simulation: a camp moved - its old chest, on a 6-high mound, emptied into the new camp's chest.
- The old hill down to y=62.
- The camp's first ring in its layers: cobblestone at y=62 throughout, dirt at y=63 under the wheat and tree chunks and
  cobblestone elsewhere; no cell left open below sea level as it's laid.

## Notes
- **Decided (operator, 2026-11-22): the old hill is left for now.** Cutting it down gives nothing toward the reserves
  (backlog/sites.md, the order of building). When it's wanted it's a mission, chunk by chunk, under
  backlog/authority-and-missions.md step 3 - the hill spans chunks x -4..-3, z 2..3 - not the flatten's next site
  chosen by rule.
- The live camp is at x=24 z=88, ground y=64, chunk (1, 5) - deployment configuration (`minecraft.camp`; the
  repository's default is no camp); the old hill's stockpile chest stood at (-48, 75, 51).
