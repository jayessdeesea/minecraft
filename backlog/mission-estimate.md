---
status: proposed
priority: P1
depends-on: [fleet-command, authority-and-missions]
doctrine: ADP 5-0 (planning; course-of-action screening: feasible, acceptable, suitable); FM 4-0 (logistics estimate, concept of support); ADP 3-0 (operational reach, basing)
---

# Mission estimates: what a mission costs, and whether it's in reach

## Why
Only the keep has an estimate (`command.MissionEstimate.keep`: its work, its bill, its shortfall in logs, FEASIBLE or
PREPARE first). Any other mission - a farm, an outpost, an expedition - is taken on with nothing worked out: what it
will draw, which site supplies it, whether that site can spare it, and whether the objective is near enough to be
worked from an existing base. The platoon leader is shown no estimate when it chooses.

## What's left
- **An estimate for any mission** (`command`, pure), worked out before the mission is given:
  - **The objective:** where, what end state, and the site that supplies it, by name.
  - **The force:** squads and roles, and the work loads they carry (backlog/sites.md, kit by role).
  - **The bill of materials, by class of supply:** what the work consumes - blocks dug over tool uses for Class II,
    chests and logs for IV, bread for I for the days it lasts - and its starting stock at the objective.
  - **The travel:** the walk there and back, measured (`bot.nav.RouteEffort`), not straight-line.
  - **The shortfall:** the bill against the supplying site's stock above its reorder point, and the time its producers
    take to make the difference good.
  - Its duration, its recalculation as the work goes and its calibration against actuals are
    backlog/authority-and-missions.md step 7.
- **Screening, computed** (ADP 5-0's tests that can be measured):
  - **Feasible:** the supplying site's projected stock stays at or above its reorder point through the mission
    (backlog/sites.md, the gate). If not, the estimate says what to make first and how long it takes, so a mission
    starts with a preparation phase rather than not at all; the work starts only when the preparation leaves the
    supplying site above its reorder point. (Phases and their states: authority-and-missions.md step 1.)
  - **In reach:** the objective is within the operational reach of its supplying site (below). If not, the estimate
    adds an outpost on the way, as a mission of its own with its own estimate.
- **The platoon leader chooses, the code costs:** it sees the candidate missions' estimates - each one's shortfall and
  time to make it good, its reach - in a `<missions>` section of its prompt, and picks among them. It never estimates.
- **Operational reach**, measured rather than fixed:
  - **A day's reach.** A squad is sealed in at dusk (docs/military-doctrine.md § 14), so a day's work at an objective
    costs the walk there and back. First figure: about 4 blocks a second on level ground, so a round trip of a quarter
    of the daylight leaves three quarters for work.
  - **A carried reach.** A squad that builds a patrol base where it is stays out overnight: days out = the carried load
    (rations, tools, blocks for its hut) over the day's burn, less the safety level.
  - **When to build an outpost.** When the travel a mission would spend over its duration costs more than an outpost
    does in materials and soldier-hours, or the carried reach is shorter than the work. An outpost is laid out to its
    site standard (about 80 cells, 256 bread, one chunk fenced, gated and lit; backlog/sites.md) and supplied from the
    site behind it until its BUILDUP.
- **An expedition is a mission like any other:** an objective (an area to survey, a biome for what the camp lacks -
  jungle for bamboo, desert for sand), a carried reach, an end state (the survey filed); its bill mostly rations,
  torches and blocks for nightly patrol bases.

## Done when
- `MissionEstimate` gives an estimate for a mission other than the keep, with its force, supplying site, travel, bill
  and shortfall's time to make good, and FEASIBLE, PREPARE first, or out of reach with the outpost it adds.
- The platoon leader's prompt has `<missions>`.
- `PlatoonLeaderScenarioIT`: offered a mission with no depot stock and a nearer grove, the platoon leader takes the
  preparation first; offered an objective out of reach, it takes the outpost first.
- Live: the next big mission starts with its depot stocked, and no worker stands idle for a tool in its first day.

## Notes
- **Decided (operator, 2026-11-15; the first two steps 2026-11-22): the missions, in order.**
  1. Build up the reserves - food and wood from the templated wheat and tree farms, from a temporary base by the
     water (backlog/sites.md, the order of building).
  2. Build the permanent structure at the origin - the force bootstrapped far enough to sustain itself.
  3. Establish trade with the villagers - smaller permanent structures first, so the distances are safe to travel.
  4. Explore the other realms, the Nether and the End - gather what's there, and trade as appropriate.

  The platoon leader makes up no missions of its own: what it chooses among is the operator's current mission and what
  its estimate adds - a preparation, an outpost where the reach runs out.
- First figures - walking speed, work rates - are measured in the scoreboard and refined.
