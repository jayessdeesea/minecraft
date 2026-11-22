---
status: proposed
priority: P1
doctrine: FM 4-0 (supply-point distribution; Class IV and VII); docs/echelons/tenets.md (specialists produce, missions consume)
---

# Ore and ingots through the common stockpile; the mine worked for what the force needs

## Why
The operator (2026-11-16): "by now everyone should have armour, even if it is just copper." Ore is pooled
(`bot.supply.MetalPool`), veins are followed (`Miner.oreInSight`) and a mine's stash is drawn up by day, but nothing
sizes the mine's work to what the soldiers lack: the mine is asked for on a fixed reserve
(`PlatoonSergeant.IRON_RESERVE`, 24), the "stone and iron" standard is met once the stairs reach the bottom, and each
soldier still smelts and crafts its own kit from what it carries (field299: three soldiers with no armour, an iron
pickaxe given up 86 times for "no IRON_ORE within 48 blocks").

## What's left
- **The bill of what the soldiers need.** The platoon sergeant sums every soldier's kit gaps (armour pieces, iron
  pickaxes, axes) into the ore and ingots they take; that bill, not `IRON_RESERVE`, sets the mine's work and the
  "stone and iron" standard. A soldier's armourer draws from the stockpile rather than searching for ore.
- **The crafter** (the ore's chain of hands, operator 2026-11-14: stash, haul, smelter, crafter - each a job, chests
  between): ingots drawn and turned into the ladder's next rung - armour, weapons, tools - for whoever is short, the
  iron pickaxe first. Who is given the job is backlog/authority-and-missions.md's (step 4).
- **The stash**: its second chest when the first is full; the stash shown in the mine base's site line.
- **Fuel**: coal kept in the depot and drawn for the smelter; charcoal from the grove's logs; a smelter for a squad
  away from the keep.
- **The platoon leader's stockpile line** shows raw iron only: raw copper and ingots too.
- **A copper level** at about y=48, where copper is commonest, for armour.
- **A draw takes only what's wanted**: a stack larger than the need picked up and the rest put back (a right-click
  takes half), so the next consumer finds some.
- **A chest's kinds across a restart**: the per-chest kinds a draw looks for (`SoldierRunner.chestStock`) are this
  run's only, so after a restart a draw finds a chest holding its kind only once a load has gone into it
  (`StoresBook` already keeps each chest's contents for the sum).

## Done when
- "Stone and iron" is met only while the stockpile and the force's packs hold the ore the armour still missing needs;
  short, the platoon leader sends a squad to MINE.
- Simulation: a soldier short of a piece has it made from the stockpile's ingots by the crafter, with no ore trip of
  its own.
- Live: armour slots filled climb over a day, with no lone ore trips.
