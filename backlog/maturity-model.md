---
status: ready
priority: P1
depends-on: [fleet-command, sites, readiness, site-standards, keep-campaign]
doctrine: FM 3-0 / ADP 3-0 (operational reach, culmination, operational pause); FM 3-99 (lodgment phases); FM 10-27 (supply in the airhead); FM 4-0 (supply levels, classes of supply, controlled supply rate); ATP 3-37.10 (base camp standards); ATP 3-94.4 (reconstitution); AR 220-1 (unit status)
---

# Maturity model: what a site can sustain before it expands

## Why
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
(keep-campaign.md) and readiness away from the camp (readiness.md).

## Levels
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

## Supply levels: the gate
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

**The gate.** Projected forward, not read off the stock:
- A site starts new work - a mission supplied from it, the next level's work - only if its stock
  after that work's draw stays at or above the reorder point.
- When the projection crosses the reorder point, the platoon sergeant tasks producers: it detaches
  soldiers to the work within the platoon leader's priorities, as the foreman shares out the keep
  (backlog/fleet-command.md). The platoon leader sees it only as state - "wood: PAUSE, two on it" -
  under specialists produce, missions consume (tenets.md).
- When it would cross the safety level before the lead time is out, the site pauses: stop, and
  fill up.

**A production check, beside the gate.** A gate stops expansion; it doesn't make supply. If a
class's production is below its requirement at steady state, the site waits for ever, so each
class also reports production against requirement over the day, and the platoon sergeant gives a
shortfall producers - a second grove, a second miner - rather than waiting on it.

## Pause, fall back, and holding a level
**Three responses, by how far a class has fallen:**

| State | When | What it does | Doctrine |
|---|---|---|---|
| ADVANCE | every class at its stockage objective, held over the day | the site may take on its next level's work, and supply another | operational reach |
| PAUSE | any class projected below its safety level within its lead time | no new work that draws that class; work under way that draws it stops; everyone not on security produces it | operational pause |
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

## What's authorised at each level
**Basis of issue.** Each level authorises a floor - the least every soldier there has - and a
ceiling - nothing above it is made yet:

| Level | Floor | Ceiling |
|---|---|---|
| SHELTER | a weapon (wooden at least) | stone; nothing that costs iron |
| AIRHEAD | stone sword and pickaxe | iron only for an enabler (below) |
| BUILDUP | a shield; the best armour to hand | iron tools for producers |
| ESTABLISHED | iron for all | diamond for weapons and armour, never hoes |

**Best available, not best possible.** Between floor and ceiling a soldier makes the best its
materials allow: copper armour now over none while it waits for iron (operator: materials are
traded off, not waited for).

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

## Kit by role: protection and work apart (decided, operator 2026-11-14)
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

## What a model sees
No prose ladder in a system prompt (docs/prompting.md: a rule in prose loses to the state it
describes). An escalation to the platoon leader carries, and its standing view shows, a `<sites>` section, as `<foothold>` is now. A line for each site: its kind, its level
and the level due by the timetable, its state (ADVANCE, PAUSE, RECONSTITUTE), and each class short
of its objective in days of supply, with production against requirement. The camp's line is
today's `<foothold>`.

## Done when
- `command` has site kinds and levels as data, each kind supplying its levels' standards, and
  `Foothold` is the camp's instance; the mine base and the keep are expressed in the same terms.
- Each site's stock is measured by class in days of supply against its next work's bill of
  materials, with its state, and the log says each time a state changes (`[Supply]`).
- The keep: DIG doesn't start, and pauses, while the depot is below its objective in handles,
  tool heads and chests for the next stretch of dig.
- The dawn AAR and the scoreboard report each site's level, state and short classes.
- `bot.craft.Ladder` is split: the protection ladder, and a work load per role sized from its task;
  a builder on the keep carries its role's tools before anything past the protection floor.
- `PlatoonLeaderScenarioIT`: a site in PAUSE on wood, where the platoon sergeant tasks producers, and the platoon leader sends nobody
  further out; a creeper-struck depot, where it contracts; a material short with two claims on it,
  where the enabler gets it.
- Live: the keep levels a full layer without a builder standing idle for want of a tool.

## Notes
- **First check the waste.** Rough arithmetic says the dig needs little wood: about 5,500 blocks
  at 131 uses a stone tool is some 42 tools, 84 sticks, about 11 logs; three or four chests is 8
  more. keep-campaign.md records "half a log a handle", twice what a handle should cost. Log logs
  spent per tool made; above a quarter, the crafting is the fix.
- **Decided (operator, 2026-11-14): lead time.** The roadmap plants a farm on day 2; § 17 puts food
  in the BUILDUP. A standard may carry a lead - the farm planted by AIRHEAD, feeding by BUILDUP - so
  the cheap first step comes early without making food an AIRHEAD target, which cost the morning
  (docs/prompting.md).
- **Decided (operator, 2026-11-14): a farm at the mine base.** sites.md stands: wheat round the
  water at the mine base's phase 3. readiness.md's "a mine level has no farm" is corrected on
  landing.
- **Naming:** § 17's phase 4 is "Armour", Readiness's ESTABLISHED, the roadmap's "Lodgment
  established". One name: ESTABLISHED, armour one of its standards.
- **Floors aren't C-ratings.** A C-rating measures a squad's fitness; a floor says what comes first.
- The levels of supply are first figures, to be refined from the dawn AAR as consumption is measured.
