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
- **The stairwell standard and the cutter's upkeep are built** (docs/military-doctrine.md § 16):
  the 4x4 well in chunk-local 6..9, its north-east corner at every y = 12k, holes filled and wall
  ore put back. What's left: live, a mine sited to it and cut to its landing; the main tunnel's
  and branches' walls kept whole the same way.
- **Branch tunnels, the rest.** The tunnels are cut and lit (docs/military-doctrine.md § 16). What's
  left: the LOGSTAT counting raw iron and diamonds.
- **Levels.** Built: a squad to a level at y%4==0, two cutters to a level, a second level for a
  squad of four, levels cut on down while ore is wanted, and a dried-up mine moved to a fresh site
  (docs/military-doctrine.md § 16). What's left: the rest of the mine base (backlog/sites.md); and
  the squad quarrying the stone round the landing - the keep's 4,096 cobblestone - with an
  `Order.Gather(STONE)` round it for each soldier but the cutters. Tried; its squad simulation
  can't tell it from the miner's own tunnels, which keep every soldier busy there in a fresh mine,
  so it wants a scenario with the tunnels already cut.

- **The next mine cut ahead (operator, 2026-11-17).** Built: a squad released from the flatten
  with nothing short cuts the next site's stairs by day. Left: whether that's enough to have it cut
  before the keep's is spent, live; and a squad of its own for it if not.
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
  Live (field408), with the drill's order made to outrank the mine's, Hal at work in his tunnel was
  found stranded and given a stair up 26 times in one night; three crews stood empty, no ore.
  Built: in a mine's galleries only the pocket rule strands a soldier, not the buried or below ones
  (`SoldierRunner.cutOff`, `MINE_GALLERY_REACH`). Left: whether the pocket rule alone still fires on
  the 1x1 spiral's bottom - watch "cut off in a pocket" in the mine.
- **A cutter's share flipping.** Live (field410-411), Dana started branches 47 and 49 in turn every
  1-3 s, 188 times a night, her level's tunnels not growing, as a stranded squadmate came in and out
  of the mine's crew (fixed: none the drill has is counted). Watch "cuts branch" counts per level: a
  level whose starts run to hundreds a night is flipping again.
- **The mine base has one furnace for the night shift.** Live (field395, 15:48), five soldiers set
  about smelting at the keep mine's base within ten seconds - copper leggings, boots, a chestplate,
  two iron pickaxes - and a furnace is one soldier's at a time (`Crafter.usable`), so the rest waited
  on it: Ivy's run was cut off when another took it, and she stood beside it a minute, twice. Surplus
  to need, no locks (work in parallel): a base's furnaces sized to its shift - `Workshop` holds one
  furnace, so its landing, fitter and crafter would each take several. About two cut-off runs a night.

## Notes
- Diamonds need an iron pickaxe: the equipment ladder (docs/military-doctrine.md § 11) reaches
  one from the iron the stairs open.
- Lava lakes are common below y=-54; the stairs stop at lava beside a cell, and so should a
  tunnel.
- Ladders down a 1x1 shaft are faster to climb than stairs, and a vertical drop with water or
  platforms every 8 blocks is how a player makes one safe. Ladders need a climbing move the
  soldiers don't have; stairs came first because they're walked.
