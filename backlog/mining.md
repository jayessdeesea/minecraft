---
status: proposed
priority: P1
depends-on: []
doctrine: ATP 3-34.81 (engineer reconnaissance); FM 4-0 (Class IV)
---

# Mining: ore at depth, and bases on the way down

## Why
A mine squad cuts the stairwell down, sets a level every four blocks, and runs a main tunnel and lit branches out from
it (docs/military-doctrine.md § 16). The ore it brings up is still thin and uneven - nights of 0 ore of the 16 wanted
with every level crewed (field443) - because every level works the same slab of rock, a gallery that meets a cave
stops there, and a mine's base has one furnace and nothing to re-arm a respawn with. Iron is commonest around y=16 and
diamonds near y=-55, in veins a few blocks off any one shaft.

## What's left
**Where the squads mine, and who says so**
- **By night, a mine site per squad** - backlog/authority-and-missions.md step 3; today every squad goes down the
  camp's one shaft. To measure over a garrison night once that stands: whether hostiles follow a squad down from the
  surface, whether the stairs need torches and a door at the top, and whether a site cut fresh yields the night's 16
  ore.
- **A mine ordered by day, and the next mine cut ahead of the last one's end** (operator, 2026-11-17), are the platoon
  leader's tasks, given in its one decision - authority-and-missions.md step 5 - not a standing rule's (today a squad
  released from the flatten with nothing short cuts the next site's stairs, and the night shift cuts a mine whatever
  was ordered). Its picture wants the mine head in it to ask for one (backlog/cells.md).
- **The squad quarrying the stone round the landing** - the keep's 4,096 cobblestone - each soldier but the cutters:
  the squad leader's split of its ground, authority-and-missions.md step 4. Its squad simulation wants a mine with the
  tunnels already cut; in a fresh one the miner's own tunnels keep every soldier busy and nothing tells the two apart.
- The rest of the mine base: backlog/sites.md. Faces 100 blocks off with "no route" each report (field458):
  backlog/long-routes.md.

**The rock worked**
- **Unworked rock** (operator, 2026-11-17). Every level's main tunnel runs straight out from its last step, the same
  way on every level (`Staircase.outward`), so levels four apart have all worked one slab and the rock round the
  shaft the other ways is untouched. Turning a level's tunnel at the landing would cut through the spiral's own
  steps, and mines on disk keep the shape they were cut to: a heading per level off a short spur clear of the ring
  wants designing, not a flag. First, a probe (console `execute if block`) of a branch wall cut in field443 for ore
  the model doesn't show - the seeing worked that night (ore mined beside Delta's stairs), so spent faces are the
  likelier cause and the headings the fix.
- **A gallery that breaks into a cave** (`stuck/cave-eli-field552`; field535-552, Eli at y=20): its next stand is over
  the void with no floor, and the miner has no route to it for runs. Decided (backlog/questions-overnight.md): floored
  across at its level with the cobblestone it carries. First a squad simulation that holds a scenario's objective at
  its height underground - today the objective is put on the surface (`NightRules.grounded`), the camp's ground work
  and the recovery drill take the soldier up to it, and the cave is never crossed. Then the bridge level with the
  goal (`Pathfinder.stepBuild`'s ground-beyond rule relaxed), unproven without that scenario.
- **A cutter at a cave with nothing to floor it** (field462). It keeps a stack of cobblestone when it stores
  (`SoldierErrands.MINE_CARRIED`) and gives the waiting tunnels up after an idle minute. Left: short of blocks it says
  so and is supplied from the mine's stash (backlog/soldier-order.md, running low is a LOGSTAT), or takes the stone
  from its own tunnel's wall - so the cave is floored, not the tunnel lost.
- **The walls kept whole** in the main tunnel and the branches - holes filled, wall ore put back - as the stairwell's
  are. And live, a mine sited to the stairwell standard and cut to its landing.
- **The LOGSTAT counting raw iron and diamonds.**

**The base and the crew**
- **Furnaces sized to the shift.** `Workshop` holds one furnace and a furnace is one soldier's at a time
  (`Crafter.usable`): field395, five soldiers set about smelting at the keep mine's base within ten seconds and the
  rest waited, about two runs cut off a night. Surplus to need, no locks: the workshop, its landing, fitter and
  crafter each taking several.
- **A re-arming cache at each mine head.** field427-435: five soldiers killed by night respawned, were sent down
  their squad's mine and stood the night at its head's workshop - "can't make wooden pickaxe and wooden sword: no LOG
  within 48 blocks". The head's stash chest holds a pickaxe and a sword's worth of logs (three), kept back from the
  depot and topped up by day like the mine's torches; a respawn short of a pickaxe draws from it before it goes down.
- **Two cutters never sent to stand on one stair cell** (field465: Mia and Ned on one cell all night, 0 ore). Their
  waits stand a step apart (`Miner`); their cuts don't.
- **A wait at step 1 past a few minutes said loudly**: a waiting miner reports `Mining[step=1]` and raises no
  `[Alert]`.

**Read as in the mine, or out of it, wrongly**
- **The climb-out reads a hillside as the mine.** field440: Liam on a slope under a hill, 38 blocks from Charlie's
  mine head at y=96, was sent "climbs out of the mine to its head first" every 2 s, in turn with his armourer's
  errand, and stood. The climb-out (in `SoldierRunner.orderSquad`) asks only that the soldier is underground, below
  the head by `MINE_BELOW` and within `CLIMB_OUT_NEAR` of it; it wants the soldier in the mine's galleries - on the
  mine's cells - as `SoldierRunner.cutOff` reads a mine (`MINE_GALLERY_REACH`).
- **The shaft's bottom read as a pocket** (field172; field408). In a mine's galleries only the pocket rule strands a
  soldier now. To check: whether that rule alone still fires at a 1x1 spiral's bottom - whether the flood
  (`Pathfinder.reachable`) climbs the stair, or the cutter's work below the landing breaks it. The log line is "cut
  off in a pocket".
- **Watch for branch flipping**: "cuts branch" counts a level; starts in the hundreds a night mean a cutter's branch
  is changing under it again (field410-412).

## Done when
- A garrison night's 16 ore, each squad at its own site, with no cutter standing a night at a cave, a furnace or
  another's stair cell.
- A respawn by night goes back down armed.
- No "climbs out of the mine" for a soldier on the surface, and no "cut off in a pocket" for one at a shaft's bottom.

## Notes
- Diamonds need an iron pickaxe: the equipment ladder (docs/military-doctrine.md § 11) reaches one from the iron the
  stairs open.
- Lava lakes are common below y=-54; the stairs stop at lava beside a cell, and so should a tunnel.
- Ladders down a 1x1 shaft are faster to climb than stairs, and a vertical drop with water or platforms every 8
  blocks is how a player makes one safe. Ladders need a climbing move the soldiers don't have; stairs came first
  because they're walked.
- Bows against archers at the far faces: backlog/ranged-combat.md.
