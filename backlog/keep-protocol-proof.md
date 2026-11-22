---
status: needs-decision
priority: P3
depends-on: []
---

# A model of the flattening protocol, checked

## Why
The operator wanted the flatten's pacing protocol shown safe and live by a checker, not by examples, and its
cost compared with the alternatives (2026-11-14). The protocol this item modelled - strips, each reporting its
top - is gone: a level is now shared out as a rectangle a builder (`command.LevelPlan`, `LevelPlanTest`;
docs/military-doctrine.md § 17), and backlog/authority-and-missions.md step 4 (sectors) moves it again.

## Done when
- A small model of the protocol in a checker that runs in the build or beside it (TLA+ with TLC, Alloy, or an
  exhaustive search in Java over small sites): no builder digs below the site's level; a placed block is
  cleared before the level falls past it; under fair reporting the site reaches its floor, and a lost line
  delays and never deadlocks.
- A cost model beside it - builder-seconds idle, blocks walked - against the lower bound of every builder
  digging all day.

## Notes
- **The decision (operator):** is a checked model still wanted? If yes, this is rewritten against the sectors
  once step 4 lands, and nothing is built before then. If no, delete this file.
