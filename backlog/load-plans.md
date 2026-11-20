---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-21.18 (foot marches: the fighting load, the approach march load, and what's left with the trains); FM 4-0 (supply discipline; Class II, V)
---

# Load plans: what a soldier carries for its kind of work, and the rest at the depot

## Why
A soldier carries whatever it has made or found: a pack of spare swords, worn pickaxes, emeralds
and odd blocks, and no room for the cobblestone its work digs (operator, 2026-11-15). An army
plans a load by the job: the *fighting load* on the soldier, the rest left with the trains till
it's needed (ATP 3-21.18). Here the trains are the site's depot - the supply dump.

## Decided (operator, 2026-11-15)
The fighting load by kind of work, the best of each kind kept up to its figure:

| Work | Weapons | Bow + arrows | Pickaxes | Shovels | Axes | Food | Torches | Other |
|---|---|---|---|---|---|---|---|---|
| Flattening (the keep's hands) | 2 | 1 + 16, from BUILDUP | 2 | 2 | 2 | 64 | 64 | room to haul what's dug |
| Mining | 2 | 1 + 16, from BUILDUP | 3 | 1 | - | 64 | 64 | 64 cobblestone to bridge caves; ore to the mine's stash |

- **Surplus to the depot:** what's beyond the load - a third sword, a worse pickaxe, emeralds -
  goes to the depot on the soldier's next trip there, with its loads, not a walk of its own. There
  it waits for whoever's short (cross-levelling draws from it).
- **The bow, once mature:** from the camp's BUILDUP - a site that makes what its work consumes -
  every load has a bow and 16 arrows; before that a bow is weight with no arrows behind it. The
  miner's third pickaxe and the 64s (one stack each, `Subsistence.BASIC_LOAD` for food) are the
  first figures, to be refined.
- **Clean the depot out as the force matures:** what's two tiers below the best the force now
  makes - wooden swords once stone is standard, stone once iron is - is cleared from the depot;
  wooden tools burn as the smelter's fuel.

## Done when
- **Built** (docs/military-doctrine.md § 11, "The fighting load by kind of work"): `LoadPlan`'s
  flattening and mining loads - the flattening load every soldier's but a miner's, the farms and woods too (field505), the surplus slots, and `Depositor` putting them in on its trips -
  with its loads, or on a trip of its own at three slots; the tool load two of each.
- A soldier short of a kind draws one from the depot's spares (today it only makes one, or is
  cross-levelled one by a squadmate).
- The miner's third pickaxe made, not only kept.
- The bow and its 16 arrows in every load from BUILDUP, once arrows can be had (Notes).
- The depot's clear-out of old tiers, and wooden tools to the smelter's fuel.
- Live: a keep hand's pack holds its load and what it's hauling, and the depot holds the spares.

## Notes
- **Arrows are the hard part.** An arrow is a flint, a stick and a feather (four from one of
  each). Flint comes from gravel - about one break in ten - which the keep's dig and the mine
  meet; sticks from the grove; feathers only from chickens, so the arrow supply waits on a
  chicken pen (backlog/renewables.md: two chickens, seeds to breed them, a fence). Till then the
  only arrows are skeletons' drops.
- Today's pieces: `WorkLoad.builderTools` (a keep builder's pickaxes and shovels by the blocks
  ahead), `CrossLevel.TOOLS_LOW` (two tools before one is handed over), `Pack` (shedding bulk and
  merging part-stacks), `Hotbar` (the quick-switch layout).
