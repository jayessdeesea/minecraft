---
status: needs-decision
priority: P2
---

# Damaging drops: when may a soldier take a fall that hurts?

## Why
The route planner never drops more than 3 blocks (`Pathfinder.MAX_DROP`), the most a fall can be
without damage. Vanilla players can fall further: each block beyond 3 costs 1 health out of 20.
A soldier whose only way out is a longer drop is stuck forever.

Carol is the live case. In every run she ends up at (83, 67, 28), on a 7-cell ledge along a cliff
face. `TerrainProbeIT` shows the ledge's only exits drop 12 to 14 blocks to a cave floor at y=52,
which costs 9 to 11 health.

## Decision needed
How much health a soldier may spend on a drop, and when. Options:
1. **Never.** Today's behaviour: the soldier reports CANTCO and waits to be re-tasked or killed.
2. **Health-aware, last resort.** Damaging drops are allowed only when no route without one
   exists, and only if the soldier would land with at least a margin of health left (for example
   6, out of reach of a single mob hit). Planned at a high cost, so a safe route always wins.
3. **Only when ordered.** The soldier reports CANTCO with "only way out is a 12-block drop", and
   the squad leader decides. This keeps the judgement with the echelon that weighs risk, but adds
   another field for the model to get wrong.

Recommendation: option 2. It's deterministic, testable in the scenario harness (a ledge
family), and uses the health the soldier now reports.

## Notes
- The planner would take the soldier's current health as a parameter. `Pathfinder` is pure, so
  this is a signature change, not shared state.
- Landing in water negates fall damage in vanilla, but our model treats water as a hazard. Out of
  scope here.
