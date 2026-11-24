---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-21.8 (patrol base priorities of work - rest plan)
---

# Rest plan: beds in the camp

## Why
Nobody in the force sleeps, so phantoms come every night (and linger by day): on 2026-11-24 a run logged 1,197
phantom contact lines in 26 minutes, and every squad was taken to and fro between its task and fighting. Beds used
each night are the cure; the overhead rule in `DeterministicSoldier` only stops the flapping.

A soldier makes itself a bed - wool from sheep or from the depot's string (docs/military-doctrine.md § 11) -
and then does nothing with it. Without a bed used, a soldier killed at night respawns at the world's spawn in
the open and is shot again (live, field421-443: Eli four times in a night), and one who hasn't slept in three
in-game days draws phantoms whenever it stands under open sky above sea level.

## Decided (operator)
- 2026-11-18: beds are the safe respawn first, ahead of sleeping through nights: each soldier's bed in the
  lit, walled camp, used once to set its spawn there (using a bed sets the spawn by day too).
- Beds are for the surface roles; miners work through the night. A miner under rock draws no phantoms
  however long it goes unslept - until it surfaces by night, the case to watch.
- The night skips only as it does for any player: `players_sleeping_percentage` stays at its default of 100,
  so the soldiers sleeping never ends the operator's night for them. The rest plan is for phantoms and the
  respawn, not for skipping nights.

## What's left
- **Place and use.** Each soldier's bed placed in the camp and used once; a soldier whose bed is placed no
  longer wants string for another.
- **Lying down.** A squad in for the night lies down in its beds and is up at dawn. The whole squad at once,
  not in shifts: a sealed, lit shelter is the security, and sleeping costs nothing.
- **One night in three.** A soldier kept up for night work sleeps often enough that none goes three nights
  without; the rest plan rotates who stays up.
- **Shears** (2 iron ingots): wool without killing the sheep, the flock left to grow back.

## Done when
- A soldier that dies respawns at its bed.
- A squad sealed in for the night lies down in beds placed inside its shelter, and is up at dawn.
- No soldier goes three nights without sleeping: phantom spawns counted over a multi-day garrison run.

## Notes
- **A bed needs room.** The hasty hut's floor is the squad's own cells, two blocks high to the roof - no room
  to stand on a bed - so a bed means a bigger floor (2x3 for four). A sleeper is refused with a monster within
  8 blocks of the bed, walls or not, so beds work best inside the lit, walled camp
  (backlog/durable-shelter.md).
- **Confirm against the server:** per the wiki, getting into a bed resets the phantom timer
  (`time_since_rest`) even when the night doesn't skip. If it only resets on a full night's sleep, the
  soldiers sleep in turns instead, a different half each night.
- A cheaper first step against phantoms: a soldier at low health by night takes cover.
