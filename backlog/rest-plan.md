---
status: ready
priority: P2
depends-on: [patrol-base]
doctrine: ATP 3-21.8 (patrol base priorities of work - rest plan)
---

# Rest plan: beds in the patrol base

## Decided (operator, 2026-11-18)
Beds are the safe respawn: a soldier's bed in the lit, walled camp, used once to set its spawn there (using a bed
sets the spawn by day too). Wanted first, ahead of sleeping through nights. Live (field421-443), soldiers killed at
night respawned at the world's spawn on the keep's open site and were shot there again, Eli four times in a night.
Seen (field443): no soldier carried a bed or wool - the sheep-hunting for wool isn't yielding yet. Built:
wool from string, drawn from the depot (docs/military-doctrine.md § 11). Left: placing each soldier's bed in the
camp and using it once to set its spawn - and a soldier whose bed is placed no longer wants string for another.

## Why
A Minecraft player needs no sleep to keep going, but sleep does three things the squads go
without:
- **Phantoms.** A player who hasn't slept in three in-game days, and hasn't died meanwhile, draws
  phantoms from the night sky - but only while it stands above sea level where it can see the sky
  (the server's phantom spawner checks both). A soldier sealed under its hut's roof draws none; the
  three phantom deaths seen were soldiers out after dark, and the shelter rule
  (docs/military-doctrine.md § 14) is what keeps them in. Beds would end the draw outright.
- **Respawn.** Sleeping in a bed sets the player's spawn point there. A dead soldier now respawns
  at world spawn and links up from there (docs/military-doctrine.md § 6); with a bed it would wake
  in the camp.
- **The night.** Once enough of the online players are asleep (the `players_sleeping_percentage`
  gamerule), the server skips to dawn. Eight soldiers who never sleep are eight players who keep
  the operator's own bed from ever skipping a night.

- **Seen live (field45, field47):** two soldiers of the night shift died in an hour, each at
  1.1-1.3 health and food 0, fighting a phantom at the mine head - knocked into the air and killed
  by the fall. No staircase could be sited then (fixed: the camp's staircase is kept on disk,
  docs/military-doctrine.md § 16), so the whole squad held the head, under open sky, all night.
  With the stairs cut, the night shift goes down them; whether that alone keeps phantoms off
  wants a night watched. Until beds: a starving soldier under open sky at night is the phantom's.
  A cheaper first step: a soldier at low health by night takes cover. (The camp's farm, 44 cells
  then, is now grown with the plots by the waters beside it, docs/military-doctrine.md § 4.)

- **Live (field421).** A soldier killed at night respawns at the world's spawn, on the keep's open
  site, and holds there till dawn (LinkUp's night hold): Alice, killed at the keep's south edge,
  respawned at (0, 66, 0) and was shot again at (7.6, 65, 3.4) a minute later. A bed in the lit
  camp is the spawn that ends it.

## Doctrine
Rest is one of a patrol base's priorities of work, under a rest plan that keeps security while
part of the unit sleeps (ATP 3-21.8, ch. 7).

## Mapping to Minecraft
- **Security while sleeping is the hut.** A player can't sleep with a monster within 8 blocks, and
  a sealed, lit hut keeps them off - so the whole squad sleeps at once, not in shifts. Shifts buy
  nothing here: sleeping takes no time and leaves nobody worse off for it.
- **Turns, where they matter, are for phantoms.** A soldier kept awake for night work - a
  torchbearer, a sentry - needs to sleep one night in three, so the rest plan rotates who stays up.
- **Beds** are made: a soldier without one makes its wool from the depot's string or hunts sheep
  for it (docs/military-doctrine.md § 11). Left: shears (2 iron ingots), which take wool without
  killing the sheep - more wool per sheep, and the flock left to grow back.

## Done when
- A squad sealed in its patrol base at night lies down in beds placed inside it, and is up at dawn.
- No soldier goes three nights without sleeping. Measured by counting phantom spawns over a
  multi-day garrison run.
- A soldier that dies respawns at its bed.

## Notes
- **Decided (operator): beds are for the surface roles; miners work through the night**
  (docs/military-doctrine.md § 18). Phantoms come only to a player under open sky above sea
  level, so a miner under rock draws none however long it goes unslept - until it surfaces by
  night. A miner coming up at night is the case to watch.
- **A bed needs room in the hut.** The hasty hut's floor is the squad's own cells, two blocks high
  to the roof - no room to stand on a bed - so a bed means a bigger floor (2x3 for four). A
  sleeper is refused with a monster within 8 blocks of the bed, walls or not, so beds work best
  inside the lit, walled camp.
- **Decided (operator):** the night skips only the way it does for any player - when everyone in
  the overworld is asleep, the operator included. `players_sleeping_percentage` stays at its
  default of 100: the soldiers sleeping never ends the operator's night for them.
- So the night usually isn't skipped, and the rest plan is for phantoms: each soldier lies down in
  a bed often enough that none goes three nights without. Per the wiki, getting into a bed resets
  the phantom timer (`time_since_rest`) even when the night doesn't skip - confirm against the
  server before relying on it; if it only resets on a full night's sleep, the soldiers sleep in
  turns instead, a different half each night.
