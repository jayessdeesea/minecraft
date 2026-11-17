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
- **Steps put up to reach a step (field266, 11:54-11:56): the biggest loss on a wide level.** The
  level y=78, 754 blocks, went at about 20 blocks a minute with five builders. Gus, on the bench's
  outer rows (x=16..19 z=-50..-45), put about twenty steps in two minutes to cut cells at y=80-84 -
  themselves earlier steps, read as soil - and each step put up to reach one stood over the level
  and was the level's to dig in turn, by the same climb; it shut itself in once and cut its way
  out. Built: a builder standing on the force's own step cuts from there, the step under it too
  (Clearing.next), so it takes its climb down with it; and a cut from the air never aims at the
  force's own step (Clearing.fromTheAir) - field301's builder went between two steps on a dirt
  tower's top for two minutes, the crew waiting on the level; and a step over the ground sets no
  level for a cut from the ground either (Clearing.next, field313). Done when a wide level's pace line shows
  steps put no more than its blocks' share, and the level goes at its crew's rate.
  Field269, 30 minutes after: 323 steps still, three quarters on the east bench at y=85-86 under
  the hill beyond the site (to about y=90, the floor at 77) - builders on the hill with no walk
  down, building their way off: backlog/flatten-campaign.md's graduated slope.
- **Builders with no rectangle.** A third of the crew's ticks - most of it a crew bigger than a sparse level needs, which backlog/flatten-campaign.md sizes to the level. Find which builders the plan leaves
  out and why - fewer columns than builders at the level, a builder dropped from the crew between
  plans, the road's cutter - and give each one ground.
- **"clearer idle, nothing to cut" is still 15%** (field258, level y=81: 1,339 s against a best of
  116 s). The level's columns are read live now, and the foreman logged only six rectangles with
  ground its clearer couldn't cut, so the rest is elsewhere: between a builder's last block and the
  squad loop's next look (up to a second) it's labelled so, and the order's cover (`route`'s
  `Clearing.toClear`) is a snapshot. The foreman logs, at each level's end, every builder with a
  tenth of its ticks or more so ("[Keep] Bob had nothing to cut 30% of y=80 ..."), its rectangle
  and its cover's size. Field262's level y=78: Jack 75% (53 s), Kate 42%, Liam 27%, Ivy 20%,
  each with no cover routed. A builder whose squad's order isn't its dig - an errand, a fight's
  move - is now counted "held off its rectangle (Order)", apart from its clearer's idling: read
  which orders hold them next.
- **Mine-head depositors stand (field256, 08:40).** Gus and Carol, of the mining squads, stood a
  minute each at the mine head with a full load, the depositor's hands, ordered to (-26.5, 70,
  -19.5) - a cell the world model reads solid (feet and head) over air - and their walk to the
  stockpile, the camp's 70 blocks off once their own workshop's chest was full and the next not
  fitted, timed out. No re-siting line was logged, so the workshop's stand read standable. Find
  whose stand that order is, and why it's solid. And field268, 12:32: Mia, of the farm squad, at
  (-48.9, 74, 43.1) - within the chest's 3.5-block reach of (-46.5, 73, 44.5) by Depositor's own
  test - failed five walks to it in a row, "expected the chest in reach within 1789 ticks"; suspect
  the depositor's soldierAt left stale while another routine holds the hands.
- **A metal draw that starts and stops (field270, 13:34).** Jack, at the site's north end
  (5.5, 74, 65.5), set out for 5 raw copper at the main depot every 30 s and called it off within
  seconds ("the trip is no longer wanted"), its order Idle meanwhile - the walk never steered it -
  and stood a minute; then gave up the copper helmet, "no COPPER_ORE within 48 blocks". Found: the
  force's view of the stockpile took every chest opened, the outposts' too, so the draw came and
  went with it - fixed, only the stockpile draws come from sets it. Left: watch that the draw now
  holds, and why the soldier's order read Idle between its tries.
- **A fight with no leash, through caves under the site (field277, 16:50-16:58).** Dana, a
  flatten builder, went down from the level (~74) to a cave under the site at (-0.3, 52), back up,
  down again, then east through the caves fighting one hostile after the next - each blow a step
  toward the next - to (74.7, 47.2, -45.3), 75 blocks from the site and in water, the sealer
  filling round it. The dig breaks into caves under its floor, and a soldier in contact closes on
  what it fights however far that takes it from its order. Wanted: a leash - not closing past a
  distance from where its order holds it (the drill's 24 blocks of support?) - and the caves
  under the floor sealed or left to a squad sent for them. Both change how contact is fought.
- **The mine's stairs go down but not up (field283-284).** Soldiers on the camp's mine stairs at
  y=21-29 (x=-26..-24, z=-24..-22) are cut off - the walk flood finishes inside its budget (550)
  and reaches nothing near the surface - and the recovery drill lays a stair up for some (93, 126
  blocks) and none for others ("nothing to step up onto" all round). They got down - by a drop a
  route allowed, or a step since taken - and can't climb back. Walk the stairs' cells from the
  head down (Staircase.cuts) against the world for the first step a soldier can't go up: a
  floored step's head room, a step dug from under it, a refill in the walkway.
  Found (field285, probed read-only): the stairs (head (-26, 70, -24), the plain ring) have solid
  blocks in both feet and head of steps at (-27, 62, -25) and (-27, 55, -24), and more at
  (-25, 28, -25) and (-25, 26, -23). The log shows step 8, (-27, 62, -25), cut again and again by
  the miners (07:02-07:41) while walkers put their route steps into it ("[Dana] its step at
  -27 62 -25 keeps being refused"): soldiers crossing the shaft's open top build into the stairs'
  own cells, and some stay. Built: no route builds in a staircase's steps (WorldView.noBuild).
  Left: the cutter clearing the stairs from the head down when it finds them blocked - field316,
  steps 6-10 (y=63-59) solid for minutes while Gus stood stranded at y=0, a 188-block stair up laid
  out for him, then cleared. Separately, the camp's stairs are kept on disk twice - "to
  y=24" by Bravo's miner, "to y=16" by Alpha - two plans for one shaft.
- **A way out cut sideways from a pocket under the ground (field287).** Three soldiers sent into a
  pit by the camp's chest at (-46..-48, 70-71, 40-42), three under the camp's ground, were taken for
  shut in, and their doorway cut sideways - the room grew a cell at a time, and each was taken for
  shut in again. A way out of a room below the ground goes up to it, not along. (The stand that
  sent them in, two under the chest, is fixed: no stand lower than a step under a chest.)
- **The uneven split.** As split, a level's best was 192 s against 123 s shared evenly (8
  builders); one builder had 50 digs while two had 3. `LevelPlan` shares rectangles by blocks at
  plan time and the foreman holds them till done or due (the operator's protocol). Wanted
  (operator, 2026-11-16: do a better job assigning): rectangles sized by their estimated time -
  the walk, the climb and the blocks' hardness, not their count. (A builder done with its own
  taking half of the most that's left of another's is built.) "Done, waiting for the level" was
  15-35% of the crew's ticks on wide levels. On hilltop levels of 27-49 blocks with the crew sized
  for the levels under them, "waiting, no rectangle" was 30%: more builders than shares, the level
  at a time held (the clearing rules) - a squad with no share there is a candidate for the
  released squads' work (backlog/flatten-campaign.md).
- **Blocks that stop soldiers, and fences (operator, 2026-11-16).** Built: a refused step and a
  stall name the blocks there ("wildflowers P"), counted and said at the 1st, 10th and 100th of a
  kind ("[Blocks] ..."). Every state the server has is classed (tools/block-class-generator), so an
  "unknown" block is a class that's wrong for it, as the wildflowers' was: read the [Blocks] lines
  each run and fix the class. Fences are classed tall (T, 1.5 high) already; if soldiers stall on
  them, the [Blocks] lines will say which - a gate, a wall - and where.
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
  Found (field294): a column whose top was cut since the site's last scan dropped out of the level
  whole, a dirt tower in the outer rows flickering it 88-74-88 at each cut - fixed, the column's
  top followed down live (`SoldierRunner.LIVE_TOP_DROP`). Field324, after that and the steps kept on
  disk: the level fell from 87 to 82 and rose back three times in ten minutes on hilltop columns
  (14..17, -30..-33) and (19, -39..-41), each "seen" - loaded throughout - so it's not the memory of
  unloaded columns: something takes loaded columns out of the site's scan for a while. Next: log,
  at a rise, whether the column was in the last scan's cells and under which squad's `except`. Left: a builder's own step to stand on is the level's to dig and is dug from under it and put
  back at one cell (Eli at (19, 89, -11)); keeping steps out of the dig by age left a spire's
  climbing steps standing in the squad simulation - it wants the step that's under someone's feet,
  not a recent one.

## Done when
- The dig so far (`[Keep] pace: the dig so far`) holds within 90% across a day's levels.
