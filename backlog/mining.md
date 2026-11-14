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
- **Branch tunnels, the rest.** The tunnels are cut and lit (docs/military-doctrine.md § 16). What's
  left: the LOGSTAT counting raw iron and diamonds.
- **Levels.** The landing at the bottom is cut, with the mine base's workshop, and the stairs are
  lit (docs/military-doctrine.md § 16), and with nothing left to cut the night shift waits at
  the landing. What's left: the rest of the mine base (backlog/sites.md), and a next level once
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
