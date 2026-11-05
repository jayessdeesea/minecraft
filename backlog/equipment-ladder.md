---
status: in-progress
priority: P1
depends-on: [patrol-base]
---

# The equipment ladder: what's left

## Why
The ladder (docs/military-doctrine.md §11, `bot.craft.Ladder`) runs from a weapon through stone
tools, a shield and iron armour to iron tools. Diamond is still missing.

## Done when
- **Diamond, only after iron armour is complete.** It needs an iron pickaxe and deep ore. Never
  a luxury tool (a diamond hoe) while the armour is leather or missing. Live, the ladder is seen
  to the chestplate (`rearm-to-chestplate`); the rungs above it are the same machinery.

## Notes
- Ore is found only where it's exposed within `Digger.SEARCH_RADIUS` and four blocks up or down.
  Ore is mostly underground, so iron comes from cliffs and caves, or not at all. Mining down to
  ore is a separate piece of work.
- Whether the server blocked an arrow is read from health, not from its own block event, which
  isn't decoded.
- An arrow is seen as in flight for a second after it comes into view (`EntityTracker`), since
  velocity isn't decoded. One already lying on the ground that comes into view raises the shield
  for a moment.
