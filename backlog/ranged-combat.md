---
status: proposed
priority: P2
doctrine: ATP 3-21.8 (weapons employment; direct fire)
---

# Ranged weapons and choosing a weapon

## Why
The kit report counts bows and crossbows as weapons, but no soldier fires one. A soldier holding
only a bow fights bare-handed. Every fight is decided at arm's length, including against a
skeleton on a ledge or across water, where closing is slow or impossible.

## Done when
- **Choosing a weapon:** each tick the reflex picks one for the nearest visible hostile.
  - Melee (sword, else axe, else fists) when it's within a few blocks and reachable.
  - Bow or crossbow, with arrows, when it's farther away, or when it's visible but the pathfinder
    can't reach it: a ledge, across water, behind a drop.
  - Accessibility, not just distance, decides it, since the reachability and line-of-sight checks
    already exist.
- **Shooting:** the soldier draws, aims and releases. For a bow that's Use Item, hold, then Player
  Action RELEASE_USE_ITEM, with the draw time chosen for the range and aim leading the target's
  motion. A crossbow is load, then fire.
- **Making them:**
  - A bow is 3 sticks and 3 string. String comes from spiders, or from cobwebs broken with a
    sword.
  - Arrows are flint, a stick and a feather. Flint comes from gravel, feathers from chickens.
  - Ammunition is the hard part. The supply point can stock arrows, and LOGSTAT already counts
    them (Class V).
- Measured in `CombatScenarioTest`: against a skeleton on an unreachable ledge, a soldier with a bow
  kills it; one without reports it can't engage, rather than standing below taking arrows.

## Notes
- Arrows are entities: the drop is visible in `EntityTracker`, and a missed arrow can be picked
  back up.
- Squad level: ranged soldiers give supporting fire while others close. That's the fire-and-
  movement half of react-to-contact (docs/military-doctrine.md § 8), and a reason for fire teams
  of four (backlog/scenario-matrix.md, Notes).
