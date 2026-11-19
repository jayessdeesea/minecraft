---
status: in-progress
priority: P1
depends-on: [echelon-contracts]
doctrine: ATP 3-37.10 (base camp standard designs, capabilities by echelon); ATP 4-90 (sustainment, Class II/IV/VIII); FM 4-0 (logistics estimates)
---

# Sites: a base is a set of capabilities, with a bill of materials

## Why
A player who mines sets up a working base at the level they mine: water, a table, a furnace,
chests for what doesn't fit in the inventory, light, and somewhere to eat. They carry down what
it needs, or win it on the spot. Where a base goes and what it holds is the base's knowledge,
not the soldier's: a soldier moves, fights, digs, places, carries, and crafts at a table it's
sent to.

## Built so far
Workshops (docs/military-doctrine.md § 11): the engineer sites the camp's, a hasty one at an
objective, and the mine base's at the landing; a soldier crafts only at one, and fits a missing
table or furnace into the cell the workshop names.

## The mine base, by phase
A phase starts when the one before it is standing: phase 2 needs iron, which phase 1's furnace
smelts.

| Capability | Fixture | Bill of materials | Phase | |
|---|---|---|---|---|
| Craft, smelt | table and furnace in the landing's recesses | 4 planks; 8 cobblestone | 1 | built |
| Store | 2 chests, recesses beside the main tunnel's second cell | 16 planks | 1 | the surface workshops' chest built (docs/military-doctrine.md § 12); the mine base's first, its stash of ore (§ 16); its second not |
| Light | torches down the stairs and at the landing's stand | coal or charcoal, sticks | 1 | built; the tunnels unlit |
| Water | an infinite source: two buckets | 3 iron a bucket | 2 | |
| Food | 3x3 wheat round the water, under torches (light 9+) | seeds, a hoe | 3 | |

- **Decided (operator):** water waits for the first iron, as phase 2; wheat grows at the mine
  base; no tree farm underground - wood is hauled down from the surface (backlog/renewables.md).
- **Logistics works out the loads:** the blueprint's materials, less what's at the site and what
  the squad carries, become loads - "bring 20 logs down", "smelt 6 iron here" - carried if the
  squad has them, gathered if not. A squad going down carries a stack of wood, and the cutter a
  spare pickaxe, since the nearest workshop above the landing is the top of the stairs.

- **Stores by class of supply.** Doctrine stores classes apart: ammunition (Class V) separated from
  fuel (Class III) and from everything else, food (Class I) kept clean and dry, each storage area
  marked (FM 4-0; ATP 4-42 on Class I storage). Here: one chest per class at a site - food, kit
  (weapons and armour), materials (logs, cobblestone), ore and ingots - so a soldier drawing one
  doesn't open, scan and hold the others, and a full chest of one class doesn't block another.
- **Signs on the chests.** A sign above each chest naming its class, for the operator: a soldier
  needs no sign, since the site's blueprint says which chest holds what - but the operator walking
  the camp reads the signs, and a sign that disagrees with what's inside is a fault to see.

## Done when
- In the squad simulation, a MINE squad builds the mine base's phase 1 from the blueprint; raw iron
  is smelted at the landing, and what doesn't fit in the inventories goes into the site's chests.
- The dawn review reports each site's capabilities, standing and missing.

- **More than one base** (from backlog/readiness.md, merged here; each squad's readiness at the camp is built,
  docs/military-doctrine.md section 17). The home base stays the camp the operator names (`minecraft.camp`), within 100
  blocks of x=0 z=0 so the operator can find it, and the biggest. Others each have a kind - a mine level (y=32, y=0
  and so on), another dimension's foothold - and are measured through the lodgment's phases on their own standards
  (a mine level has no farm; the Nether can't be lit safe). A squad's readiness is the phase of the base it stands
  at. The platoon leader is shown readiness once a live miss shows it needs to: it already keeps an exposed squad
  near shelter (`exposed-far-out`).

## Notes
- **Naming:** `command.Foothold` today is the camp's phased standards. Once a site has a type, it
  becomes a site of the type "camp", and the mine base one of the type "mine".

## Merged from sites.md (2026-11-19): Site standards: the style guide for a foothold, an outpost and a base

The operator's direction (2026-11-12). Each kind of site has a standard for what it grows, what
it holds in store and what it builds, sized by who it supports. These are the numbers the
engineer lays a site out to and the general measures it by; each fact here, once built, moves to
the site's own doc and is stated nowhere else.

### Priorities
- **Food and shelter first, and kept up**, at every site.
- **Then light, weapons and armour** - which later in a run become worth more than physical
  security: with the ground lit, the soldiers armed and armoured, and sleep enough to keep
  phantoms away, a squad can work at night with little worry - keeping a farm inside a lit area,
  where torchlight keeps monsters from spawning. Night work is a later phase's standard, not a
  foothold's: until then, the squads seal in at night.

### Sizing
Measured live (field123-125): a hydrated wheat cell ripens about once in 35 minutes, so a cell
gives about 1.7 wheat an hour - a little over half a loaf; a soldier working eats about 2 to 3
loaves an hour. So:
- **To feed a soldier:** about 5 cells.
- **To build its basic load** (64 loaves) within a working day as well: about 20 cells a soldier.
- **To stock a store** of N loaves within a day: about N / 5 cells more.

| Site | Supports | Wheat cells | Bread in store | Notes |
|---|---|---|---|---|
| Foothold | the force while it arms and armours (2 squads, 8 soldiers) | ~160 (built: grown toward 192, 156 where the ground allows) | none beyond the basic loads | minimal: a wall, lit ground, a farm, a mine |
| Outpost | one squad visiting, resupplied and sent on | ~80 | one basic load for each soldier of a squad: ~256 | one chunk, fenced and gated, a light moat round it (backlog/cells.md) |
| Main base | several squads coming back from missions depleted | ~20 a soldier it feeds, plus the store's | a basic load for every soldier it supports, and a reserve: ~1,000 for 4 squads | 2x2 chunks; the abundance is the point - squads come back empty |

The same rule holds for the other stores - logs, stone, ore and ingots, torches: a site holds
what the squads it supports will draw before it can make more, through the common stockpile
(docs/echelons/tenets.md, specialists produce, missions consume).

### Done when
- The foothold's food standard reads in these terms - cells growing against cells wanted, bread
  in store against its stockage objective - and the general sees both.
- An outpost and a main base, when built (backlog/cells.md, backlog/sites.md), are laid out to
  this table.
- A later phase's standard: night work in the lit compound, once lit, armed, armoured and slept.

### Notes
- The figures are first estimates from three runs; refine them from the dawn AAR as they're measured.

## Merged from sites.md (2026-11-19): Maturity model: what a site can sustain before it expands

### Why
The force runs out of basic supplies because it takes on more than it can sustain. Live
(2026-11-14): levelling the keep, the builders wore out their tools and had nothing to make new
ones from, and threw away material for want of chests to put it in. That's overextension, and
doctrine has a name for where it ends: the *culminating point*, where an attacking force can't go
on because of supply problems, the enemy, or the need for rest. Avoiding it rests on *operational
reach*, how far and for how long a force can work from its base.

Real forces don't measure that by structures built. They measure stocks in *days of supply*
against levels set in advance, they pause before culminating, and when a loss knocks them back
they reconstitute: they contract to what they can hold and build up again. This item puts the same
three things - a gate, a pause and a fallback - over the phases the force already has, so the
platoon leader can't send it further than its supply reaches.

It also brings the six ladders measured today into one: the lodgment's phases (§ 17), the
roadmap's days, the site standards, the mine base's phases (sites.md), the keep's DEPOT/DIG/SLAB
(flatten-campaign.md) and readiness away from the camp (sites.md, More than one base).

### Levels
The lodgment's, for every site. A level says what a site can do; whether it may go further is
the supply gate below.

| Level | Means | Construction standard (ATP 3-37.10) |
|---|---|---|
| SHELTER | a squad survives a night here | none: a hasty hut |
| AIRHEAD | nothing spawns on the ground; walled or sealed; its workers armed | initial |
| BUILDUP | the site makes what its work consumes | initial → temporary |
| ESTABLISHED | stores at their stockage objective; its workers armoured | temporary |
| SEMI-PERMANENT | surplus that supplies other sites; laid out to its final plan | semi-permanent |

Each kind of site fills in what a level means there, and a cell can be N/A:

| Site | SHELTER | AIRHEAD | BUILDUP | ESTABLISHED | SEMI-PERMANENT |
|---|---|---|---|---|---|
| Camp | patrol base (§ 14) | lit, walled, stone swords (§ 15) | farm, grove, mine (§ 17) | armour; ~160 cells | N/A: the main base takes over |
| Mine base | landing sealed | landing lit; table, furnace | water | chests by class | branch levels (mining.md) |
| Keep | N/A: supplied from the camp | DEPOT stocked | DIG, SLAB | fields; ~1,000 bread | the 12-chunk ring (cells.md) |
| Outpost | patrol base | one chunk lit, fenced, gated | ~80 cells | ~256 bread | N/A |
| Nether foothold | sealed | N/A: can't be lit safe | N/A: no water | stores brought in | N/A |

### Supply levels: the gate
**Classes.** The force's materials, in the Army's classes of supply:

| Class | Here | Made from |
|---|---|---|
| I, subsistence | bread, cooked meat | wheat, animals |
| II, individual equipment | tools, weapons, armour, shields | wood, stone, ore |
| III, fuel | coal, charcoal | coal ore, logs |
| IV, construction | logs, cobblestone, chests, torches | trees, stone |
| V, ammunition | arrows | sticks, flint, feathers |

Wood is the input to II, III and IV at once - handles, charcoal, chests, torches - so a site's wood
is budgeted as one resource across all three, never class by class.

**The burn rate has two parts.** Each class at each site is drawn by:
- **Upkeep:** what running the site costs whatever else it does - bread for the soldiers it
  feeds, torches for its lit ground, tools worn by its standing work. Measured, as a moving average
  over the last few days: upkeep doesn't stop when the site pauses, so measuring it is safe.
- **Committed work:** what the missions it supplies will draw, from their bills of materials
  (backlog/mission-estimate.md) - the keep's next level in tool wear and chests, an outpost's
  starting stock. Planned, never measured: drawn-yesterday falls while a site is paused, and
  would open the gate on stock the work can't live on.

**Days of supply** is the stock over the day's burn, upkeep and committed work together; an
in-game day is the unit.

**Levels of supply, adaptive.** For each class at each site (FM 4-0), set from the burn rate, not
fixed counts:
- **The safety level:** what the site needs to keep running through a replenishment that's late or
  a loss - the floor under the camp. In an airhead, doctrine wants three days of supply on hand,
  two at least (FM 10-27). First figure: two days of upkeep.
- **The lead time:** how long the class takes to make more - a grove's regrowth and harvest, wheat's
  ripening, a mining trip - measured from the producers' output.
- **The reorder point:** the safety level plus what's burnt during the lead time (the Navy's *low
  limit*). The question it answers: at this burn rate, will the stock fall below what the camp needs
  to run before more can be made?
- **The stockage objective:** the reorder point plus one day of committed work.

**The gate.** Projected forward, not read off the stock. Decided (operator, 2026-11-15): the force
is prepared before it expands - a new mission, or a site's next level, waits on the gate - and work
under way doesn't wait for better kit: it goes on with inferior tools (stone, or none) until it's
safe to expand, as the basis of issue below caps them.
- A site starts new work - a mission supplied from it, the next level's work - only if its stock
  after that work's draw stays at or above the reorder point.
- When the projection crosses the reorder point, the platoon sergeant tasks producers - built for
  wood: the keep's wood party goes out at Class IV's reorder point and back at its objective
  (`PlatoonSergeant.Wood`); and food: a farmer a squad under Class I's reorder point, half of it
  under its safety level (`PlatoonSergeant.farmers`) - it detaches
  soldiers to the work within the platoon leader's priorities, as the foreman shares out the keep
  (backlog/fleet-command.md). The platoon leader sees it only as state - "wood: PAUSE, two on it" -
  under specialists produce, missions consume (tenets.md).
- When it would cross the safety level before the lead time is out, the site pauses: stop, and
  fill up.

**A production check, beside the gate.** A gate stops expansion; it doesn't make supply. If a
class's production is below its requirement at steady state, the site waits for ever, so each
class also reports production against requirement over the day, and the platoon sergeant gives a
shortfall producers - a second grove, a second miner - rather than waiting on it.

### Pause, fall back, and holding a level
**Three responses, by how far a class has fallen:**

| State | When | What it does | Doctrine |
|---|---|---|---|
| ADVANCE | every class at its stockage objective, held over the day | the site may take on its next level's work, and supply another | operational reach |
| PAUSE | any class projected below its safety level within its lead time | no new work that draws that class; work under way goes on with what it has, inferior tools rather than none; everyone spare produces it | operational pause |
| RECONSTITUTE | a loss that takes a class below its operating level at once: a creeper at the depot, deaths that drop the kit | contract to the sites that can be supplied; mothball the rest; build back up | reconstitution (ATP 3-94.4) |

Between the safety level and the objective the site carries on with the work it has, and starts
none.

**Hysteresis.** The levels themselves give the dead band: the site advances at the stockage
objective and pauses at the safety level, and the operating level lies between them. Two more
damp it:
- **Down at once, up at a decision point.** A PAUSE or a RECONSTITUTE takes effect at the check
  that finds it, as a commander raises force protection at once. An ADVANCE takes effect only at
  the dawn after-action review, and only if the class held its objective at `STEADY` (90% of the
  day's checks): live, lit ground and the wall scored at single looks went in and out of met 36
  times a run (§ 17).
- **One transition a day upward.** A site rises at most one level a dawn.

### What's authorised at each level
**Basis of issue.** Each level authorises a floor - the least every soldier there has - and a
ceiling - nothing above it is made yet:

| Level | Floor | Ceiling |
|---|---|---|
| SHELTER | a weapon (wooden at least) | stone; nothing that costs iron but the iron pickaxe, the enabler, at any level (operator, 2026-11-15) |
| AIRHEAD | stone sword and pickaxe | as SHELTER |
| BUILDUP | a shield; the best armour to hand | iron tools for producers |
| ESTABLISHED | iron for all | diamond for weapons and armour, never hoes |

**Best available, not best possible.** Between floor and ceiling a soldier makes the best its
materials allow: copper armour now over none while it waits for iron (operator: materials are
traded off, not waited for).

*Built:* the ceilings, by the camp's level (`Ladder.authorised`; docs/military-doctrine.md § 11).
The floors - what every soldier at a level must have first - and the priority of use below aren't.

**When a material is scarce, its uses are ranked** - a controlled supply rate, which doctrine
sets so that issues never take a stock below its safety level:
1. **The survival floor:** no soldier below its floor goes without the cheapest thing that lifts it.
2. **Enablers:** what opens a production chain - the first iron pickaxe (diamonds, emerald ore),
   a bucket (water, so a farm). Each multiplies what comes after it.
3. **The main effort's work:** its tool load before anyone's iron sword.
4. **The rest.**

The platoon leader sets the ranking for each scarce material, since that's a priority across the
platoon, and it's shown as computed state; the platoon sergeant and the soldiers apply it when
they issue and craft (tenets.md: the leader owns what, the soldier how).

### Kit by role: protection and work apart (decided, operator 2026-11-14)
The equipment ladder (§ 11) is one list ranked by survival gained per resource spent. That's right
for protection, and wrong for tools: the tool load is rung 13, after iron armour and diamond, and
a spare is one more than carried. A builder on the main effort climbs through armour before the
ladder gives it a second shovel, and a dig that wears out ~42 tools has a spare each.
- **The protection ladder:** weapons, the shield, armour - rungs 1-3, 7-10 and 12 as measured by
  `CombatScenarioTest`. Ranked by survival per resource, as now.
- **The work load, by role:** the tools a role's task wears out, sized from the task - blocks to
  dig over uses a tool, plus a safety level - carried as a load and topped up at the site's depot.
  A miner, a builder and a farmer each have their own; a rifleman almost none. A Class II requirement
  of the site it works from, in the gate above.
- **Protection by exposure.** A builder on lit, walled ground holds the protection floor (a stone
  sword) and no more until the work load is met; miners and soldiers working outside the walls get
  armour first. The priority of use above decides when the two claim the same iron.

### What a model sees
*Not yet:* the `<sites>` view waits on the platoon leader having a decision it informs - choosing
among costed missions (mission-estimate.md) - as the keep rule decides its days today; a section it
can't act on is tokens and a distraction (docs/prompting.md).

No prose ladder in a system prompt (docs/prompting.md: a rule in prose loses to the state it
describes). An escalation to the platoon leader carries, and its standing view shows, a `<sites>` section, as `<foothold>` is now. A line for each site: its kind, its level
and the level due by the timetable, its state (ADVANCE, PAUSE, RECONSTITUTE), and each class short
of its objective in days of supply, with production against requirement. The camp's line is
today's `<foothold>`.

### Done when
- **Sites and levels as data are built** (`command.Site`; docs/military-doctrine.md § 17): the camp
  from its foothold, the keep and the mine base by what can be measured of them today, `[Sites]` on
  each change, the dawn AAR's list. What's left: `Foothold` folded into `Site` rather than read into
  it; the mine base's sealed landing and water, the keep's fields and stores; outposts and the Nether
  foothold; and the `<sites>` section in the platoon leader's view - a prompt change, measured with
  `PlatoonLeaderScenarioIT`.
- **Days of supply are measured** (`command.Supply`; docs/military-doctrine.md § 17): the camp's
  Class I and Class IV, upkeep from the stock's falls, the levels and the state, `[Supply]` on each
  change, the dawn AAR's list. What's left: by site, not the force's; Classes II, III and V; the
  committed burn for classes other than Class IV, whose comes from the keep's estimate; lead times measured
  from the producers rather than first figures; and the state acted on - the gate, the PAUSE, the
  production check.
- The next mission (mission-estimate.md, the operator's order of missions) doesn't start while the
  camp's projected stock after its draw is below the reorder point; the keep's dig, under way, never
  stops for a short depot.
- The scoreboard reports each site's level, and both it and the dawn AAR its state and short classes
  (the AAR lists each site's level now).
- The work load for the other roles: a keep builder's is built (`bot.craft.WorkLoad`;
  docs/military-doctrine.md § 11); a miner's, a woodcutter's and a farmer's are to come, topped up at
  the site's depot rather than only from what's carried, and the ladder's own tool-load rungs then
  dropped.
- `PlatoonLeaderScenarioIT`: a site in PAUSE on wood, where the platoon sergeant tasks producers, and the platoon leader sends nobody
  further out; a creeper-struck depot, where it contracts; a material short with two claims on it,
  where the enabler gets it.
- Live: the keep levels a full layer without a builder standing idle for want of a tool.

### Notes
- **The waste, checked (2026-11-15):** the crafting is not the fix. From the Crafter lines of
  field160-202, logs spent per item made: a stone pickaxe 0.13 (the 0.125 its sticks need), a stone
  shovel 0.06, a stone sword nothing, a crafting table 3 in all against 106 tools. "Half a log a
  handle" was wrong. Wood goes into chests (two logs each) and smelting fuel (about a log a piece of
  copper armour): Class III, budgeted with the rest of the wood.
- **Decided (operator, 2026-11-14): lead time.** The roadmap plants a farm on day 2; § 17 puts food
  in the BUILDUP. A standard may carry a lead - the farm planted by AIRHEAD, feeding by BUILDUP - so
  the cheap first step comes early without making food an AIRHEAD target, which cost the morning
  (docs/prompting.md).
- **Decided (operator, 2026-11-14): a farm at the mine base.** sites.md stands: wheat round the
  water at the mine base's phase 3. sites.md's "a mine level has no farm" is corrected on
  landing.
- **Naming:** § 17's phase 4 is "Armour", Readiness's ESTABLISHED, the roadmap's "Lodgment
  established". One name: ESTABLISHED, armour one of its standards.
- **Floors aren't C-ratings.** A C-rating measures a squad's fitness; a floor says what comes first.
- The levels of supply are first figures, to be refined from the dawn AAR as consumption is measured.

## Merged from sites.md (2026-11-19): Roadmap: what to expect on day 1, day 2, and after

### Why
The garrison has the pieces of a lodgment (docs/military-doctrine.md § 17) but no timetable, so
nothing says whether it's on schedule or behind. Measured on 2026-11-07, after about twelve hours
of garrison running (some 36 in-game days), it is behind:
- **Casualties.** About 50 deaths. More than half were falls ("fell from a high place", "hit the
  ground too hard"), several at once; then skeletons, creepers, endermen, one phantom, and three by
  a squadmate's sword. Every death drops the soldier's kit, so gear never accumulates.
- **Gear.** Every soldier carries a wooden sword and no armour. The ladder (§ 11) reached a bow
  once. Stone is gone within reach of the camp, and no mine has been cut.
- **Sustainment.** No soldier carries a ration. Soldiers walk round at 1 to 5 health, because
  health only comes back at food 18 or more.
- **The camp.** A part-built dirt wall, scattered huts and torches. It doesn't look like a base.

Real units fix this with a timetable of *priorities of work*, each "a task, a given time, and a
measurable performance standard" (ATP 3-21.8 ch. 7; FM 3-21.8), improved continuously: a hasty
position becomes a deliberate one, and an airhead becomes a lodgment. Their leaders also run an
*after-action review* at the end of each day (TC 25-20): what was supposed to happen, what did
happen, why, and what to do differently. Both map directly onto this project. The timetable is
data the general can be measured against, and the review is the daily read of the log.

### Doctrine
- **Lodgment phases** (FM 3-99, from FM 90-26): seize the airhead, consolidate it, expand it to
  an airhead line that can be held, then build up combat power inside it. Already the shape of
  § 17's phases.
- **Base camp standards** (ATP 3-37.10): construction goes *initial*, then *temporary*, then
  *semi-permanent*. Each standard is austere at first and built up with more engineer effort as
  the camp is held longer.
- **Priorities of work, patrol base** (ATP 3-21.8 ch. 7; TC 3-21.76): security first and
  continuous; then the withdrawal plan, communications, mission preparation, weapons and equipment
  maintenance, water, mess and rest plans; stand-to at dusk and dawn. Never more than part of the
  unit eating, cleaning weapons or sleeping at once.
- **Priorities of work, defence** (FM 3-21.8): local security, then position key weapons, then
  dig fighting positions in stages. Stage 1 is sited, stage 2 is the retaining walls, stage 3 is
  dug, and stage 4 is the overhead cover that makes a hasty position deliberate. Then obstacles,
  supplementary positions, and continuous improvement.
- **Unit status** (AR 220-1): C-1 to C-4 from personnel and equipment on hand and fit. This is
  already the squads' C-rating.

### The timetable
Each phase must be met by its day, measured at that day's dusk (day tick 12000). An in-game day
is 20 minutes, and the garrison starts at a dawn as if just dropped in. "All" means every soldier
alive at the time.

| Day | Phase (§ 17) | Standard by dusk |
|---|---|---|
| 1 | Airhead seized: initial standard | All armed (a wooden sword at least) by noon. Each squad sealed and lit in a hasty hut at dusk. Two food each carried or eaten. No falls. |
| 2 | Airhead consolidated | All with a stone sword and pickaxe. The camp's ground lit (SECURE). A farm planted. Each squad's hut within the camp. Health back to full by dusk (food 18 or more). |
| 3 | Airhead line held | The wall closed, with its lip and a gate. A tree farm planted. A stockpile chest of logs and cobblestone. Shields for all. First bread. |
| 4–5 | Buildup: temporary standard | A mine cut to y=16 with a lit landing. Copper, then iron armour for half the soldiers. Two rations each carried. The huts replaced by one barracks with a bed per soldier. |
| 6–7 | Lodgment established | Iron armour, sword and pickaxe for all. The wall lit on top. A furnace, table and chests in the camp. Casualties under one a day. C-1 for both squads. |
| 8+ | Semi-permanent standard, then expansion | Branch tunnels and a base at a second mine level (backlog/mining.md). Diamonds. A Nether foothold (backlog/readiness.md). The home camp stays the biggest. |

**Daily drills, from day 1:**
- **Stand-to** at dusk and dawn: every soldier inside the walls or its hut at dusk, and not out
  until dawn's stand-to ends. This is built for huts (§ 14).
- **The after-action review** at each dawn is logged (docs/military-doctrine.md § 17). What it
  doesn't say yet: the day's own standard from this timetable, and each squad's C-rating at dusk.

### Done when
- The timetable is data in `command` (`Foothold` gains a day: each phase's due day and its
  standards), so "behind schedule" is computed, not judged. The general's `<foothold>` names the
  day and what's late.
- The AAR names the day's standard from the timetable and each squad's C-rating at dusk.
- A fresh garrison started at a dawn meets day 1's and day 2's standards in a live run, and holds
  every later standard it reaches on later days.

### What's in the way
In the order the timetable meets them:
- **Falls** (day 1). Two causes found and fixed: blocks with no collision read as solid, sugar
  cane among them (docs/world-data-spec.md § 2), and steps in a fight taken over a drop
  (docs/military-doctrine.md § 8). The casualty reports (§ 6) behind the second showed what made
  short falls fatal: every one was a soldier at 2 health or less.
- **Hunger and phantoms** (days 1-2). Those soldiers were starving at food 0, and fighting
  phantoms at dawn: the ground round the camp is hunted out, wheat takes days, and soldiers who
  never sleep draw phantoms (backlog/rest-plan.md). Food that comes sooner than wheat - fishing,
  or a pen of animals to breed (backlog/renewables.md) - and beds are what's left.
- **Respawning up high.** A dead soldier respawns near world spawn, sometimes on a patch of glass
  whose only way down is a fall costing 14 of its 20 health, and stands there reporting CANTCO. Beds in the camp would move
  its respawn there (backlog/rest-plan.md); until then they build their way down (see Notes).
- **Kit lost at death** (every day). Until deaths are rare, kit is rebuilt after every one.
- **Health below full** (day 2). Food at 18 or more is the standard, not just "not hungry". Hunting
  and farming have to keep the squads above it, and rations have to be carried and eaten by day.
- **Stone runs out near the camp** (day 2). The mine (§ 16) is the camp's quarry. Cut it on day 2,
  not after the buildup phase.
- **Gate and wall light** (day 3, backlog/durable-shelter.md).
- **Barracks and beds** (days 4–5, backlog/rest-plan.md): a bigger room inside the walls.
- **Branch tunnels and mine levels** (day 8+, backlog/mining.md). **Bases beyond the camp** (day
  8+, backlog/sites.md).

### Notes
- **Decided (operator):** no `spawnpoint`. Soldiers get down from where they respawn by building
  their way, not by falling: a fall and a meal to heal is the exception, not the drill
  (backlog/mobility.md, the respawn platform).
- **Decided (operator):** friendly fire stays on, and kit is dropped at death (`pvp` and
  `keepInventory` at their defaults). Both are part of what the chain of command has to manage.
- **Decided (operator):** the foothold is where the first night is made, here at (-48, 44). The
  main base is later, at the origin (its layout is backlog/cells.md), built while the foothold is
  the temporary base.
- **Decided (operator):** materials are traded off, not waited for. Armour from whatever metal
  there is, copper included. Stone tools over wooden ones. Scarce premium materials (diamond) go
  to armour and weapons before tools like hoes. The equipment ladder already ranks them this way
  (docs/military-doctrine.md § 11). A style guide - form over function - comes after the basics.
- The days are a target to measure against, not a script. The general still decides what each
  squad does. The timetable only says where the camp should be by now.
