---
status: in-progress
priority: P1
depends-on: [patrol-base]
---

# The equipment ladder: what's left

## Why
The ladder (docs/military-doctrine.md §11, `bot.craft.Ladder`) runs from a weapon through stone
tools and iron armour to an iron sword and pickaxe. Three rungs and one check are still missing.

## Done when
- **Shield.** It's made (1 iron ingot, 6 planks), worn in the off hand, and raised against an
  archer in sight. The rung goes in before iron armour if `CombatScenarioTest` shows that a raised
  shield beats a chestplate against a skeleton. A shield nobody raises is dead weight, which is
  why it isn't on the ladder yet.
- **Diamond, only after iron armour is complete.** It needs an iron pickaxe and deep ore. Never
  a luxury tool (a diamond hoe) while the armour is leather or missing.
- **Iron axe,** once chopping takes the best axe in hand. Today `Digger` chops bare-handed, so an
  axe would buy nothing.
- **Seen live:** a squad told to rearm, away from a patrol base, with trees and stone in reach,
  climbs to stone tools; one with exposed iron ore in reach climbs to an iron chestplate.

## Notes
- Ore is found only where it's exposed within `Digger.SEARCH_RADIUS` and four blocks up or down.
  Ore is mostly underground, so iron comes from cliffs and caves, or not at all. Mining down to
  ore is a separate piece of work.
