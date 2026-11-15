---
status: proposed
priority: P1
doctrine: FM 4-0 (supply-point distribution; Class IV and VII); docs/echelons/tenets.md (specialists produce, missions consume)
---

# Ore and ingots through the common stockpile; the mine worked for them

## Why
Armour is short - 10 of 32 slots filled live - and the ore for it isn't reaching anyone:
- No miner mined ore in twelve runs (field112-123). It takes only ore touching the stairs it cuts,
  so a vein one block into the wall is passed by; and the mine's standard, "a mine cut down to
  the iron", is met once the stairs reach the bottom, after which no squad is sent to MINE.
- Raw ore and ingots stay in the pack of whoever dug them, smelted only into that soldier's own
  armour. Each soldier walks off alone for the 8 copper a chestplate takes: in field119, 39 of 67
  of those trips never reached the ore, one 90 blocks from camp.

## Built so far
- Raw ore through the stockpile (`bot.supply.MetalPool`, docs/military-doctrine.md § 12): kept
  only for a piece it makes, drawn to complete one, else pooled - one squad's copper armours the
  other (`copperInPiecesTooSmallForArmourIsPooledAndMadeIntoOne`).
- A mine cut to the bottom is worked again for the ore in sight of its cuts (`Miner.prospect`),
  as a squad sheltering down it at night does.

## Left
- **The ore's chain of hands (operator, 2026-11-14)**, each link a job of its own, chests between:
  1. *A mine stash.* Chests at the mine base's landing, beside its workshop: a miner unloads raw ore
     there and goes on cutting, not up 54 steps with every pack.
  2. *The haul.* The stash brought up in one big batch - one soldier, a full pack of ore - to the
     surface depot's chests, at dawn when the squads come up anyway.
  3. *The smelter.* Someone draws raw ore and fuel from the depot, smelts it at its furnace, and puts
     the ingots back in a chest.
  4. *The crafter.* Ingots drawn and turned into the ladder's next rung - armour, weapons, tools -
     for whoever is short, the iron pickaxe first.
  Today one soldier does all four for its own kit, and only from what it carries: raw ore stays in
  the pack of whoever dug it unless `MetalPool` pools it.
- Fuel: a smelt failed live for want of "planks or logs to burn". Coal from the mine, or charcoal
  from the grove's logs, kept in the depot for the smelter.
- The general isn't shown the ore in the stockpile: its line says logs and cobblestone only.
- Ingots stay with whoever smelted them.

## Done when
- A miner follows a vein - the ore behind the ore it took - not only the faces its cuts touch.
- "Stone and iron" is met only while the stockpile and the force's packs hold the ore the armour
  still missing needs; short, the general sends a squad to MINE the tunnels.
- Simulation: one squad's miner stocks ore it mined; the other squad's soldier draws it and makes armour.
- Live: armour slots filled climb over a day, with no lone ore trips.
