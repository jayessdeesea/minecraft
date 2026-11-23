---
status: proposed
priority: P1
---

# Mobility: swimming, water lifts, and stairs into a tall face

## Why
A soldier steps up one, drops three, pillars, cuts and builds steps, and is dug out of a pocket or off a ledge by the
recovery drill (docs/echelons/soldier/capabilities.md, docs/military-doctrine.md § 7). What it still can't do: move
through water anywhere but the surface, climb a tall face with nothing carried, or build steps toward a place further
than one search reaches. The platoon leader still picks ground a squad can't get to.

## What's left
- **Swim as moves.** The planner knows a swimmer only at the surface (water at the feet, air at the head,
  `Pathfinder.swimmable`): a soldier wholly underwater has no footing in its plan. Wanted: rise a block through water
  (jump held), sink a block, and swim forward into a step at the surface - each a move at a swim's cost, so a flooded
  stair or a pool with a bank is swum, not stranded in. (Live, three soldiers floated for hours in the flooded spiral
  stair of the camp's mine; the stuck-in-water drill got them out by filling the water into steps.)
- **Water columns as lifts.** A 1x1 shaft of water sources is a ladder to swim up and drop down without damage; soul
  sand at its foot makes a bubble column that lifts, magma one that pulls down. For the mine (backlog/mining.md:
  "ladders down a 1x1 shaft"), a water elevator beside the staircase is the fast way between levels. The engineer
  lays it out at the mine base - the shaft cut, filled from water buckets (`Recipe.BUCKET` exists; carrying water
  needs the bucket's use), a plain column until there's Nether access for soul sand - and a soldier's moves include
  riding it.
- **A staircase into a face taller than three blocks, with nothing carried.** A step is cut only where it comes out
  under open air, so the planner never tunnels and the survey's flood fill stays bounded.
- **Built steps toward an objective beyond one search.** Offered on a partial route they led the scenario soldier
  astray (lakes and ravines), so a soldier builds only when the plan reaches.
- **A stair up whose cut stalls midway** - a cell that won't break, water found - is laid again from where the soldier
  stands. Today it's laid again only once the soldier is more than 6 off every cell of it (docs/military-doctrine.md
  § 7); check whether a stalled cut is covered before building anything.
- **The survey takes the top ground in each column.** In a valley or under an overhang that's often far above the
  squad, which is part of why the high ground dominates its picture.
- **The respawn platform, live.** Replacements respawn on a patch of glass at y=102 near (-5, 9), 17 blocks over the
  ground. The recovery drill's stair passes the squad simulation's copy of it; it wants seeing live, the next time a
  replacement lands there.

## Done when
- Squad simulation: a soldier in a flooded stair swims up and out; one across a pool with a bank swims to it.
- Squad simulation: a soldier with nothing carried reaches ground four or more blocks up a face by steps it cuts.
- A soldier sent past one search's reach builds toward it without being led into water or a ravine, in the scenario
  that failed.
- A water lift at a mine base is laid out, filled and ridden both ways, in the simulation.
- Live: a replacement on the respawn platform is down and linked up without help.

## Notes
- If something unseen stops a soldier again as at the mine's bottom (field161-168: Bob stuck three times a minute short
  of (-42, 31, 42), the cell air on the server), run this from the console while it's stuck:
  `execute as @e[x=-42,y=31,z=42,distance=..1,type=!player] run say PROBE` with the cell's own coordinates.

## Unreachable is a fault to find, not a state to report (operator, 2026-11-23)
"Mobility-wise, everything should be reachable." The garrison has drafted 1,069 stuck cases with their ground
(`garrison-state/worlds/`); a handful were ever registered. `StuckTriageIT` replays the newest forty, each soldier
alone on its recorded ground: 22 reach their goal - held live by something else, a crowd or their tasking - 3 have a
goal beyond the ground kept, and 15 are stuck alone. Of those, as first read:
- **The simulated server's own gaps** stop a replay before the soldier's fault shows: it swung only the one kind of
  gate it places (fixed), and it opens only the one chest state it places - a chest facing another way, or half of a
  double one, fails the tick of whoever uses it (Kate, Bob by the camp's chests). Next: any chest state opened.
- **A step of a block or two with a heap in the way** (Fran on the old hill's top among chests and tables, 2 blocks;
  Pete at y=41 under the camp, 3 up; Olga, Mia and Carol on the mine's stairs, 2 to 3): "no route" a body's length
  from the goal. Each to be read in its replay and registered under `stuck/`.
- **A stand in its own way** (Dana, rearming at a table: ended 1.7 off the stand it started on).
To build: the triage's STUCK cases read one by one, the fault of each fixed and its case registered; a goal beyond
the recorded ground recorded wider (the snapshot is 24 each way of the soldier, the goal often 30 off).
