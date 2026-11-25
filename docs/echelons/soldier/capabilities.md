# Soldier: what it does on its own

A soldier is a player bot with its own connection, driven by plain Java every tick (20 a second).
It never calls a model. Everything here it does without being told, as reflexes of
`bot.DeterministicSoldier` and the routines round it. What it's told is in [orders.md](orders.md).

## Moving
To the goal its order names, by A* over block cells (`bot.nav.Pathfinder`), re-planned every 2
seconds or when stuck - a wide search asked again from the same cell to the same goal over ground unchanged since
(`WorldView.stamp`) given its last answer, not searched again (`bot.nav.PlanMemo`):
- **Walks** level ground, round anything solid, and diagonally only where both sides are clear. A
  corner it clips walking a level stretch is slid along, never jumped onto: a jump costs hunger. Stood on a
  block's edge over a drop, it steps onto the block before its route's first step (`Mover`; field609, a mine's shaft).
  Where its box no longer fits - a block put since in a column it reaches into, a squadmate's fence - it steps to the
  middle of its own cell first: from there no other step would fit either (`Gait.step`).
- **Steps up one block** - a jump, only where its route rises. A fence, at 1.5 blocks, is a wall, not a step.
- **Drops three blocks** unhurt. Further only within the fall allowance its order's risk gives it,
  and only when no route without a fall exists ([military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).
- **Swims** across water at the surface. It never enters lava or fire.
- **Cuts a step** into a bank too high to jump, from the ground or out of water: soil by hand, stone with a pickaxe, never sand or
  gravel, never where it would tunnel or let water in.
- **Pillars** with cobblestone or dirt it carries, beside a wall: from the middle of its cell - off it, a block
  over the cell beside stops the jump - it jumps and places a block under itself.
- **Builds steps** with those blocks, down a sheer drop or up to ground above, when that's the
  only way that reaches its goal (`Pathfinder.stepBuild`): one block a step against ground beside
  it, two in open air. Never on farmland or a crop, which a block put down on spoils; nor does it
  pillar off farmland, or cut a step through it.
- **Keeps blocks to get about with:** at the stockpile, carrying two stacks of cobblestone or more, it keeps its
  smallest of 16 or more back (`Depositor.ROUTE_BLOCKS`) - live (field512-522), a miner banked all 1,207 of his
  and stood for good at a gap he could have floored with one. A single stack goes in whole.
- **Bridges a hole in the floor** at its own level with a block it carries - over a cave or a shaft, a hole that's
  no walkable drop, with ground on its far side (a gap, never a ledge into the air) - floored from the ground beside it and walked over (`Pathfinder.stepBuild`, level). Live
  (field512-520), a miner stood for good at a one-block gap in his gallery's floor, planning wide every few seconds.
- **Walks round rather than builds** (operator, 2026-11-18; 2026-11-20: a block is placed with intent, and
  steps left behind littered the camp): a route that places blocks is searched again placing none, and that
  one taken if it's a plain walk - no step dug, no crop trampled, no fall that hurts - costing at most 40
  blocks' more for each block it saves (`Pathfinder.DETOUR_PER_BLOCK`): a placed block worth 44 blocks' walk, with the search itself still
  pricing it at 4, so a climb that must build stays inside its node budget. Only for a route of two
  blocks or more (`WALK_ROUND_FROM`), and a goal no more than a jump above it: a single step up a
  slope is a builder's stride, and blocks placed to reach a goal higher - a mason onto its wall's
  top - are the work. On the camp's ground - its area of influence, 32 blocks out past its wall line
  (`CampGround.INFLUENCE`; operator, 2026-11-21) - a single step is walked round too, a goal up a hill is
  walked to when a way up stands (a wall's top has none, so a mason still builds), and each block
  priced 8 blocks' walk more for its taking down (`Pathfinder.TAKEDOWN_COST`): a step left there is a debt.
- **Takes back the steps routes left** (police call; operator, 2026-11-20: a block is placed with intent): one
  without work a minute, or stood a minute on a CANTCO, digs the nearest step within 16 blocks, and 4 up or down, that a route put
  down 5 minutes ago or more (`SoldierRunner.scaffolding`) and that stands proud - open over it and on three of its
  sides, so not one that filled a hole - top of a stack first, none a soldier stands by nor the camp's wall's, by
  hand, no shovel wanted (`app.PoliceCall`). It keeps the step till it's gone or other work comes; one not gone in
  a minute is passed over for ten, by everyone. Ahead of a CANTCO'd stone wall or floor.
  And one a minute on the same "no route", where it stood, with a pickaxe and fewer than 8 blocks to bridge or step
  with, quarries stone round it first, over its own work - 6 blocks each way, to 8 (`Recovery.WAY_BLOCKS`), by the
  gatherer's own stands, ones its routes reach - and isn't taken for stranded meanwhile (`Recovery.waitingOnABlock`).
  Live (field512-535), a miner stood for good at a gap of five cells in his gallery's floor over a cave.
- **Doesn't try the same thing forever.** Stuck short of a waypoint - jumping at a block that
  won't take it, a squadmate on the same step - it routes round that cell for 10 seconds, at a
  cost that leaves the cell usable if there's no other way, and comes back to it only after.
  The same goes for what it works at: a wall cell, a torch's cell, a spot to dig, a stockpile that
  fails is passed over for the next, and each time it fails again it's left twice as long
  (`bot.supply.Setbacks`). Something a squadmate was in the way of is tried again soon; a cell
  across water or a torch on leaves is tried less and less.
  A tree, a block to quarry or a cut of the camp's ground is only taken from a stand it can get
  to from where it is - on foot, on blocks it carries, or cutting steps - not one walled off.
- **Plugs the water it's stuck in, and says so.** Afloat and not 1.5 blocks further on in 30
  seconds, it's stuck in the water: carrying cobblestone, dirt or - with neither - logs, which it
  otherwise keeps for tools and won't build a route on, it puts a block into the water
  within 4 of it from where it floats - sources first, which stop the flow, then the water nearest
  it, flowing or not, which is footing to climb out by. Stranded with no stair up and water within
  2 of it - a waterfall beside its pocket barring every heading - it plugs that water the same way,
  so the stair can be dug. Carrying none of them, it first cuts a block
  from the bank beside it - level with its head, then its feet, then below; one with nothing over
  it that falls or flows - and plugs with that. And it reports CANTCO "no route from (x, y, z) out of the water", which
  the squad's recovery drill reads as stranded - cut off, with no route to a squadmate either -
  when it has too few blocks to build its own way:
  a squadmate with blocks is sent to build it a stair (docs/military-doctrine.md § 7). Live, two
  soldiers floated in a flooded shaft for hours reporting the wood they meant to gather.
- **Searches wider for a goal its cheap searches can't reach.** Once its plans gain no ground, it
  searches 15 times wider (`Mover.LONG_PLAN_BUDGET`) and, finding the way, goes on
  searching that wide for that goal: a cheap replan on the way round would lead back to the wall.
  A wide search that doesn't get there isn't made again for that goal for 5 seconds
  (`WIDE_RETRY_TICKS`): soldiers at a mine's bottom with no way out made one at the end of every
  partial route, two thirds of the soldiers' CPU. A wide search that doesn't reach the goal still gives a leg, if it
  ends on ground at least 8 blocks nearer (`Mover.isLeg`, `LEG_GAIN`): the soldier walks that and plans again from
  its end. From the surface a leg must end on the surface - never down into a cave toward a goal there's no walk to.
  A leg planned again from the cell the last began at goes round that one's first 4 cells (`LEG_AVOID_CELLS`), with
  a WARN. Nor after a cheap one that tried every cell it could get to
  (`Pathfinder.Result.exhausted`). And a plan that found no way at all is tried again after a second,
  then two, four, eight - sixteen at most - as its failures toward one task mount (`Mover.NO_ROUTE_RETRY_TICKS`):
  a soldier in a dig whose every cut's stand had no way in planned three and four searches a second,
  a quarter of the garrison's time.
- **Asks for help when it's out of ways of its own.** The ladder is: try again later, try another
  target or route, then ask. A routine whose last 3 tries in a row all failed, whatever they were
  at, reports CANTCO with the last one's place and reason - clearing, walling, lighting, farming, or
  carrying to the stockpile - and that CANTCO is a request to its squad leader, timed and alarmed
  (docs/military-doctrine.md § 4). One success, or the work going away, ends it.
  Work it can't begin it does elsewhere, or says so at once: a farm ordered where there's no
  ground by water within 8 blocks is the camp's farm instead, logged why; with no camp's farm
  either, CANTCO "no ground to farm by water within 8 blocks of (x, y, z)".
- **Sprints** only on an urgent move over 8 blocks, closing on a hostile, or under fire, and never
  at food 6 or below. A routine walk is walked: walking costs no food, sprinting 0.1 exhaustion a metre.

## Fighting
How it fights, measured, is in [military-doctrine.md § 8](../../military-doctrine.md#8-actions-on-contact-and-the-react-to-contact-drill).
In short:
- **Contact interrupts everything.** A hostile in sight within its contact radius, or being hit,
  takes over its movement whatever routine holds its hands.
- **Melee** by `bot.Strike`: the sword's sweep at full charge with none of the force's in its box; with a squadmate in it, a knockback hit sprinting, else a hit short of the sweep's charge.
- **Archers:** cover when it can't close, is bare-handed, or would lose the exchange closing on it (`Engagement`); with a bow, it shoots back.
- **Creepers:** gets clear; strikes only when boxed in.
- **Shield** up against arrows in flight. **No fighting step** over a deep drop.

## Keeping itself going
These are routines, one at a time by priority ([routines.md](routines.md)).
- **Eats and hunts** when hungry ([military-doctrine.md § 4](../../military-doctrine.md#4-reports-upward)) - never an
  animal the force brought in: within 16 blocks of where a scout mission wanted them, or of an animal farm's middle, it is
  stock (`WorldView.stockAt`). No meal with a creeper within 8 blocks, seen or not (`Rations.creeperNear`).
- **Scouts for animals and leads them in** on its squad's scout mission (`bot.supply.Herder`): its own sector of the
  ground, the kind's food in hand, none led over water - where the land ends it says.
- **Makes its kit** by the equipment ladder, when the squad leader allows it, and **stands to**
  with free hands: best armour on, best weapon in hand ([military-doctrine.md § 11](../../military-doctrine.md#11-pre-combat-checks)).
- **Picks up** what lies near it once nothing threatens: string, feathers, a dead squadmate's kit.

## Reporting
Its `SoldierStatus` every squad cycle: where it is, the order it holds, a `Report` (see
[orders.md](orders.md)), and its `Condition` - health, food, deaths, and its kit counted: weapon,
armour, arrows, logs, cobblestone, dirt, rations, shield, pickaxe, bow, spare weapons.

## What it can't do
- Climb ladders or vines, or step over a fence.
- Build steps toward an objective beyond one search (about 4000 cells). It builds only when the
  route it plans reaches.
- Dig straight down, but for the block it stands on with known ground a block under it - its own
  pillar - never onto farmland, which landing on tramples; or straight up under a block that falls.
- Dig ore whose drop needs a pickaxe of a tier it hasn't got: left for a squadmate's, or until
  one is made. Every other block it digs with the best tool carried for its kind - pickaxe, axe,
  shovel, or fists, which break anything but bedrock, slowly and for no drop. It pillars in open air only straight up to a
  goal over its column: a stand to cut the top of its strip from. Nor tunnel, except as the staircase routine does and
  as the recovery drill's stair up out of a pocket does (docs/military-doctrine.md § 7): a cut the
  engineer lays, up to 40 steps to the surface, dug cell by cell from where it stands - afloat, from the water it
  floats in - never under sand, gravel or a liquid.
- Open doors or gates on its route. A farmer opens a chunk farm's gates once they stand and leaves them open
  (docs/military-doctrine.md, the camp's farm); a route through a shut gate is no route.
- Decide what to work on. That comes from its squad's task (see [routines.md](routines.md)).
- Leave a mission's task for an errand of its own (operator, 2026-11-22). While its squad's objective is a step of the
  operator's mission (`FarmMissions.on`), it hauls nothing from a mine's stash or an old camp's chest, and draws logs for
  its tools only from a chest in its reach - 48 blocks across and 12 up or down (`SoldierErrands.inMissionReach`). Live
  (field692), two of a squad flattening a chunk walked 300 blocks each to a chest at the bottom of the mine for eight
  logs.
- Climb the kit ladder, or make a trip for its load's surplus, on any task: that's the squad leader's leave, given to a
  squad holding (docs/military-doctrine.md § 11). On a task it makes only the weapon or the pickaxe it has none of. Live,
  a builder's load plan put its spare sword and its logs in the chest and its kit rule drew a stack of logs and made the
  sword again, 50 blocks each way, a block dug between.
- Make a trip of its own to stock the depot - a reserve run short there, bread past its load - without its squad
  leader's leave or a duty whose yield is the depot's, a harvester's or a baker's (`SoldierErrands.stocks`). A full pack
  goes in, and its stock with it. Live (field696), a tree farm's one digger made 33 trips in a quarter of an hour to a
  chest 40 blocks off, nothing put in: 62% of its ticks the depositor's, 20% the dig's.
- Leave a gate open, or be stopped by a shut one (operator, 2026-11-22: a habit of keeping them shut): walking up to
  a shut gate it opens it, and clear of an open one with nobody in it, it shuts it - its own or one it found open,
  whatever its task (`bot.GateHabit`, ticked with its walk). To its route a shut gate is a way through, never a wall
  (`Pathfinder.solid`); to everything that builds, fills or measures it's the block it is - in the squad simulation, with
  a gate read as open ground everywhere, one was filled with dirt. A farm's gates are put up shut and stay so.
- Take a stand by a chest another soldier is in: a chest is reached from any cell round it, and the next soldier takes
  the next (`SoldierErrands.standTaken`). Live (field698), ten soldiers of three squads were sent to one cell.
- Leave a torch standing where a block of ground goes: a builder of ground takes it up and puts the block in
  (`Builder`). Read as the cell filled, a torch a block under a farm's level held its squad as fillers for hours.
- Carry the same load to a chest twice: a trip that put nothing in isn't made again while it carries the same
  (`Depositor`). Live (field693), a harvester walked farm to chest and back every 20 s, 30 blocks each way.
- What it still starts itself is listed, to be taken away, in backlog/authority-and-missions.md.

## What it approximates
Its movement (`bot.Gait`, `bot.Mover`, `bot.nav.Pathfinder`) and its senses approximate the game's. A gap is closed when
a live run or a scenario shows it costing something, as an item of its own then.
- **Partial blocks.** Slabs and stairs collide as full cubes; fences and walls are taken as 1.5 blocks tall.
- **Swimming is at the surface only:** no diving, no current pushing a swimmer, and seagrass, kelp and bubble columns
  are hazards a swimmer goes round.
- **No ladders or vines** for climbing (backlog/ladders.md).
- **A drop into water** is limited as one onto ground is, though landing in water does no harm.
- **Arrows.** One blocked is read from its health, not from the server's own block event, which isn't decoded; and an
  arrow counts as in flight for a second after it comes into view, since its velocity isn't decoded - one lying on
  the ground that comes into view raises a shield for a moment.
- **It knows every entity the server sends its connection** - through walls, behind it, in the dark - where a player
  sees only what's on screen. Contact needs a line of sight (docs/military-doctrine.md § 8); the squad's drills and
  the platoon leader's picture don't.
