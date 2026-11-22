---
status: proposed
priority: P2
depends-on: []
---

# Ladders: a soldier climbs one, and puts one up

## Why
The operator (2026-11-22): the soldiers need to handle ladders. Today a ladder is no move at all - the planner has
none (`bot.nav.Pathfinder`), so a route up or down one doesn't exist, a soldier beside one stands as at a wall, and
the way up a face is a stair cut or built, a block a step. A ladder is one column: the stacked wheat-farm layouts
wait on it (backlog/wheat-chunk-layout.md), a mine's shaft could be a ladder's width, not a stair's, and a permanent
structure of more than one floor needs a way between them.

## What's left
- **Climbing as a move** in the planner and the walk: onto a ladder from its foot or its head, up and down it at the
  ladder's speed, off it at any block of its height, no fall while on it; a ladder block known to the block tables.
- **The simulated server** answers a climb as the real one does, so a squad simulation can use one.
- **Putting one up:** the recipe (7 sticks for 3), and a ladder placed against a solid face, a block at a time from
  the bottom, as a builder's cells are - an order's work, given by the squad leader, never a soldier's own idea
  (backlog/authority-and-missions.md).
- **Vines** climb the same way and are left until something needs them.

## Done when
- A soldier routes up a ladder already standing to a goal at its head, and down it, in the planner's tests and the
  squad simulation.
- Ordered to, a soldier puts up a ladder of a given height against a face and climbs it.
- Live: a soldier reaches a ledge by a ladder a player put there.
