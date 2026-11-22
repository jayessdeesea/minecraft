---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camp standard designs); FM 6-0 (staff functions)
---

# The engineer: blueprints as data, standards inspected from the world

## Why
The soldier knows too much about structures: `bot.supply.Mason` holds the camp wall's layout,
`command.PatrolBase` the hut's, `bot.supply.Lamplighter` "light this ground". So a foothold and a main base, a
forest and a coast, all get the same hut and the same ring wall, because the layout is code in a routine. The
soldier's orders are `MoveTo`, `Idle`, `Place`, `Dig`, `Fill` and `Gather` (`command.Order`); nothing says
"make this" or "hold this ground".

Who gives a soldier its work is backlog/soldier-order.md; who splits the ground among soldiers is
backlog/authority-and-missions.md, step 4; emptying `SoldierRunner` is backlog/split-oversized-sources.md.

## What's left
- **The engineer is a staff function, not an echelon** - geometry done by code, as `Formations` is, so no
  model spends a call on fence posts. It turns "a foothold cell, forest style, at chunk C" into cells and
  blocks.
- **Blueprints are data files**, one per structure type and biome, so a style guide is a new file, not new
  code. backlog/cells.md's cell is one blueprint type; backlog/sites.md's Notes hold the style vocabulary.
- **Standards are inspected by the engineer, not the soldier.** "Every floor cell of the camp at block light 1
  or more" is checked from the world every cycle, and each shortfall becomes a concrete order -
  `Place(torch, cell)`. The soldier never knows there's a camp.
- **`FORTIFY`, `SECURE` and `PATROL_BASE` become blueprints and inspections**, their layouts out of `Mason`,
  `Lamplighter` and `PatrolBase`.
- **Two more orders:** `Craft(item, count)` and `Hold(area)`, each with its WILCO, its CANTCO with the reason,
  and its completion, as the others have.

| Who | Knows | Decides |
|---|---|---|
| Platoon leader (model) | the mission, the site's character (foothold or main base, forest or ocean) | what to build where, in which style, in what order |
| Engineer (deterministic) | the style guide: a blueprint per structure type and biome | the cells and blocks; inspects the standards against the world |
| Squad leader | its soldiers and their state | who takes which work, security while working |
| Soldier (deterministic) | its own survival, and its orders | how to carry out this order safely |

## Done when
- `FORTIFY`, `SECURE` and `PATROL_BASE` are blueprints and inspections, not soldier routines; a second style
  of one of them is a data file and no code.
- `Order.Craft` and `Order.Hold` exist, each with a squad-simulation scenario.
- The squad-simulation scenarios that pass today pass unchanged on the new layers, and the echelon docs
  describe the engineer and the blueprints.

## Notes
- An order's kind changes when a scenario can't pass without the change (backlog/command-scorecard.md counts
  what the orders couldn't say).
