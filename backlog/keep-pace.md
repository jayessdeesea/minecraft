---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-34.40 (general engineering: production rates, the critical path)
---

# The keep's dig within 90% of its best

## Why
The keep's dig is to run within 90% of the fastest it could go, with the telemetry to say whether
it is and where the rest goes, so the method can be refactored (operator, 2026-11-14 and
2026-11-15). `command.LevelPace` measures it (docs/military-doctrine.md § 11): a level's best is
its digs' best shared evenly over its crew, and every builder's ticks are put down to what held
them. The first full-floor levels (y=66, 1,030 blocks, field227-228) ran at 20-26% of best, the
crew's ticks:

| Held by | Share |
|---|---|
| waiting, no rectangle | 30-40% |
| the clearer (digging) | 33-38% |
| the farm | 12-20% |
| scavenging, gathering, moving | 15% |

## What's left, the biggest first
- **Builders with no rectangle.** A third of the crew's ticks - most of it a crew bigger than a sparse level needs, which backlog/flatten-campaign.md sizes to the level. Find which builders the plan leaves
  out and why - fewer columns than builders at the level, a builder dropped from the crew between
  plans, the road's cutter - and give each one ground.
- **The uneven split.** As split, a level's best was 192 s against 123 s shared evenly (8
  builders); one builder had 50 digs while two had 3. `LevelPlan` shares rectangles by blocks at
  plan time; one that empties fast is shared out again only after `KEEP_REPLAN_MILLIS`.
- **Breaks at three times vanilla's.** A dig waits its target's fixed time plus
  `Breacher.MARGIN_TICKS` (5): dirt with a stone shovel 12 ticks against vanilla's 4. The server
  finishes a dig once 70% of its time has gone by, so vanilla's own time for the tool in hand plus
  a tick is margin enough. Done when the break ticks taken are within 10% of best - and
  `SquadSimulationTest.aSoldierStrandedUpItsOwnPillarDigsItDown` still passes: with the shorter
  wait it fails (the soldier builds back up its pillar), as it does with a guard that holds a new
  cut while the soldier is in the air, so the pillar's recovery depends on the time between cuts.
- **Pickups.** About 10 ticks a dig waiting for the drop.
- **One-block levels at the edges: the benched edge (clearing rule 6) isn't built.** Where the
  site meets ground higher than its floor - the big site's east edge, x=16, against a hillside -
  soldiers crossing between the two put steps in the edge column to climb the wall (field245:
  Dana and Fran, four in a minute), and each is a level of its own while it stands. Set-aside ore
  and unloaded chunks also moved the level, and are fixed (one answer for the site; a column's last
  known top). Done when the ground just round the dig is lowered as it goes - benched, a walkable
  slope, docs/military-doctrine.md § 17 rule 6 - and the edge needs no steps.

## Done when
- The dig so far (`[Keep] pace: the dig so far`) holds within 90% across a day's levels.
