---
status: ready
priority: P1
depends-on: [renewables]
doctrine: ATP 3-21.18 (foot marches: the fighting load, the approach march load, and what's left with the trains); FM 4-0 (supply discipline; Class II, V)
---

# Load plans: the depot's spares drawn, the bow, and old tiers cleared out

## Why
A soldier keeps the fighting load of its kind of work and puts the rest in the depot (`bot.craft.LoadPlan`;
docs/military-doctrine.md § 11 holds the figures). The other half isn't there: the spares in the depot are drawn by
nobody - a soldier short of a tool makes one or is cross-levelled one - no load has a bow, and the depot fills with
tiers the force has outgrown.

## What's left
- **A soldier short of a kind is issued one from the depot's spares.** It reports the shortage and its squad leader
  answers with the draw (backlog/soldier-order.md, pre-combat checks) - not an errand of the soldier's own.
- **The miner's third pickaxe made**, not only kept.
- **The bow and 16 arrows in every load from BUILDUP** (operator, 2026-11-15) - a site that makes what its work
  consumes; before that a bow is weight with no arrows behind it. An arrow is a flint, a stick and a feather (four from
  one of each): flint from gravel, about one break in ten, which the dig and the mine meet; sticks from the grove;
  feathers only from chickens, so it waits on the chicken pen (backlog/renewables.md). Till then the only arrows are
  skeletons' drops.
- **The depot cleared of old tiers as the force matures** (operator, 2026-11-15): what's two tiers below the best the
  force now makes - wooden swords once stone is standard, stone once iron is - is cleared out; wooden tools burn as
  the smelter's fuel.
- **Both buckets kept full of water** (operator, 2026-11-22: two buckets in the toolkit, both filled - it will help
  tremendously with the wheat farms). Built: two buckets on every soldier's kit ladder (`Ladder.BUCKETS`). Left: a bucket
  is filled only on the way to a hole (`bot.supply.WaterCarrier`), and only a farm's tender waters; an empty one is to be
  filled at a pond's source when one is passed, and a template's holes watered by whoever lays its ground
  (`app.TemplateGround`) - with two full buckets a soldier makes its own source beside the farm and needs no pond.
- The first figures - the miner's third pickaxe, the stacks of 64 - refined from live wear.
- **The hotbar by the task** (operator, 2026-11-22: the tools for the trade quickly accessible). Built: the sword,
  pickaxe, shovel, axe, a stack of blocks, food and torches each in its slot whatever the task (`bot.supply.Hotbar`,
  docs/military-doctrine.md § 11). Left: the fifth slot by the task - a hoe on a farm, a bucket for a water carrier -
  and each routine taking its tool by selecting its slot, never a swap from the pack (the paver does, `Footing.hold`;
  the wall's repointing still digs its dirt by hand).

## Done when
- In the squad simulation a soldier short of a tool is issued the depot's spare and makes none.
- A mine's cutter short of three pickaxes makes up to three.
- From BUILDUP, with arrows to be had, every load holds a bow and 16 arrows.
- A depot holding stone and wooden tools at an iron-standard camp is cleared of the wooden, and they're burnt as fuel.
- Live: a keep hand's pack holds its load and what it's hauling, and the depot holds the spares.

## Notes
- Today's pieces: `LoadPlan.FLATTENING` and `MINING`, `Depositor` (the surplus trips), `WorkLoad.builderTools`,
  `CrossLevel.TOOLS_LOW` (two tools before one is handed over), `Pack`, `Hotbar`.
- Work loads by role as a Class II requirement of the site are backlog/sites.md (kit by role); a weapon cross-levelled
  into a full pack is backlog/authority-and-missions.md (left from the flatten work).
