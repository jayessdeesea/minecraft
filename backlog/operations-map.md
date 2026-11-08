---
status: proposed
priority: P2
doctrine: FM 6-0 (common operational picture, running estimates, unit journal)
---

# The general's operations map and journal

## Why
The general decides from what one prompt shows it: the mission, the time, the foothold's
standards, and each squad's status now. It keeps nothing between calls but each squad's
objective. So it can't know where the base's chests of copper ingots are, that a furnace stands
by the mine head, how long Bravo has been on the wall, or why the last order failed. The operator
expected it to keep notes:
- by place, keyed on the 16-block cell: f(x/16, y/16, z/16) = what's there;
- on the missions under way;
- by squad: f(Alpha, Bravo, ...) = what each is working on.

## Doctrine
A headquarters keeps a **common operational picture** (an overlay on the map of what's where),
a **running estimate** for each line of effort (what's happening, how far along, what's in the
way), and a **unit journal** of what was ordered and reported, in order (FM 6-0). Nobody
remembers it; it's written down, and the commander reads it before deciding.

## Design sketch
- **The map is measured, not written by the model.** Keyed on the cell (chunk x and z, and y/16
  for mine levels): stations (table, furnace, chest and what's in it), patrol bases, walls, mine
  heads and how deep, the farm, tree farm and pens, and the danger seen there (deaths by cause,
  lava, water). Code keeps it from what the soldiers see. Same rule as `<objective_ground>`: the
  lookup is done for the model, and it can't drift from the world or invent a chest.
- **The squad notes are measured too:** each squad's task, since when, its progress (blocks left,
  steps cut), and the last CANTCO with its reason.
- **The model writes only intent.** One line per squad: what the general means it to achieve
  next, beside its objective. It's read back on the next call, so the general continues a plan
  rather than re-deciding from scratch every two minutes. That's the one thing only it can know.
- **The journal is the order trace** (`OrderTrace`), already logged. The general sees the last few
  entries per squad, not all of them.
- **Kept small:** the prompt shows only the cells near each squad's objective and near the camp,
  and the nearest of each kind of station. A map dumped whole is a grid to decode, and the models
  don't read grids (AGENTS.md, behaviour modelling).
- **Persisted** under `minecraft.state-dir`, like the wall layout (`WallStore`), so a restart
  doesn't forget it.

## Done when
- The general's prompt carries the nearest of each station and stock with its place, and each
  squad's progress and intent line.
- A `GeneralScenarioIT` situation where the right answer depends on a remembered station (send a
  squad with raw iron to the furnace by the mine) passes 5 of 5.

## Notes
- Depends on the soldiers recording what's in a chest they open; they already open them to
  stockpile (`SupplyRun`).
