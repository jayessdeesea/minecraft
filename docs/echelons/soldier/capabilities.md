# Soldier: what it does on its own

A soldier is a player bot with its own connection, driven by plain Java every tick (20 a second).
It never calls a model. Everything here it does without being told, as reflexes of
`bot.DeterministicSoldier` and the routines round it. What it's told is in [orders.md](orders.md).

## Moving
To the goal its order names, by A* over block cells (`bot.nav.Pathfinder`), re-planned every 2
seconds or when stuck:
- **Walks** level ground, round anything solid, and diagonally only where both sides are clear.
- **Steps up one block.** A fence, at 1.5 blocks, is a wall, not a step.
- **Drops three blocks** unhurt. Further only within the fall allowance its order's risk gives it,
  and only when no route without a fall exists ([military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).
- **Swims** across water at the surface. It never enters lava or fire.
- **Cuts a step** into a bank too high to jump, from the ground or out of water: soil by hand, stone with a pickaxe, never sand or
  gravel, never where it would tunnel or let water in.
- **Pillars** with cobblestone or dirt it carries, beside a wall: jumps and places a block under
  itself.
- **Builds steps** with those blocks, down a sheer drop or up to ground above, when that's the
  only way that reaches its goal (`Pathfinder.stepBuild`): one block a step against ground beside
  it, two in open air. Never on farmland or a crop, which a block put down on spoils; nor does it
  pillar off farmland, or cut a step through it.
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
  it, flowing or not, which is footing to climb out by. And it reports CANTCO "no route from (x, y, z) out of the water", which
  the squad's recovery drill reads as stranded when it has too few blocks to build its own way:
  a squadmate with blocks is sent to build it a stair (docs/military-doctrine.md § 7). Live, two
  soldiers floated in a flooded shaft for hours reporting the wood they meant to gather.
- **Searches wider for a goal its cheap searches can't reach.** Once its plans gain no ground, it
  searches 15 times wider (`DeterministicSoldier.LONG_PLAN_BUDGET`) and, finding the way, goes on
  searching that wide for that goal: a cheap replan on the way round would lead back to the wall.
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
- **Melee** at a sword's full charge, holding the swing while a squadmate is in the sweep.
- **Archers:** cover when it can't close or is bare-handed; with a bow, it shoots back.
- **Creepers:** gets clear; strikes only when boxed in.
- **Shield** up against arrows in flight. **No fighting step** over a deep drop.

## Keeping itself going
These are routines, one at a time by priority ([routines.md](routines.md)).
- **Eats and hunts** when hungry ([military-doctrine.md § 4](../../military-doctrine.md#4-reports-upward)).
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
- Dig a block whose drop needs a pickaxe of a tier it hasn't got: left for a squadmate's, or until
  one is made. Every other block it digs with the tool of its kind - pickaxe, axe, shovel or hand. It pillars in open air only straight up to a
  goal over its column: a stand to cut the top of its strip from. Nor tunnel, except as the staircase routine does.
- Open doors or gates.
- Decide what to work on. That comes from its squad's task (see [routines.md](routines.md)).
