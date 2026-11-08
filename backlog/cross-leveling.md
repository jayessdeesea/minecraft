---
status: proposed
priority: P2
doctrine: ATP 3-21.8 (consolidation and reorganization); FM 4-0 (cross-leveling)
---

# Cross-leveling beyond logs

## Why
Soldiers hand logs to a squadmate who needs them for a tool, food to one that has none, and a
spare weapon to one bare-handed (docs/military-doctrine.md § 13). Arrows aren't covered yet.

The stockpile also can't be drawn on for materials: logs go into the supply point's chest but never
come back out.

## Done when
- Arrows are cross-levelled the same way.
- A soldier short of logs, with no squadmate to cover it, draws them from the stockpile when the
  squad is at the supply point. This reuses `SupplyRun`'s chest handling.

## Notes
- **What a thrown stack holds isn't decoded.** The receiver walks onto any dropped item near it.
  With more than one kind of thing being passed, decoding the item entity's metadata may be needed,
  so a receiver doesn't chase a squadmate's unrelated drop.
