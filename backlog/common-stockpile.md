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

## Left
- The general isn't shown the ore in the stockpile: its line says logs and cobblestone only.
- Ingots stay with whoever smelted them.

## Done when
- A miner takes ore it can see from its stairs and tunnels - a vein followed, not just a face
  touched - copper as well as iron: copper armour beats none.
- "Stone and iron" is met only while the stockpile and the force's packs hold the ore the armour
  still missing needs; short, the general sends a squad to MINE the tunnels.
- Simulation: one squad's miner stocks ore it mined; the other squad's soldier draws it and makes armour.
- Live: armour slots filled climb over a day, with no lone ore trips.
