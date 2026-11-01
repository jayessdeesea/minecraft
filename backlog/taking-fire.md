---
status: proposed
priority: P2
---

# Detect ranged fire from outside contact radius

## Why
A skeleton shoots from up to about 15 blocks away, but a soldier only reacts to hostiles within
`CONTACT_RADIUS` (8). In `CombatScenarioTest` a skeleton holding at 8-10 blocks kills the soldier
in 85 of 100 seeds, and the soldier often never reports contact at all. A soldier losing health
with no hostile in reach is under fire and should say so.

## Done when
- Health dropping with no hostile within contact radius triggers a contact report naming the
  likely source: the nearest ranged hostile within bow range, if one is tracked.
- The response is to break line of sight or close in as a squad, not to stand in the open. The
  skeleton baseline in the combat simulation improves.
