---
status: proposed
priority: P3
---

# Soldier physics approximations

## Why
`DeterministicSoldier`'s movement approximates vanilla physics, and live runs haven't yet forced
these gaps closed:
- Partial blocks: slabs and stairs collide as full cubes, while fences and walls are modelled as
  1.5 blocks tall.
- Swimming is at the surface only: no diving, no current pushing a swimmer, and seagrass, kelp
  and bubble columns are hazards a swimmer goes round.
- No ladders or vines for climbing.
- Landing in water cancels fall damage in vanilla, but the planner limits a drop into water as it
  does one onto ground.
- A blocked arrow is read from health, not from the server's own block event, which isn't
  decoded; and an arrow counts as in flight for a second after it comes into view, since velocity
  isn't decoded - one lying on the ground that comes into view raises a shield for a moment.
- Perception: a soldier knows every entity the server sends its connection - through walls, behind
  it, in the dark - where a player only sees what's on screen. Contact needs a line of sight
  (docs/military-doctrine.md § 8), but the squad's drills and the general's picture don't.

## Done when
- Each gap is closed only when a live run or scenario shows it costing something. Split it into
  its own item at that point.
