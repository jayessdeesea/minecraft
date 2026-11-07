---
status: needs-decision
priority: P2
depends-on: [patrol-base]
doctrine: ATP 3-21.8 (patrol base priorities of work - rest plan)
---

# Rest plan: beds in the patrol base

## Why
A Minecraft player needs no sleep to keep going, but sleep does three things the squads go
without:
- **Phantoms.** A player who hasn't slept in three in-game days, and hasn't died meanwhile, draws
  phantoms from the night sky. Squads that never sleep get them from the fourth night on. A
  roofed hut shelters from them; open ground doesn't.
- **Respawn.** Sleeping in a bed sets the player's spawn point there. A dead soldier now respawns
  at world spawn and links up from there (docs/military-doctrine.md § 6); with a bed it would wake
  in the camp.
- **The night.** Once enough of the online players are asleep (the `players_sleeping_percentage`
  gamerule), the server skips to dawn. Eight soldiers who never sleep are eight players who keep
  the operator's own bed from ever skipping a night.

## Doctrine
Rest is one of a patrol base's priorities of work, under a rest plan that keeps security while
part of the unit sleeps (ATP 3-21.8, ch. 7).

## Mapping to Minecraft
- **Security while sleeping is the hut.** A player can't sleep with a monster within 8 blocks, and
  a sealed, lit hut keeps them off - so the whole squad sleeps at once, not in shifts. Shifts buy
  nothing here: sleeping takes no time and leaves nobody worse off for it.
- **Turns, where they matter, are for phantoms.** A soldier kept awake for night work - a
  torchbearer, a sentry - needs to sleep one night in three, so the rest plan rotates who stays up.
- **Beds** are 3 wool and 3 planks. Wool comes from sheep: shorn with shears (2 iron ingots), or one
  wool from killing one. So beds wait on hunting or shearing sheep, a new capability.

## Done when
- A squad sealed in its patrol base at night lies down in beds placed inside it, and is up at dawn.
- No soldier goes three nights without sleeping. Measured by counting phantom spawns over a
  multi-day garrison run.
- A soldier that dies respawns at its bed.

## Notes
- **Question for the operator:** should the soldiers' sleep skip the night for the whole server?
  With `players_sleeping_percentage` at its default of 100, the night skips only when the operator
  sleeps too. Lowered, the soldiers alone would end every night, the operator's included.
