---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-21.8 ch. 2 (troop leading procedures); FM 6-0 ch. 8 (running estimates); ATP 3-34.40 (general engineering - production rates, site preparation before works)
---

# Flattening as a campaign: resource, survey, plan, work it level by level, reassess

## Why
A flatten (command.Flatten) is run by the foreman one level at a time, but nothing coordinates it
as a whole (operator, 2026-11-15): there's no check it has what it needs before it starts, no
survey of the work, no plan of who it needs when, and no look back while it runs. Every squad on
the site is its crew whatever the level holds, so a sparse level at the top leaves most of them
standing - "waiting, no rectangle" was a third of the crew's ticks on the keep - while a full level
at the bottom wants every hand there is. The coordinator decides where parallelism pays and where
it gets in the way (docs/echelons/tenets.md, "work in parallel").

## The campaign, as troop leading runs it
A state machine the foreman runs (command, pure; its record kept on disk under
`minecraft.state-dir` as the wall, the farm and the mine are), each phase's standard measured from
the world, not from reports, and shown to the squad leader and the platoon leader as the foothold is:
1. **Resource** - the depot's standard met before the dig is staffed: chests enough for what the dig
   will stow, a table and furnace, logs, food, a pickaxe and a shovel for every builder it will
   need (the depot's detail is backlog/flatten-campaign.md). What's short goes up as the squad's
   Class IV line, and the platoon leader answers with WOOD or MINE.
2. **Survey** - the site as it stands: the blocks to dig at each level, top down (a profile), and
   what they are (soil, stone, ore, trees) - from the world model, before anyone walks onto it.
3. **Plan** - the crew each level needs, from its blocks: enough builders that each has about a
   standard share (the first figure: 24 blocks a builder, so a 30-block level wants one or two and a
   1,089-block level wants the whole force), never more than the site's columns allow. The plan
   says which levels are the campaign's critical path.
4. **Work it level by level** - the foreman shares each level among that level's crew (LevelPlan,
   built); hands the level doesn't need are released - their squads to the platoon leader's own
   objective for them - and called back as the levels widen.
5. **Reassess** - a running estimate each squad cycle: blocks left, the crew's pace against best
   (LevelPace, built), what the depot and the packs still hold; the plan made again when any of
   them leaves its standard - a shortfall goes back to Resource, a level slower than planned gets
   more hands.

## The farm and grove ground (operator, 2026-11-17)
x=-48..-16, z=-16..16 to y=62, topped with soil: under way (`!flatten -48 -16 -16 16 62 north soil` -
the north depot, salvage and the soil top are built, docs/military-doctrine.md § 17). Left: the groves
and the farm moved onto it once it's down (backlog/renewables.md), and the keep's site after it.


- **Built:** the crew sized to the level under way, squads released and called back
  (docs/military-doctrine.md § 17, "Its crew is sized to its level").
- The campaign record and its phases, with the measured standard for each.
- The survey ahead: the next levels' blocks, so squads are called back before a wide level starts,
  not when it has.
- Simulation: a site with a spire and then a full floor - one builder takes the spire while the
  other squad works elsewhere, and both are on the floor.
- Live: `[Keep] pace` shows "waiting, no rectangle" under 5% across a flatten, and the dig so far
  within 90% of its best (backlog/flatten-campaign.md).

## Notes
- Built: the foreman (`SoldierRunner.foremansNet`), its levels (`LevelPlan`), the pace telemetry
  (`LevelPace`), the haul road (`AccessRoute`), the depot's row of chests, the wood party, and
  `Flatten` as the site.
- **Built (2026-11-15):** the foreman's protocol - rectangles held till done or due, reassigned on
  timeout, split only for a builder come late; supplies between levels.
- **Why Resource comes first (field246, the big site):** builders left their rectangles mid-level
  for tools and timed out, four of five on one level - one with no pickaxe and "no LOG within 48
  blocks" to make one, one short of a stone sword with "no STONE within 48 blocks" it could walk to,
  one sent for a shovel to the camp's stockpile 70 blocks off and underground, because the depot's
  chest wasn't standing (11 logs between the builders and the stockpile, 16 wanted). The protocol
  handed each rectangle on and one builder finished them all; the crew's time went on the errands.
  Done when: no level is dealt until the depot's chest stands and each builder on it carries a
  pickaxe and a shovel - the depot's logs and tools stocked before the crew is sent.
- **Depots by the site's size - what's left:** built, a depot every 48 blocks along the west edge,
  loads into the nearest, draws from the main one (docs/military-doctrine.md). Left: draws from
  any depot that holds the thing wanted, surplus moved to where it's short (docs/echelons/tenets.md,
  "work in parallel"); each outpost its haul road; Resource checks every depot's standard before
  the crew is sent.
- **Flatten, not the keep, in the code's names:** `keepwork`, `foremansNet`'s keep fields, the
  `[Keep]` log tag and `Objective.Task.Keep` are the flatten's; the keep is one use of it
  (operator, 2026-11-15). The task's name in the platoon leader's prompt changes with it.
- **Errands held till a builder's rectangles are done:** a depot trip with surplus, a draw of food
  while fed, a tool made with one still in hand - none mid-assignment; hunger first, always.
- **Traversal in the split:** blocks weighted by the walk and climb to them, so a builder with a
  hard reach gets fewer (operator, 2026-11-15: about the same blocks each, or fewer if traversal is
  challenging).
- **Released squads, the rest (operator, 2026-11-16):** farm, wood and iron are built
  (docs/military-doctrine.md § 4). Left: hunting - no squad task for it yet - and the mine's stash
  of raw iron, not counted with the surface chests'.
- **A graduated slope where the site is cut into a hill.** Built: the bench is twelve rows, 1:1,
  and the depots and pits stand past it (docs/military-doctrine.md, clearing rule 6). Left: a hill
  higher than twelve over the floor still stands as a wall past the twelfth row - whether that wants
  rows to the full depth, live, once the big site's east side is cut down to it. Live (field389),
  with the level at y=90 and the depot at y=71, the way down off the site's top ran past (27, 93, -7)
  and (51, 93, 2): 118 crowding alerts there in a day, two ways at once, and Alice failed five walks
  running to the stockpile from (27.7, 93, -7.4). The (51, 93, 2) half was the mine's climb-out rule
  sending soldiers on the slope below its head up and back (fixed: under the ground only).

## Merged from flatten-campaign.md (2026-11-19): The keep as a campaign: depot first, a record that outlives the day

### Why
The keep's dig runs with no supply behind it (operator, 2026-11-14). Live:
- No wood within reach of the keep: no chest at its depot (two logs), two builders with no pickaxe
  and two with no shovel (half a log a handle), stone dug by hand twelve times as slow.
- No iron in any pack: the mine stopped at its planned depth, so no iron pickaxe, and the keep's
  emerald ore stands set aside for one.
- Excess thrown away for want of a chest; hundreds of cobblestone carried that the slab will want.
- The Class IV request (`PlatoonSergeant.classFour`) asks only for logs, only under four, and nothing asks
  for iron.
- Nothing remembers the campaign: each dawn the squads are put back on the keep by the operator's
  rule, and what was prepared, what's short and what phase it's in is worked out again or not at all.

### Done when
- The campaign record and its phases are backlog/flatten-campaign.md; this item is the depot's part of it.
- **The depot's standard**, sized from the dig: chests enough for the slab's cobblestone (the
  fill's cell count over a chest's 1,728) and one more for wood and tools; a crafting table and a
  furnace beside them; a starting stock of logs; every builder a pickaxe and a shovel.
- **Requests from the record**: what the campaign lacks goes up as the squad's Class IV line -
  chests, logs, iron for one iron pickaxe - and the general answers with WOOD or MINE as it answers
  the log request now.
- **The foreman's errands**: a builder with a full pack unloads at the depot; one short of a tool
  makes it at the depot's table or draws it; one with two pickaxes hands one to a builder with none
  (`CrossLevel.TOOLS_LOW` keeps too much with the holder). Tool-aware rectangles: stone to builders
  with pickaxes, soil and gravel to those with shovels.
- Simulation: a keep with no chest and trees 40 blocks off - two builders fetch logs, the chest and
  table go up at the depot, a builder with a full pack unloads, the rest never stop digging.
- Live: the depot's chests stand, nothing useful is thrown away, and every builder holds the tool
  for its block.

### Notes
- **The depot's row of chests is built** (docs/military-doctrine.md § 4, "The depot is a row of
  chests"): six cells, filled in turn. What's left of it:
  - **A base's stockpile is big (operator, 2026-11-15):** "a lot of chests" - about 32 double
    chests at a base, to dump a lot into. Double chests need `ChestWindow` to open a 54-slot window
    (today two chests side by side are kept apart because nothing opens the pair). Sized from the
    counts beside that: eight builders carry about 9,000 blocks.
  - **Caps on bulk:** each material's reserve and most are built (docs/military-doctrine.md § 4,
    the stockpile's levels). Left: an active plan that wants more (the slab, a wall) lifting a most;
    other squads' loads dumped as the crew's are; draws of the table's kinds - saplings for a
    grove, seeds for a farm - from the depot, through the reserve (today only metal, logs and
    cobblestone are drawn); and a second depot once the row's cells within reach are all full.
    The pack's own order when full is built (docs/military-doctrine.md § 4, the pack): dirt past two
    stacks, then seeds, then cobblestone past five stacks; never ore, ingots or tools.
  - Selective stowing: cobblestone, logs, ore, spare tools, the lumberjacks' axes and saplings go in
    (operator, 2026-11-14: they aren't needed all the time). `Depositor` stows only a full load of
    logs or cobblestone, so a pack full of dirt and odd blocks never empties.
  - Which chest is being filled isn't kept: after a restart the first is tried again, a failed trip
    for each full chest. It belongs in the campaign record.
  - Draws (logs, cobblestone for the slab) look in the chest being filled only, not along the row.
- **The wood party and the keep's grove are built** (docs/military-doctrine.md § 4, "The keep's
  wood party"). What's left:
  - **A surveyor (operator, 2026-11-16):** a role that finds the forest - and ore, and ground -
    past what the squads see (`bot.supply.ResourceSurveyor` reports only what's within 64 blocks
    of a squad), and says where it is. No surveyor role exists yet.
  - **A standing lumberjack (operator, 2026-11-16):** with an axe, it clear-cuts the forest the
    surveyor found; takes a second pass once the leaves have decayed, for the saplings and logs
    dropped; and replants on the grid ((x+2)%4 == 0, (z+2)%4 == 0, `TreeFarm.onGrid`) only where
    there's room for the tree to grow - open air above (`TreeFarm.HEADROOM`, built) and round it
    sideways (not checked today: a grid cell beside a wall or a trunk is planted). A forest far
    out is cut and its wood and saplings carried back, and the grove planted near the keep, not
    there.
  Today the wood party cuts where a keep squad last saw trees, and the one grove is planted by
  the depot.
  - A lumberjack who draws its axe and saplings from the depot's chest and
  puts them back, rather than making an axe each time; the grove grown to 16-32 trees with more than
  one plot once the first is planted; the general's whole-squad WOOD answer to Class IV RED dropped
  for the keep's squads, now the foreman sends a party.
- The depot's spot exists (`SoldierRunner.siteWorkshop`, the keep's branch): stand, table, furnace
  and chest cells on the depot's surface.
- `Depositor` stows at the squad's stockpile and reports what the chest holds; `MetalPool` pools
  ore through it. The keep's depot should be a stockpile like the camp's, not a second mechanism.
- Iron's path is backlog/common-stockpile.md and backlog/mining.md: a tunnel to ore seen from the
  stairs, then the first two ingots to one iron pickaxe for the miner.
  - **Trees taller than a body's reach:** a cutter fells a tree whole up to 5 above where it stands
    (`Gatherer`, `Digger.Target.LOG`); a trunk taller than that leaves its top logs ("leaves N log(s)
    of the tree out of reach") and their leaves standing. Left: pillar up under the trunk for the
    rest, then down again.

## Merged from flatten-campaign.md (2026-11-19): The keep's dig within 90% of its best

### Why
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

### What's left, the biggest first
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
- **A builder on its own step's column, taken off the crew and left with no order (field360,
  06:31-06:37).** Bob dug 9 of 10 blocks of his rectangle, put a step up and stood at (24.5, 97,
  -20.1) on the one cobblestone at (24, 96, -20), air at (24, 96, -21) and (25, 96, -20). Two
  things went wrong, and between them cost 371 s idle:
  - His position rounds to the open cell beside the column, so every route from it failed - "no
    route from (25, 97, -20) to within 0 of (25, 97, -20)", four times. A start cell should be the
    one under the body that holds it (`Pathfinder`'s start, the soldier's footprint).
  - Recovery laid out a 1-block stair down and took him off the crew, but he stood `Idle` with
    `order Idle[]` the whole time - the stair never reached him as an order. Find where the squad's
    recovery order is lost for a keep hand (`Recovery.orders` gives an `Order.Place` for one with
    the blocks - he carried 43 dirt - and the squad's per-soldier `route` should keep it).
  The squad leader also answered his CANTCO with a whole-squad wedge move, the point man halting
  for him. One occurrence so far: watch for a repeat.
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
  `BuilderSimulationTest.aSoldierStrandedUpItsOwnPillarDigsItDown` still passes: with the shorter
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
  unloaded columns: something takes loaded columns out of the site's scan for a while. A rise now logs
  each column's top in this scan, in the last cycle's, and read live. Field327: (19, -43) "scan 88,
  last cycle's 71, live 87" - 17 blocks counted as ground in one cycle that weren't the cycle
  before, too many to have been built in a second - and (18, -30) "scan 90, last cycle's 85, live
  89", with builders putting steps at y=89 for stands at y=91 there. Found (field329, the rise naming
  the risen columns and whether their blocks are steps): the live read's follow-down landed on the
  force's own step just put up and took it for the column's top - "live 86, over last's a step
  true" - raising the level every few seconds; fixed, it passes over steps. The scan's top a block
  over the step, not a step, is a climber's fresh step caught before it's on the record. Left: a builder's own step to stand on is the level's to dig and is dug from under it and put
  back at one cell (Eli at (19, 89, -11)); keeping steps out of the dig by age left a spire's
  climbing steps standing in the squad simulation - it wants the step that's under someone's feet,
  not a recent one.

### Done when
- The dig so far (`[Keep] pace: the dig so far`) holds within 90% across a day's levels.

## Spare hands on a level narrower than the crew (operator, 2026-11-20)
Spare hands are reallocated to other tasks. Built for the camp's ground work (docs/military-doctrine.md, keeping the
camp; `SiteGeometry.shareGround`). Left: the keep's own shares (`Tasking.keep`), which still put two builders on a
strip once a level's remainder is narrower than the crew - under the rules of a clearing (§ 17), every builder on
the level, so the spares' other work there is the next level's or the keep's other jobs.

