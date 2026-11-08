---
status: proposed
priority: P2
doctrine: ATP 3-21.8 (consolidation and reorganization); FM 4-0 (cross-leveling)
---

# Cross-leveling beyond logs

## Why
Soldiers hand logs to a squadmate who needs them for a tool, and food to one that has none
(docs/military-doctrine.md § 13). Other shortfalls aren't covered yet:
- a bare-handed soldier while a squadmate carries a spare weapon
- arrows

The stockpile also can't be drawn on for materials: logs go into the supply point's chest but never
come back out.

## Done when
- A bare-handed soldier gets a squadmate's spare weapon, when one exists.
- Arrows are cross-levelled the same way.
- A soldier short of logs, with no squadmate to cover it, draws them from the stockpile when the
  squad is at the supply point. This reuses `SupplyRun`'s chest handling.

## Notes
- **Spares need an inventory classification.** `Inventory` doesn't distinguish a second weapon from
  the held one yet. "Surplus" for each class needs the same keep-what-you-need rule as logs.
- **What a thrown stack holds isn't decoded.** The receiver walks onto any dropped item near it.
  With more than one kind of thing being passed, decoding the item entity's metadata may be needed,
  so a receiver doesn't chase a squadmate's unrelated drop.
