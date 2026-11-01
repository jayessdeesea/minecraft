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
- No swimming. Water is a hazard that soldiers route around.
- No ladders or vines for climbing.
- Drops longer than 3 blocks, which cost health, are never planned (backlog/damaging-drops.md).

## Done when
- Each gap is closed only when a live run or scenario shows it costing something. Split it into
  its own item at that point.
