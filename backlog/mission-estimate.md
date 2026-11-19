---
status: proposed
priority: P1
depends-on: [fleet-command, maturity-model, keep-campaign]
doctrine: ADP 5-0 (planning; course-of-action screening: feasible, acceptable, suitable); FM 4-0 (logistics estimate, concept of support); ADP 3-0 (operational reach, basing); FM 5-0 (running estimates)
---

# Mission estimates: what the next big objective costs, and whether it's in reach

## Why
The force takes on its big objectives - the keep, an expedition, an outpost - with nothing worked
out first: what it will draw, how long it takes, which site supplies it, and whether that site can
spare it. The keep showed the result (flatten-campaign.md): the dig started with no wood at the depot
and no chests for the spoil, and stopped when the tools ran out. A staff would have refused that
plan at its first screening, because it isn't *feasible* - the force can't do it with what it has
in the time allowed (ADP 5-0).

Before an operation, a staff writes a logistics estimate - requirements against what's on hand,
the shortfalls, how they'll be made good - and the order's sustainment paragraph says where the
supply comes from. Whether the objective is in *operational reach* - near enough to be worked
from an existing base - decides whether the plan needs a new base on the way.

## Done when
- **The keep's estimate is built** (`command.MissionEstimate`; docs/military-doctrine.md § 17): its
  work at the measured rate, its bill (tools, logs, chests, cobblestone, rations), its shortfall in
  logs above Class IV's reorder point, FEASIBLE or PREPARE first, `[Estimate]` when that turns, the
  dawn AAR's line, and Class IV's committed burn from it. What's left: the force and its work loads,
  the supplying site by name, travel, and the shortfall's time to make good; estimates for other
  missions (an outpost, an expedition); and the screening, phases and `<missions>` below.
- **A mission estimate** (`command`, pure), worked out for any mission the platoon leader may take on, before
  it's given. The keep's campaign record (flatten-campaign.md) is its first instance:
  - **The objective:** where, what end state, and the site that supplies it.
  - **The force:** squads and roles, and the work loads they carry (sites.md, kit by role).
  - **The bill of materials, by class of supply:** what the work consumes - blocks dug over tool uses
    for Class II, chests and logs for IV, bread for I for the days it lasts - and its starting stock
    at the objective.
  - **The duration:** the work's size over the force's measured rate (blocks an hour, cells planted
    an hour), plus the travel.
  - **The shortfall:** the bill of materials against the supplying site's stock above its reorder
    point, and the time its producers take to make the difference good.
- **Screening, computed** (ADP 5-0's tests that can be measured):
  - **Feasible:** the supplying site's projected stock stays at or above its reorder point through
    the mission (sites.md, the gate). If not, the estimate says what to make first and how
    long it takes, so a mission starts with a preparation phase - the keep's DEPOT - rather than not
    at all.
  - **In reach:** the objective is within the operational reach of its supplying site (below). If
    not, the estimate adds an outpost on the way, as a mission of its own with its own estimate.
- **The platoon leader chooses, the code costs.** At troop-leading procedures' "make a tentative
  plan" (backlog/fleet-command.md), the platoon leader sees the candidate missions' estimates - each
  one's duration, its shortfall and time to make it good, its reach - in a `<missions>` section, and
  picks among them. It never estimates.
- **Phases from the estimate.** A mission's phases are PREPARE (the shortfall made good, the depot
  stocked), EXECUTE, and CONSOLIDATE (the site's level raised, stores returned). Each phase's entry
  is the gate: EXECUTE starts only when PREPARE leaves the supplying site above its reorder point.
- `PlatoonLeaderScenarioIT`: offered the keep with no depot stock and a nearer grove, the platoon leader takes the
  preparation first; offered an objective out of reach, it takes the outpost first.
- Live: the next big mission after the keep starts with its depot stocked, and no worker stands
  idle for a tool in its first day.

## Operational reach
How far a squad can work from a site, measured rather than fixed:
- **A day's reach.** A squad must be sealed in at dusk (§ 14), so a day's work at an objective costs
  the walk there and back. The walk is measured, not straight-line: `bot.nav.RouteEffort` already
  rates the camp's routes against a level walk. First estimate: a soldier walks about 4 blocks a
  second on level ground, so an objective whose round trip takes a quarter of the daylight leaves
  three quarters of it for work.
- **A carried reach.** A squad that builds a patrol base where it is can stay out overnight, so its
  reach is what it carries: rations, tools for the work, blocks for its hut. Days out = the carried
  load over the day's burn, less the safety level - the same days-of-supply arithmetic as a site's.
- **When to build an outpost.** When the travel the mission would spend over its duration costs more
  than an outpost does - the outpost's own estimate in materials and soldier-hours - or when the
  carried reach is shorter than the work. An outpost is laid out to its site standard (~80 cells,
  ~256 bread, one chunk fenced, gated and lit; sites.md) and supplied from the site behind
  it until it reaches its BUILDUP.

## Notes
- **Expeditions.** Exploring is a mission like any other: an objective (an area to survey, a biome
  for something the camp lacks - jungle for bamboo, desert for sand), a carried reach, and an end
  state (the survey filed). Its bill of materials is mostly rations, torches and blocks for nightly
  patrol bases.
- **Running estimates.** A staff keeps its estimates current as the operation goes (FM 5-0). Here:
  the estimate is worked out again each foothold check against the measured rate, so a mission
  running slow says so, and its days left and shortfall move with it.
- **Decided (operator, 2026-11-15): the missions, in order.**
  1. Build the keep - the force bootstrapped far enough to sustain itself.
  2. Establish trade with the villagers - outposts first, so the distances are safe to travel.
  3. Explore the other realms, the Nether and the End, and trade there as appropriate.

  The platoon leader makes up no missions of its own: what it chooses among is the operator's
  current mission and what its estimate adds - a preparation, an outpost where the reach runs out.
  Each mission starts only when it's prepared for (sites.md, the gate).
- First figures - walking speed, work rates - are to be measured in the scoreboard and refined.
