---
status: ready
priority: P1
---

# The timings the force acts by, checked against the game's on a schedule

## Why
The operator (2026-11-24): re-assess the timings of the weapons, tools and the rest periodically - it's very bad to
have them wrong. Found that day by eye, not by a check: stone and cobblestone were dug in a fixed 35 and 45 ticks
whatever pickaxe was held, three times a stone pickaxe's 15 on cobblestone. A time too long is work lost quietly; one
too short is a dig the server refuses ("broken within 40 ticks; it didn't come", 62 times that day).

## Done when
- **Every time is derived, not written down**: a dig's from the block's hardness and the tool held (`world.Hardness`,
  `BreakTime`); a weapon's from its cooldown and damage (`weapon-stats-777.txt`); eating, a bow's draw, a furnace's
  smelt, a crop's growth. `Digger.Target`'s fixed `digTicks` for logs, soil, gravel and leaves go the way rock's went.
- **A table test** holds the game's own figures for the pinned version - break ticks by block and tool tier, attack
  cooldown ticks by weapon - and fails where a derived time differs.
- **The live check, each profile** (docs/conventions.md, the sixteenth commit): from the run's log, the digs the server
  refused (too short) and the digs that took more than their best time (`ErrandLedger`'s best break against the
  spell's) by block and tool; swings that landed before their cooldown; each said with its count. A kind over a
  threshold is a fix before the next change.
- The pin moves (docs/stack.md): the table is regenerated with it.
