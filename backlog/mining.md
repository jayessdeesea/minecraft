---
status: proposed
priority: P1
depends-on: []
doctrine: ATP 3-34.81 (engineer reconnaissance); FM 4-0 (Class IV)
---

# Mining: ore at depth, and bases on the way down

## Why
A MINE squad cuts a staircase down (docs/military-doctrine.md § 16) and mines the ore beside it,
but that's only the ore that happens to lie against the shaft. Iron is commonest around y=16 and diamonds near
y=-55, in veins a few blocks off any one shaft. A player at a camp near spawn digs down, sets up a
working base at each mining level - y=32 and y=0, say - and runs tunnels out from it.

## Done when
- **The rules of a mine (operator, 2026-11-16; docs/military-doctrine.md § 16):** a level for each
  squad, and the grid - strips on the world's thirds, three high, two cutters a level by side - are
  built, and the pack's cap on a cutter's cobblestone and dirt, and its torch stock. Left: a squad
  of four on two levels; a vein's holes filled back with cobblestone or dirt once it's taken.
- **The stairwell standard and the cutter's upkeep are built** (docs/military-doctrine.md § 16):
  the 4x4 well in chunk-local 6..9, its north-east corner at every y = 12k, holes filled and wall
  ore put back. What's left: live, a mine sited to it and cut to its landing; the main tunnel's
  and branches' walls kept whole the same way.
- **Branch tunnels, the rest.** The tunnels are cut and lit (docs/military-doctrine.md § 16). What's
  left: the LOGSTAT counting raw iron and diamonds.
- **Levels.** The landing at the bottom is cut, with the mine base's workshop, and the stairs are
  lit (docs/military-doctrine.md § 16); a mine worked out while the force is short of iron is run
  on a stretch, to 96 blocks. Past that, with nothing left to cut, the night shift waits at
  the landing. What's left: iron wanted past the first pickaxe (armour at BUILDUP) running the mine
  on as well; cutters on different levels, not only different stretches of one (operator,
  2026-11-15: "almost working in different levels, different directions") - a second level's
  stairs from the landing, a cutter or two on each; the rest of the mine base (backlog/sites.md), and a next level once
  the first is cut - live, the camp's mine was cut to y=16 in under a quarter of an hour, and the
  night rule sent the squad back to it every night after with nothing left to do there. With every
  squad on the night shift (field149, 2026-11-13), 846 of 1,450 soldier-reports at the landing were
  InPosition. Wanted: the squad quarries the stone round the landing - the keep's 4,096
  cobblestone - with an `Order.Gather(STONE)` round it for each soldier but the cutter. Tried; its
  squad simulation can't tell it from the miner's own tunnels, which keep every soldier busy there
  in a fresh mine, so it wants a scenario with the tunnels already cut.

- **The general never orders a mine by day.** 0 of 256 orders in a night's run, 244 of them
  patrol bases: its picture asks for one only through the AAR's "no mine yet". The night shift
  cuts it regardless. A mine head in its own cell (backlog/cells.md) would put it in that picture.
- **The night shift, live.** Every squad now mines the camp's shaft at night
  (docs/military-doctrine.md § 16). Still to measure over a garrison night: whether hostiles
  follow the squad down from the surface, and whether the stairs need torches and a door at the top.

- **The shaft's bottom reads as a pocket.** Live (field172), a soldier at the keep mine's bottom
  (-24, 19, -23) was taken for cut off - no more than 128 cells to walk by the planner's moves - and
  the recovery drill laid out for it twice in two seconds as it walked on. A 1x1 spiral of 200 steps
  should count as hundreds of cells; whether the flood fill climbs the stair, or the cutter's work
  below the landing breaks it, wants a look (`SoldierRunner.cutOff`, `Pathfinder.reachable`).
- **The task's state flickers.** Live (field168), `task Mine[]` was logged DONE ("the stairs are
  cut down to y=16, and the ore beside them taken") and UNDER_WAY ("the stairs 51 of 54 steps
  down") in turn, once a second, all night, by both squads: the state is DONE when any soldier's
  `stairsProgress` is Done, else the count of steps cut - two readings of the same stairs that
  disagree. One measure, said once when it changes.

## Notes
- Diamonds need an iron pickaxe: the equipment ladder (docs/military-doctrine.md § 11) reaches
  one from the iron the stairs open.
- Lava lakes are common below y=-54; the stairs stop at lava beside a cell, and so should a
  tunnel.
- Ladders down a 1x1 shaft are faster to climb than stairs, and a vertical drop with water or
  platforms every 8 blocks is how a player makes one safe. Ladders need a climbing move the
  soldiers don't have; stairs came first because they're walked.
