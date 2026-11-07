---
status: proposed
priority: P3
depends-on: [patrol-base, subsistence]
doctrine: ATP 3-37.10 (base camps)
---

# Renewable resources: tree and food farms

## Why
Gathering strips what's near and moves on. A force that holds ground for days needs supply that
grows back where it stands. Which kind is possible depends entirely on the terrain. That's a real
planning decision for the general: read the survey, pick what the ground supports.

## Doctrine
**Base camps** (ATP 3-37.10): a camp that is held longer develops its own sustainment instead of
living off resupply runs. What gets built depends on the site.

## Mapping to Minecraft
What the ground supports:

| Farm | Needs | Not possible where |
|---|---|---|
| Tree farm | Saplings (they drop from leaves when a tree is cut), dirt or grass, light 9+ | Nether: crimson and warped fungi need nylium and bone meal, and their stems make no charcoal |
| Wheat | Seeds (from breaking grass), a wooden hoe (2 planks and 2 sticks), farmland within 4 blocks of water, light 9+ | Nether: water evaporates (`water_evaporates` in its dimension type) |
| Nether wart | Soul sand. Wart is found only in fortresses | Overworld: it can be grown there, but has to be brought back |
| Animal pen | Two of a kind plus their breeding food, and a fenced pen | Wherever no passive mobs spawn: deserts, much of the Nether |

The survey (§ 12) reports what's nearby. The general picks the farm the ground supports, the squad
builds it beside the patrol base (backlog/patrol-base.md), and the base's light already meets the
light requirement.

## Done when
Built: wheat (docs/military-doctrine.md § 12, FARM) and a tree farm planted from saplings it breaks
leaves for (§ 12, TREE_FARM). What's left:
- **Trees regrown and cut.** A live run that sees saplings in a squad's tree farm grow and a WOOD
  squad chop them, and soldiers picking up the saplings that fall while they chop, rather than
  breaking leaves for them.
- Nether wart and animal pens.
- The general's choice follows the terrain. A scenario test with no water nearby never yields a
  wheat order.

## Notes
- Saplings are a side product of the tree gathering we already do. Picking them up is the cheap
  first slice.
- Growth is slow and random (random ticks), so live verification needs patience, or a test
  server with a higher `randomTickSpeed`.
