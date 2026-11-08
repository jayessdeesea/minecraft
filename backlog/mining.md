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
- **Branch tunnels, the rest.** The tunnels are cut (docs/military-doctrine.md § 16). What's left:
  lighting them with torches, and the LOGSTAT counting raw iron and diamonds.
- **Levels.** A landing at a set depth: a small lit room off the stairs, with a table and a
  furnace, where the squad smelts and shelters at night without climbing out.

- **The mine is never ordered.** The general has never set MINE: 0 of 256 orders in a night's
  run, 244 of them patrol bases. Nothing in its picture asks for one except the AAR's "no mine
  yet". A mine head in its own cell (backlog/cells.md) and the day-2 standard as data it reads
  (backlog/roadmap.md) would.
- **Night work underground.** Every squad now seals itself in at night and does nothing until
  dawn. A lit mine is shelter too: one squad holds the base, and the other spends the night
  down the shaft, mining. The shelter rule (`minecraft.general.shelter-at-night`) would have to
  count a squad in a lit mine as sheltered.

## Notes
- Diamonds need an iron pickaxe: the equipment ladder (docs/military-doctrine.md § 11) reaches
  one from the iron the stairs open.
- Lava lakes are common below y=-54; the stairs stop at lava beside a cell, and so should a
  tunnel.
- Ladders down a 1x1 shaft are faster to climb than stairs, and a vertical drop with water or
  platforms every 8 blocks is how a player makes one safe. Ladders need a climbing move the
  soldiers don't have; stairs came first because they're walked.
