---
status: ready
priority: P1
---

# One stuck soldier still makes the squad leader report a squad CANTCO

## Why
The system prompt says one stuck soldier other than the point man is no reason to abandon the
mission. `qwen3:14b` still often sets the squad-level `cantco` in that case. The general no longer
re-tasks on a reworded CANTCO, but the report is still wrong, and it hides a real squad-level
CANTCO when one occurs.

## Done when
- Across scenario variations with exactly one stuck non-point soldier, the squad leader rarely
  sets `cantco`, at a rate measured by `SquadLeaderScenarioIT` with a stuck-soldier variant added.

## Notes
- Same lesson as `<objective_ground>`: precompute rather than hope the model infers. For example,
  state explicitly in the prompt "N of M soldiers can reach the objective".
