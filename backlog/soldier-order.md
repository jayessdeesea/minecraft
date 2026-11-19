---
status: ready
priority: P1
depends-on: []
doctrine: FM 6-0 (the OPORD's five paragraphs); ATP 3-21.8 (react to contact, consolidate and reorganise, pre-combat checks and inspections)
---

# One order per soldier

## Why
The operator's (2026-11-18): a soldier should know, for its task, the area it works and where its
supply point is; get attacked, defend, and go back to the work; say what it lacks - a hoe, seeds -
before it starts, or as it runs short; and never make up a task of its own: a farmer with no hoe
doesn't start chopping trees.

Today a soldier's state is 128 per-name maps and sets in `app.SoldierRunner`, about 50 of them
assignments the squad loop writes and the tick reads - planting, farming, gathering, clearing,
lighting, walling, the duties, the ground orders, the keep's hands and fetches, `huntAllowed`,
`drawingStone`. No one value says what a soldier has been told, nothing ranks them, and the
routines fill the gaps with errands of their own (`app.SoldierErrands`; the armourer fetching a
handle's log off a woodcutter's strip, `WoodDutiesSimulationTest`).

## Design
- **The order** (`command`): the OPORD's paragraphs at the soldier's level - its task and purpose
  (paragraph 2), its area (3, execution), its supply point and the basic load the task needs (4,
  sustainment), whom it reports to (5). Its squad leader writes it; nothing else gives a soldier
  work.
- **A stack for interruptions.** Contact pushes the fight (react to contact); once it's over, the
  soldier consolidates and pops back to its order as it was. Eating, getting out of water, standing
  to at dusk are its own upkeep, pushed the same way - never a task.
- **Pre-combat checks.** Before it starts, the soldier checks its kit against the order's basic
  load; short, it reports what it lacks ("short: hoe, 32 seeds"), and its squad leader answers with
  a draw from the supply point or a craft from what's there - or passes it up as CANTCO. Mid-work,
  running low is a LOGSTAT the same way. It never gathers its own makings.
- **The duty order carries the rest of the OPORD.** A producer's duty, ground and dump are one value
  (`command.DutyOrder`), and the farm's and the wood's duty maps are gone from `SoldierRunner`. What's
  left: the soldier's tick reads its ground from the order for the wood too (the woodcutter's
  `Order.Gather` box and the planter's grove are still worked out apart). Its draws - food, stone, logs - still
  come from `stockpileFor`; only its deposits go to the order's dump.
- **Sea level past the farm and grove.** Once a site is flat, the next is the nearest farm or grove box off
  its level (`Flatten.next`). The camp inside its wall is left at its own height (its floor is paved, not
  cut: the operator's earlier rule), and the wood's ground isn't flattened while it stands. Left: the
  camp's routes between the work areas, and a squad-simulation scenario of one site handing on to the
  next (a flatten takes a simulated day).
- **Migrated one assignment map at a time**, the farm first: each map moved into the order is a
  slice out of `SoldierRunner` (backlog/split-oversized-sources.md).

## Done when
- A soldier's assignment is one value its squad leader wrote, shown in the squad leader's view.
- A soldier in a fight goes back to the same task, area and cell after it, in the squad simulation.
- A farmer without a hoe reports it and is supplied, and fells no tree, in the squad simulation.
- `SoldierErrands` is empty.

Begun: a soldier on a mine, a duty or the keep no longer digs its kit's makings itself
(`Armourer.digsItsMakings`) - it says what it's short of and draws it; the request reaching its squad
leader as a supply request, and the idle soldier's own digging, are left.
