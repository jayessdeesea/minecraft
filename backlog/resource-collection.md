---
status: in-progress
priority: P1
doctrine: Class IV supply (FM 4-0)
---

# Collecting resources

## Why
Squads can gather wood around an objective the general names (docs/military-doctrine.md § 12),
but the rest of a materials economy - knowing where resources are, tools, other materials, a place
to put them - doesn't exist yet. It's the coordination problem the project is about: the general
splitting gathering, guarding and moving between squads.

## Done when
- The general knows where resources are: each squad's SITREP reports the nearest stands of trees
  (and later stone) it can see, so the general can send a squad to wood rather than guess.
- Stone: a soldier crafts a wooden pickaxe (three planks and two sticks at a table) and digs stone
  for cobblestone, which needs the pickaxe for a drop.
- A stockpile: soldiers deposit what they gather in a chest (the supply point, or one they place),
  and the LOGSTAT reports what's stockpiled as well as what's carried.

## Notes
- Break times depend on the block's hardness and the tool; the server times every dig, so they must
  be right. Hardness isn't in the data reports - read it from the server's own registry, the way
  `tools/block-type-dumper` reads block types.
