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
- **The platoon leader's order is an area and a dump** (operator, 2026-11-18: "alpha go do farming
  in these x,z 1-16 areas and dump the supplies in this supply dump"). The squad's objective carries
  its work boxes and its supply point. Its squad leader shares the boxes among its soldiers
  (`WoodDuties`/`FarmDuties`: the felling separate from the planting, the harvest and replanting
  separate from the baking) and writes each soldier's order from its share.
- **Timeliness alarms at two echelons.** The squad leader knows each share's expected rate, and
  alarms on a soldier whose share falls behind it, or who holds without a share. That is the
  micromanagement. The platoon leader alarms on a squad whose area's output falls behind its order.
  The "without work" alert (`IdleWatch`) is the first, crude form.
- **Flat at sea level, the airhead too.** The flatten trends the camp and its work areas to sea
  level, for routes and fields; the phase rules for who flattens stand
  ([military-doctrine.md § 17](../docs/military-doctrine.md#17-the-lodgment)).
- **Walk round rather than build** (operator, 2026-11-18: if there are several ways somewhere,
  prefer the one that places no blocks). `Pathfinder.PILLAR_COST` is 4: a block and a jump's worth of
  time. Raised, it breaks two things: at 12, a climb that has to build runs out of its node budget
  (`PathfinderTest.withBlocksItBuildsAStairUpToAPlatformInOpenAir`). At 6 or 8, a soldier boxed in
  a pocket with nothing to build with tunnels sideways and ends in a pit at ground level
  (`aSoldierBoxedInBelowIsDugOutByASquadmateWithAPickaxe`), and at 8 the wall scenarios stall too.
  It wants the price kept apart from the search: a route found at the old price, then re-costed, or
  a node budget that scales with the price.
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
