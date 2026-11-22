---
status: proposed
priority: P2
doctrine: ATP 3-21.8 (consolidation and reorganization); FM 4-0 (cross-leveling)
---

# Cross-levelling arrows

## Why
Squadmates hand each other logs, rations, a spare weapon, tools and saplings (`command.CrossLevel`;
docs/military-doctrine.md § 13). Arrows aren't covered: an archer with none stands beside a squadmate with a
stack and no bow.

The bow and arrows a soldier should carry are backlog/load-plans.md's; this is only moving them within the
squad.

## What's left
- Arrows paired and handed across as the other items are: to a soldier with a bow and few, from the nearest
  squadmate with more than its own load, or with no bow.

## Done when
- In `CrossLevelTest` and the squad simulation, an archer out of arrows is handed some by a squadmate with
  spare, and nobody is handed arrows without a bow.

## Notes
- **What a thrown stack holds isn't decoded.** The receiver walks onto any dropped item near it; with more
  kinds being passed, the item entity's metadata may be needed so a receiver doesn't chase an unrelated drop
  (backlog/salvage-and-pack.md wants the same decode).
- A receiver with a full pack throws things away to take what it's handed
  (backlog/authority-and-missions.md, "Left from the flatten work").
