---
status: proposed
priority: P2
depends-on: [authority-and-missions]
doctrine: FM 6-0 (common operational picture, running estimates, unit journal)
---

# The platoon leader's operations map: stations, and its own intent, in its prompt

## Why
The platoon leader's books are kept by code - the chunk register, the task register, the stores chest by
chest (docs/echelons/platoon-leader/decisions.md, "Its registers"; `app.StoresBook`) - but its decision prompt
isn't shown them, and it keeps nothing between calls but each squad's current task. So it can't know that a
furnace stands by the mine head, how long Bravo has been on the wall, or what it meant a squad to do next.

## What's left
- **Stations on the map, measured.** Tables, furnaces and chests with what's in them, mine heads and how deep,
  pens, and the danger seen there (deaths by cause, lava, water), keyed on the chunk (and y/16 for mine
  levels), kept by code from what the soldiers see. Never written by the model: it can't drift from the world
  or invent a chest.
- **The registers in the decision prompt, kept small:** the nearest of each kind of station and stock with its
  place, the cells near each squad's objective and near the camp, and each squad's task with since when, its
  progress and its last CANTCO. A map dumped whole is a grid to decode, and the models don't read grids.
- **The model writes only intent:** one line a squad - what it means the squad to achieve next - read back on
  its next call, so it continues a plan rather than deciding afresh.
- **The last few orders a squad** (`app.OrderTrace`) beside them.

## Done when
- The platoon leader's prompt carries the nearest of each station and stock with its place, and each squad's
  progress and intent line.
- A `PlatoonLeaderScenarioIT` situation where the right answer depends on a remembered station (send a squad
  with raw iron to the furnace by the mine) passes 5 of 5.

## Notes
- Follows backlog/authority-and-missions.md step 1, which renames the task register's table and types and
  adds the mission and order tables these lines read from.
- A task's sites - a workshop, a farm, a mine head - are handed here when the task ends
  (backlog/authority-and-missions.md).
