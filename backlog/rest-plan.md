---
status: ready
priority: P2
depends-on: [patrol-base]
doctrine: ATP 3-21.8 (patrol base priorities of work - rest plan)
---

# Rest plan: beds in the patrol base

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
