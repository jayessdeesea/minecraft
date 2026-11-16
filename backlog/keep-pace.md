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
- **A rectangle with nothing to cut.** "clearer idle, nothing to cut" was 12-14% of the crew's
  ticks (field252): a builder holds a rectangle with ground over the level by its columns' tops,
  and its clearer's cover (`Clearing.toClear`: open-topped, not held out) is empty, so it stands
  till the rectangle times out. The foreman logs each one ("has nothing its clearer can cut")
  with its columns' tops, whether held out, covered or remembered - read those next, and make
  the foreman's "work left" and the clearer's cover the same test.
- **The uneven split.** As split, a level's best was 192 s against 123 s shared evenly (8
  builders); one builder had 50 digs while two had 3. `LevelPlan` shares rectangles by blocks at
  plan time and the foreman holds them till done or due (the operator's protocol); whether one done
  takes part of a slow one's is backlog/questions-overnight.md's question 2.
- **Breaks at three times vanilla's.** A dig waits its target's fixed time plus
  `Breacher.MARGIN_TICKS` (5): dirt with a stone shovel 12 ticks against vanilla's 4. The server
  finishes a dig once 70% of its time has gone by, so vanilla's own time for the tool in hand plus
  a tick is margin enough. Done when the break ticks taken are within 10% of best - and
  `SquadSimulationTest.aSoldierStrandedUpItsOwnPillarDigsItDown` still passes: with the shorter
  wait it fails (the soldier builds back up its pillar), as it does with a guard that holds a new
  cut while the soldier is in the air, so the pillar's recovery depends on the time between cuts.
- **Pickups.** About 10 ticks a dig waiting for the drop.
- **The bench's outer rows still flicker the level (field249, 06:34).** The level went 85, 88, 85,
  89, 85, 87 within 20 seconds, each up-level one to three blocks at x=18..19 z=-11..-10, the
  bench's rows two and three out against a dirt hill standing to about y=90 beyond them. Not steps
  (none logged there), not falling blocks (dirt), not a remembered top: the foreman logs each rise
  ("[Keep] the level rose from y=84 to y=87: [19 -17 seen]") and the column was loaded. Next
  suspect: ground counts only under an open top (`Clearing.toClear`), so ground under a cell the
  dig leaves - water beside it, a block it doesn't take - appears only once that cell changes.
  Found (field251): the keep's grove sat on a hilltop past the bench and its planter pillared up
  the bench's outer row to it - fixed, a grove is sited only where it's walked to; and a step that
  landed after reading open was dropped from the record and counted as ground - fixed, kept 20 s.
  Left: a builder's own step to stand on is the level's to dig and is dug from under it and put
  back at one cell (Eli at (19, 89, -11)); keeping steps out of the dig by age left a spire's
  climbing steps standing in the squad simulation - it wants the step that's under someone's feet,
  not a recent one.

## Done when
- The dig so far (`[Keep] pace: the dig so far`) holds within 90% across a day's levels.
