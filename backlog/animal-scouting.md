---
status: ready
priority: P2
depends-on: []
---

# A squad scouts for animals and leads them back: what's left

## Why
The operator's (2026-11-25): a squad goes out, finds animals of a kind and brings them back - look here for {animal}
and bring them back to {location}. The order, the routine in a soldier's tick, the food drawn first, the outcome and
the rule that what's brought in is stock are built (docs/echelons/platoon-leader/decisions.md, "scout mission"), and
pass in the squad simulation (`ScoutMissionSimulationTest`). None of it has run on the pinned server.

## Done when
- **The live eval.** On the pinned server: a sheep, a cow and a chicken each led 60 blocks; whether a led animal
  crosses a one-block stream, which the simulation says it won't; whether a cow keeps a soldier's walk.
- **A lead keeps to ground an animal walks.** A soldier's walk is the pathfinder's, which swims, drops and pillars
  where an animal won't follow: the route for a lead is planned over ground an animal walks, or the herder waits out
  more than it should.
- **Where the land ends, used.** A shore met is said on the net and in the mission's outcome; the platoon leader does
  nothing with it. The next scout mission over the same ground knows it: sectors cut at the shore, not walked to it again.
- **Kept over a restart.** An open mission is in memory (`app.ScoutMissions`): a deploy drops it and its squad goes
  back to the day's work, and where it brought animals is stock's ground only till then - an animal farm's chunk is
  stock's ground always. In the registers, as a farm mission's phases are.
- **By night.** Its squad shelters at dusk as any does, the animals left where they stand, and each soldier's herder
  goes on at dawn from where it was - but the mission's day runs through the night: given late, it fails for time.
  Whether a lead under way finishes first, and whether the night counts, are not decided.
- **A meal on a long lead.** While the food is to be in hand the hands are the herder's: no meal till it is done or
  under threat.
- **Only what's drawn is carried.** A draw takes a stack: a soldier with no wheat takes 64 from the stores to hold
  one, and puts none back.
- **A sector nobody can lead from.** A soldier with none of the food after three minutes is counted out and its
  sector goes unlooked: a squadmate with food to spare hands it one (backlog/cross-leveling.md), or takes the sector.

## Notes
- Fences and gates on the way are for later (operator): they are brought to open ground at {location}.
- Pigs want carrots, which the force doesn't grow: no scouting for them yet (`bot.supply.Livestock`).
- Terms: the place searched is an objective, the squad's part a task (docs/military-doctrine.md).
