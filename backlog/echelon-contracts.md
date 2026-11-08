---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camp standard designs); FM 6-0 (staff functions)
---

# Echelon contracts: concrete soldier commands, and an engineer between the general and the work

## Why
The echelons (docs/echelons/) show the abstractions don't hold:
- **The soldier knows too much about structures.** `Mason` knows the camp wall's layout,
  `PatrolBase` the hut's, `Lamplighter` "light this ground". Design knowledge sits in the one
  echelon that should know only how to work and how to survive.
- **The soldier's work has no command.** Its only order is `MoveTo` or `Idle`. What it works on
  comes from `app.SoldierRunner` (41 per-soldier maps) reading the general's `Task`, past the
  squad leader.
- **The structures can't vary.** A foothold and a main base, a forest and a coast, all get the same
  hut and the same ring wall, because the layout is code in the routine.

## Design
Four layers, each knowing only what it needs:

| Who | Knows | Decides |
|---|---|---|
| General (model) | the mission, the site's character (foothold or main base, forest or ocean) | what to build where, in which style, and in what order |
| Engineer (deterministic) | the style guide: a blueprint per structure type and biome | turns "a foothold cell, forest style, at chunk C" into cells and blocks; inspects the standards against the world |
| Squad leader (model, then drills) | its soldiers and their state | who takes which work package, security while working, sequencing |
| Soldier (deterministic) | its own survival, and the commands below | how to carry out this command safely |

- **The engineer is a staff function, not an echelon.** Like `Formations`, it's geometry done by
  code, so no model spends a call on fence posts. Blueprints are data files, so a style guide -
  form over function, later - is a new file, not new code. `backlog/cells.md`'s cell is one
  blueprint type.
- **Standards are inspected by the engineer, not the soldier.** "Every floor cell of the camp at
  block light 1 or more" is checked from the world every cycle. Each dark cell becomes a
  `Place(torch, cell)` command. The soldier never knows there's a camp.
- **Soldier commands are concrete**, beside `MoveTo` and `Idle`:
  `Dig(cells)`, `Place(cells, block)`, `Gather(resource, area, amount)`, `Craft(item, count)`,
  `Hold(area)`. Within a command the soldier chooses the order of work and where to stand, skips a
  cell it can't reach, and reports it. Each command has a WILCO, a CANTCO with its reason, and a
  completion.
- **Survival stays with the soldier:** fighting, eating, standing to, and never stepping off an
  edge unless told to. "Unless told" already exists: a soldier that can't comply safely reports
  CANTCO with the price, and only the squad leader grants the risk (docs/military-doctrine.md § 7).
- **The fan-out is deterministic.** A squad leader's model call every 20 seconds can't issue 64
  placements, so the engineer and the drills divide the work. The squad leader's judgement goes on
  allocation and security.
- **`SoldierRunner` keeps only the connections.** The squad loop - engineer, drills, command
  assignment - is a pure function in `command`/`bot`, run by the squad simulation
  (docs/architecture.md, squad simulation) as it is by the app.

## Scenarios that must pass first
Each runs in the squad simulation, against the code as it is, before anything is restructured. The mine
shaft (open ground, beside a pond, with no pickaxe in the squad), the night shift, and the wall on a
slope pass (`SquadSimulationTest`). Still to write, each needing hostiles, damage or respawning in
the simulation:
1. **A patrol base on a slope, a fight in the middle of building it.** The squad goes back to the
   same hut.
2. **A replacement stranded on a platform 17 blocks up.** A squadmate with blocks builds a stair up.

## Done when
- The scenarios pass on the new layers, and the echelon docs describe the commands, the engineer
  and the blueprints, not the runner's maps.
- `FORTIFY`, `SECURE` and `PATROL_BASE` are blueprints and inspections, not soldier routines.

## Notes
- Each command kind is scored in the field (backlog/command-scorecard.md); a command changes when
  a scenario can't pass without the change.
