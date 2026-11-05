---
status: in-progress
priority: P1
depends-on: [patrol-base]
---

# The equipment ladder: what's left

## Why
The ladder (docs/military-doctrine.md §11, `bot.craft.Ladder`) runs from a weapon through stone
tools, a shield and iron armour to an iron sword and pickaxe. Two rungs and the live check are
still missing.

## Done when
- **Diamond, only after iron armour is complete.** It needs an iron pickaxe and deep ore. Never
  a luxury tool (a diamond hoe) while the armour is leather or missing.
- **Iron axe,** once chopping takes the best axe in hand. Today `Digger` chops bare-handed, so an
  axe would buy nothing.
- **Seen live,** past what `rearm-to-iron` shows (stone tools and a shield for every soldier):
  - An iron chestplate made and worn. The scenario's 16 ore run out first.
  - A soldier with a shield raises it at a skeleton's arrows and the server blocks them.

## Notes
- Ore is found only where it's exposed within `Digger.SEARCH_RADIUS` and four blocks up or down.
  Ore is mostly underground, so iron comes from cliffs and caves, or not at all. Mining down to
  ore is a separate piece of work.
- An arrow is seen as in flight for a second after it comes into view (`EntityTracker`), since
  velocity isn't decoded. One already lying on the ground that comes into view raises the shield
  for a moment.
