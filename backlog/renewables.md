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

## Where they go: production chunks (operator, 2026-11-17)
The long-term shape: whole chunks set aside at sea level for production - one or more for wheat,
one or more for trees - each levelled, fenced round, and entered by gates, so the work is laid
out on a grid the force can plan by the chunk (yield a chunk a day, hands a chunk) rather than
fitted round whatever ground is near the camp. Until then: the camp's farm by its water, and the
keep's grove, cleared for if the ground near the depot is wooded.

Built so far: the keep's grove sited round its depot, any depth under it, and its ground cleared by
the wood party when none qualifies; trees felled whole and replanted (docs/military-doctrine.md § 4).
Left: siting and levelling production chunks; fences and gates round them (fences are classed and
dug today - `ClearingTest.aFenceAndTheGroundUnderItAreDug` - but never built); gates walked through.

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
- **The grove's harvest cycle (operator, 2026-11-14).** A pass that cuts every log in the grove, a
  wait for the leaves to decay (leaves with no log within six blocks drop their saplings over the
  next random ticks - a minute or two), then a pass that picks the dropped saplings up off the ground
  and plants them back on the grid. The planter's forester breaks leaves for a sapling today, and
  outranks the scavenger that would pick dropped ones up: it should take the ones lying in the grove
  first. Large groves, or several, so one is grown while another is cut and nobody waits on a crop.
  Done when: a live run sees the keep's grove grow, cut in one pass, and planted again from its own
  drops.
- **A flat grove at sea level (operator, 2026-11-14).** The grove's ground flattened before it's
  planted, as the keep's is - the foreman's level-by-level plan over a second site - at sea level
  where the depot's ground allows. Today the grove is sited on the flattest ground near the depot as
  it stands.
- 16 to 32 trees growing at once, so wood is never short (operator, 2026-11-14): one grove of
  radius 8 on the four-block grid is 16.
- Nether wart and animal pens - a chicken pen first: feathers are the one part of an arrow with no other
  source but skeletons, and the bow joins every load plan at BUILDUP (backlog/load-plans.md).
- The general's choice follows the terrain. A scenario test with no water nearby never yields a
  wheat order.

## Notes
- Saplings are a side product of the tree gathering we already do. Picking them up is the cheap
  first slice.
- Growth is slow and random (random ticks), so live verification needs patience, or a test
  server with a higher `randomTickSpeed`.
