---
status: proposed
priority: P1
---

# Persona contracts, and a squad simulation to pressure-test them

## Why
Live, the soldiers wander and stall, and nothing short of a live server shows why. The personas
(docs/personas/) show where the abstractions don't hold:
- **The soldier's work has no command.** Its only order is `MoveTo` or `Idle`. What it works on -
  which tree, which wall cell, which step - is decided by `app.SoldierRunner` (2,056 lines, 41
  per-soldier maps) from the general's `Task`, past the squad leader. The squad leader decides a
  formation for a squad whose hands are busy with something it never chose.
- **Nothing between a unit test and the live server.** Each routine is tested alone against a fake
  server, and a single soldier's movement against generated terrain (`SoldierScenarioTest`). But
  the squad loop - which routine each soldier gets, where it's sent, when the night shift goes
  down - lives in `SoldierRunner`, bound to live connections, so no test runs a squad through a
  task.
- **What that cost, live:** on its first night shift, the mine head was sited beside the camp's
  water, so the cutter stopped at step 2. Its squadmates couldn't make a pickaxe: each failed to
  walk to a crafting table. A simulation would have shown both in seconds.

## Design sketch
- **A soldier command vocabulary**, in `command`: a small set of work orders with the place and
  the cells in them - `Gather(resource, area)`, `Build(cells, material)`, `Dig(cells)`,
  `CutStairs(head, depth)`, `Light(area)`, `Till(area)`, `Plant(area)`, `Craft(recipe)` - alongside
  `MoveTo`. The squad leader's drills assign them, so each soldier has one order that says what it
  does, and the soldier reports against it (WILCO, `Cantco`).
- **The squad loop out of `SoldierRunner`**, as a pure function in `command` or `bot`: squad
  statuses, the world model and the objective in, an order per soldier out. `SoldierRunner` keeps
  only the connections.
- **A squad simulation** (test-only): `SyntheticWorld`, a fake server per soldier answering digs,
  placements, windows and crafting as the real one does, the squad loop, and a scripted general.
  It runs in seconds, with no Docker and no model.

## Scenarios to pressure-test it
1. **Cut a mine shaft at the camp:** ground by water, no pickaxe in the squad, the nearest crafting
   table on a ledge. Done when the stairs reach y=16 and the squad carries cobblestone and raw
   iron.
2. **The night shift:** dusk falls during scenario 1. The squad follows its cutter down, and
   nobody stands at the top in the dark.
3. **A patrol base on a slope**, with a fight in the middle of building it. The squad goes back
   to the same hut.
4. **A replacement stranded on a platform** 17 blocks up. A squadmate with blocks builds a stair
   up to it.
5. **The wall on a slope** with its dirt pit dug out.

## Done when
- Scenarios 1-5 run as tests and pass.
- The persona docs describe the vocabulary, not the runner's maps.

## Notes
- **Needs a decision (operator):** whether to restructure first (the vocabulary and the squad loop
  out of the runner), or build the simulation round the runner as it is and restructure under
  its tests. The second is safer. The first is the cleaner end state and makes the simulation
  smaller.
