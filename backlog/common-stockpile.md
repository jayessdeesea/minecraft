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
- **The leaders know what the soldiers need (operator, 2026-11-16):** "by now everyone should have
  armour, even if it is just copper." Field299: Carol, Hal and Pete wore none, Gus one piece, Dana,
  Ned and Olga two; the stores held about 1 raw iron and 22 raw copper, and soldiers gave up an iron
  pickaxe 86 times for "no IRON_ORE within 48 blocks" - each hunting ore alone where it stood. The
  platoon sergeant sums every soldier's kit gaps (armour pieces, iron pickaxes, axes) into the ore
  and ingots they take, and that bill - not a fixed reserve (`PlatoonSergeant.IRON_RESERVE`) - sets
  the mine's work and the released squads' shortfall; a soldier's armourer draws from the stockpile
  rather than searching for ore.
- **The ore's chain of hands (operator, 2026-11-14)**, each link a job of its own, chests between:
  1. *A mine stash* - built (docs/military-doctrine.md § 16, the store): the chest beside the
     landing a miner unloads raw ore into. What's left: its second chest when the first is full,
     and the stash shown in the mine base's site line.
  2. *The haul.* The stash brought up in one big batch - one soldier, a full pack of ore - to the
     surface depot's chests, at dawn when the squads come up anyway.
  3. *The smelter* - built (docs/military-doctrine.md § 4, the keep's smelter). What's left: coal
     kept in the chests and drawn for its fuel, and a smelter for a squad away from the keep.
  4. *The crafter.* Ingots drawn and turned into the ladder's next rung - armour, weapons, tools -
     for whoever is short, the iron pickaxe first.
  Today one soldier does all four for its own kit, and only from what it carries: raw ore stays in
  the pack of whoever dug it unless `MetalPool` pools it.
- Fuel: coal carried is burned first (docs/military-doctrine.md, the ladder). Left: coal kept in
  the depot and drawn for the smelter, and charcoal from the grove's logs.
- The general isn't shown the ore in the stockpile: its line says logs and cobblestone only.
- A mine's levels go deeper (built). Left: a copper level at about y=48, where copper is
  commonest, for armour; the fresh site a dried-up mine moves to (built: 32 west) at a chunk's
  centre (backlog/chunk-stairwells.md).

## Done when
- A miner follows a vein - the ore behind the ore it took - not only the faces its cuts touch.
- "Stone and iron" is met only while the stockpile and the force's packs hold the ore the armour
  still missing needs; short, the general sends a squad to MINE the tunnels.
- Simulation: one squad's miner stocks ore it mined; the other squad's soldier draws it and makes armour.
- Live: armour slots filled climb over a day, with no lone ore trips.

## A draw takes a whole stack
Built: what's drawn is kept for the kit, up to a stack (docs/military-doctrine.md § 11). Left: drawing only
what's wanted - a stack larger than the need picked up and the rest put back (a right-click takes half) -
so the next consumer finds some.

## The stores read swings with which chests were last looked in (field480-484)
The `[Stores]` line read seeds 702, 64, 0, 88, 0 across five runs in an hour, and wheat and bread 0 throughout.
In field480 the camp's next chest at (-50, 76, 47) was logged full and then forgotten, though it was never placed
(Dana couldn't take a block in hand to put it up). One cause found and fixed: the camp's upkeep dug out its workshop's
full chests - only the chest in use was kept from the dig - and the stores they held were forgotten (field486: a
chest at (-53, 76, 44) dug by Charlie's soldiers minutes after it filled). Every full chest is now kept from the
camp's dig. Left: a chest that never stood counted full and then forgotten, and the stores counted from every
standing chest - so a shortfall read is the force's, not the reading's.

