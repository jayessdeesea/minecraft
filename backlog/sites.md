---
status: in-progress
priority: P1
depends-on: [echelon-contracts]
doctrine: ATP 3-37.10 (base camp standard designs, capabilities by echelon); ATP 4-90 (sustainment, Class II/IV/VIII); FM 4-0 (levels of supply); FM 3-99 (lodgment phases); ATP 3-94.4 (reconstitution)
---

# Sites: each a kind and a level, gated by its supply

## Why
The force takes on more than it can sustain: a dig wears out its tools with nothing to make more from, and throws
material away for want of chests. Sites and their levels are data and the camp's rations and wood are counted in days
of supply (docs/military-doctrine.md § 17), but nothing acts on either: no gate stops new work, only the camp is
supplied by class, the mine base stops at a table, a furnace and a stash, and no timetable says whether the force is
behind. Where a site goes and what it holds is the site's knowledge, never the soldier's.

## What's left

### The mine base
A phase starts when the one before it stands (operator: water waits for the first iron; wheat grows at the mine base;
no tree farm underground - wood is hauled down, backlog/renewables.md).

| Capability | Fixture | Bill of materials | Phase | Left |
|---|---|---|---|---|
| Store | 2 chests, recesses beside the main tunnel's second cell | 16 planks | 1 | the second chest |
| Light | torches down the stairs and at the landing's stand | coal or charcoal, sticks | 1 | the tunnels |
| Water | an infinite source: two buckets | 3 iron a bucket | 2 | all |
| Food | 3x3 wheat round the water, under torches (light 9+) | seeds, a hoe | 3 | all |

- Its standards in `command.Site`: SHELTER the landing sealed, BUILDUP water, ESTABLISHED chests by class.
- Logistics works out the loads: the blueprint's materials, less what's at the site and what the squad carries, become
  loads ("bring 20 logs down", "smelt 6 iron here"). A squad going down carries a stack of wood, its cutter a spare pickaxe.
- **Stores by class** (FM 4-0; ATP 4-42): one chest a class at a site - food, kit, materials, ore and ingots - so a
  draw of one doesn't open the others and a full chest of one doesn't block another. A sign over each names its class,
  for the operator: one that disagrees with what's inside is a fault to see.

### Kinds and levels
| Level | Means | Construction standard (ATP 3-37.10) |
|---|---|---|
| SHELTER | a squad survives a night here | none: a hasty hut |
| AIRHEAD | nothing spawns on the ground; walled or sealed; its workers armed | initial |
| BUILDUP | the site makes what its work consumes | initial -> temporary |
| ESTABLISHED | stores at their stockage objective; its workers armoured | temporary |
| SEMI-PERMANENT | surplus that supplies other sites; laid out to its final plan | semi-permanent |

| Site | SHELTER | AIRHEAD | BUILDUP | ESTABLISHED | SEMI-PERMANENT |
|---|---|---|---|---|---|
| Camp | patrol base | lit, walled, stone swords | farm, grove, mine | armour; ~160 cells | N/A: the main base takes over |
| Mine base | landing sealed | landing lit; table, furnace | water | chests by class | branch levels (backlog/mining.md) |
| Keep | N/A: supplied from the camp | DEPOT stocked | DIG, SLAB | fields; ~1,000 bread | the 12-chunk ring (backlog/cells.md) |
| Outpost | patrol base | one chunk lit, fenced, gated | ~80 cells | ~256 bread | N/A |
| Nether foothold | sealed | N/A: can't be lit safe | N/A: no water | stores brought in | N/A |

- Left: the keep's fields and stores; outposts and the Nether foothold; `Foothold` folded into `Site` (a site of the
  kind "camp") rather than read into it; the scoreboard reporting each site's level.
- **More than one base.** The home base stays the camp the operator names (`minecraft.camp`), within 100 blocks of
  x=0 z=0, and the biggest. Others each have a kind and are measured on their own standards (a mine level's farm is
  the wheat round its water; the Nether can't be lit safe). A squad's readiness is the level of the base it stands at.
- **Exit criteria** (the operator's building guide, 2026-11-09), as standards: to leave the foothold - a bed, a stone
  pickaxe, axe and sword, 16 food, 32 torches (or their coal and sticks), 2 stacks of cobblestone; to leave the base -
  iron tools and armour, a renewable food source inside the base, storage sorted by category, 3 stacks of torches.
- **An outpost** is a single-chunk cell at a resource site, surface or mining level: walls, roof, full light, a table
  and furnace, storage for its output, and an emergency stash chest (food, a weapon, a pickaxe, torches). No work site
  more than about 200 blocks from a shelter. Retired by removing the stash and leaving the light.

### Sizing (operator, 2026-11-12)
A hydrated wheat cell gives about 1.7 wheat an hour; a working soldier eats 2 to 3 loaves an hour. So about 5 cells
feed a soldier, about 20 build its basic load (64 loaves) within a working day as well, and a store of N loaves within
a day takes about N / 5 more. First estimates, refined from the dawn AAR.

| Site | Supports | Wheat cells | Bread in store | Layout |
|---|---|---|---|---|
| Foothold | the force while it arms and armours (2 squads, 8 soldiers) | ~160 | none beyond the basic loads | a wall, lit ground, a farm, a mine |
| Outpost | one squad visiting, resupplied and sent on | ~80 | a basic load a soldier: ~256 | one chunk, fenced and gated, lit ground round it (backlog/cells.md) |
| Main base | several squads back from missions depleted | ~20 a soldier it feeds, plus the store's | a basic load a soldier and a reserve: ~1,000 for 4 squads | 2x2 chunks |

The other stores - logs, stone, ore and ingots, torches - by the same rule: what the squads a site supports will draw
before it can make more. Priorities: food and shelter first and kept up; then light, weapons and armour. Night work in
the lit compound is a later level's standard: lit, armed, armoured and slept.

### Supply: every class, by site, and acted on
| Class | Here | Made from | Left |
|---|---|---|---|
| I, subsistence | bread, cooked meat | wheat, animals | by site; the committed burn |
| II, individual equipment | tools, weapons, armour, shields | wood, stone, ore | all |
| III, fuel | coal, charcoal | coal ore, logs | all |
| IV, construction | logs, cobblestone, chests, torches | trees, stone | by site |
| V, ammunition | arrows | sticks, flint, feathers | all |

Wood is the input to II, III and IV at once, so a site's wood is budgeted as one resource across the three.
- **Burn**: upkeep, measured as a moving average (it doesn't stop when the site pauses), plus committed work, planned
  from the missions' bills of materials (backlog/mission-estimate.md) and never measured - drawn-yesterday falls while
  a site is paused. Left: the committed burn for classes other than IV.
- **Levels** (`command.Supply`): safety (two days of upkeep), lead time, reorder point (safety plus the lead time's
  burn), stockage objective (reorder plus a day of committed work). Left: lead times measured from the producers'
  output, not first figures.
- **The gate** (operator, 2026-11-15: the force is prepared before it expands; work under way goes on with inferior
  tools rather than waiting). A site starts new work - a mission supplied from it, its next level - only if its stock
  after that work's draw stays at or above the reorder point. It is an input to the platoon leader's one decision
  (backlog/authority-and-missions.md step 5), never a rule rewriting an order already given.
- **A production check beside it**: each class reports production against requirement over the day; a class made
  slower than it's needed at steady state would hold the gate shut for ever. Who is put on the shortfall is the
  platoon leader's task to a squad and the squad leader's sector (authority-and-missions.md steps 4 and 5).

| State | When | What it does |
|---|---|---|
| ADVANCE | every class at its stockage objective, held over the day | the site may take on its next level's work, and supply another |
| PAUSE | any class projected below its safety level within its lead time | no new work that draws that class; work under way goes on with what it has; the spare produce it |
| RECONSTITUTE | a loss that takes a class below its operating level at once (a creeper at the depot, deaths that drop the kit) | contract to the sites that can be supplied; mothball the rest; build back up |

Between the safety level and the objective a site carries on with the work it has and starts none (`Supply.State.HOLD`).
Left: RECONSTITUTE, and all three acted on. Down at once, up only at the dawn after-action review and only if the class
held its objective at `STEADY` (90% of the day's checks); a site rises at most one level a dawn.

### What's authorised at each level
| Level | Floor: the least every soldier there has | Ceiling (built, `Ladder.authorised`) |
|---|---|---|
| SHELTER | a weapon (wooden at least) | stone; of iron only the iron pickaxe |
| AIRHEAD | stone sword and pickaxe | as SHELTER |
| BUILDUP | a shield; the best armour to hand | iron tools for producers |
| ESTABLISHED | iron for all | diamond for weapons and armour, never hoes |

- Left: the floors. Between floor and ceiling a soldier has the best its materials allow (operator: materials are
  traded off, not waited for). A floor says what comes first; it isn't a C-rating.
- **A scarce material's uses are ranked**: 1. the survival floor; 2. enablers - the first iron pickaxe, a bucket;
  3. the main effort's tool load before anyone's iron sword; 4. the rest. The platoon leader sets the ranking for each
  scarce material, shown as computed state; the platoon sergeant applies it at issue.
- **Kit by role** (operator, 2026-11-14): protection and work apart. A miner's, a woodcutter's and a farmer's work
  load, as a keep builder's (`bot.craft.WorkLoad`): the tools the task wears out, sized from the task plus a safety
  level, a Class II requirement of the site; the ladder's own tool-load rungs then dropped. Topping up from the depot
  is backlog/load-plans.md. Protection by exposure: on lit, walled ground the floor and no more until the work load is
  met; miners and soldiers outside the walls get armour first.

### Losses priced in (operator, 2026-11-21: win the war, not every battle)
Deaths a soldier-hour by area are in the dawn scorecard. Left:
- every `[Casualty]` tagged with its activity, area, hostile, and the soldier's health, armour and kit at contact;
- each area's yield a soldier-hour beside its loss rate, computed and shown to the platoon leader;
- the combat matrix and squad simulation pricing an activity in a ground before the force takes it on, so a ring is
  taken in when its yield outruns its price and given up when the price outruns the yield. Over-extension is an
  area's price rising faster than its yield.

**One small flat base first** (operator, 2026-11-21): a single small flat base in the airhead, food and wood made
there reliably before it expands; any height in the airhead, sea level in later phases, no multi-level ground. Left:
the old keep site's depot consolidated into the supply point (backlog/renewables.md); expansion gated on food and wood.

### The timetable
Each level due by a day, measured at that day's dusk (day tick 12000); "all" is every soldier alive then. A target to
measure against, not a script. A standard may carry a lead (operator, 2026-11-14): the farm planted by AIRHEAD, feeding
by BUILDUP.

| Day | Level | Standard by dusk |
|---|---|---|
| 1 | Airhead seized | All armed (a wooden sword at least) by noon. Each squad sealed and lit in a hasty hut at dusk. Two food each carried or eaten. No falls. |
| 2 | Airhead consolidated | All with a stone sword and pickaxe. The camp's ground lit. A farm planted. Each squad's hut within the camp. Health back to full by dusk (food 18 or more). The mine cut: it is the camp's quarry. |
| 3 | Airhead line held | The wall closed, with its lip and a gate. A tree farm planted. A stockpile chest of logs and cobblestone. Shields for all. First bread. |
| 4-5 | BUILDUP | A mine cut to y=16 with a lit landing. Copper, then iron armour for half the soldiers. Two rations each carried. One barracks with a bed a soldier (backlog/rest-plan.md). |
| 6-7 | ESTABLISHED | Iron armour, sword and pickaxe for all. The wall lit on top. A furnace, table and chests in the camp. Casualties under one a day. C-1 for every squad. |
| 8+ | SEMI-PERMANENT, then expansion | Branch tunnels and a base at a second mine level (backlog/mining.md). Diamonds. A Nether foothold. |

### What the platoon leader sees
A `<sites>` section in its view and escalations, as `<foothold>` is now: a line a site - kind, level and the level due
by the timetable, state, and each class short of its objective in days of supply with production against requirement.
No prose ladder in a system prompt (docs/prompting.md). It waits on the platoon leader having a decision it informs -
choosing among costed missions (backlog/mission-estimate.md).

## Done when
- In the squad simulation a MINE squad builds the mine base's phase 1 from the blueprint, raw iron is smelted at the
  landing, and what doesn't fit in the packs goes into the site's chests by class.
- Supply is measured by site for all five classes; `[Supply]` and the dawn AAR list them.
- The next mission doesn't start while the camp's projected stock after its draw is below the reorder point; a dig
  under way never stops for a short depot.
- `PlatoonLeaderScenarioIT`: a site in PAUSE on wood, where the platoon leader sends nobody further out; a
  creeper-struck depot, where it contracts; a material short with two claims on it, where the enabler gets it.
- The timetable is data in `command` (each level's due day and standards), so "behind schedule" is computed; the
  platoon leader's view names the day and what's late; the AAR names the day's standard and each squad's C-rating at dusk.
- The scoreboard reports each site's level, state and short classes.
- An outpost and a main base, when built, are laid out to the sizing table.
- Live: a fresh garrison started at a dawn meets day 1's and day 2's standards and holds every later one it reaches;
  a full layer is levelled without a builder idle for want of a tool.

## Notes
- **The order of building (operator, 2026-11-22):** build a step at a time, so as not to overextend - what that means
  is still being learned.
  1. *Now, the bootstrap.* The aim is the permanent structure of step 2, and there is a lot to stand up before it: a
     reliable source of food first - the templated wheat farm chunks - then wood from the templated tree farm chunks
     (backlog/renewables.md), supply dumps to hold what they yield, and so on; from a temporary base by the water,
     where less ground has to be cut down. What the temporary structure is isn't settled. Which comes first and where
     each goes is the platoon leader's to decide (backlog/authority-and-missions.md step 1).
  2. *Once supply is mature:* a large permanent structure at x=0, z=0, at sea level.
  3. *As control of the ground grows:* smaller permanent structures - in the overworld to trade with the villagers, in
     the Nether to gather what's there, and so on.
  So the Keep stays a site of its own and its standards stand, but nothing is built at the origin until the camp's
  reserves say so: the gate is this item's levels of supply, not a date. The camp by the water is the temporary
  base; "one small flat base first" (2026-11-21) is step 1.
- Decided (operator): no `spawnpoint` - a respawned soldier builds its way down (backlog/mobility.md); friendly fire
  on and kit dropped at death (`pvp`, `keepInventory` at their defaults).
- From the operator's building guide (2026-11-09), stated nowhere else: a bed only in a cell that meets every
  invariant, checked before sleeping, since it sets the respawn point; after a death the nearest stash, the kit
  recovered only within its 5-minute despawn; no flammable roof or wall shared across cells, no wood within a block of
  lava or fire; clear sky 32 blocks out with a lit band beyond, extended ring by ring; no moat (operator, 2026-11-20).
  Its architectural vocabulary (Alexander, Kahn, Wright, Ando, Palladio; no curves or bare minimalism at block scale)
  is a style for the engineer's blueprints (backlog/echelon-contracts.md) once there's a deliberate base to style.
