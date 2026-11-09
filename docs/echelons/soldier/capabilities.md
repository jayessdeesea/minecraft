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
  it, two in open air.
- **Doesn't try the same thing forever.** Stuck short of a waypoint - jumping at a block that
  won't take it, a squadmate on the same step - it routes round that cell for 10 seconds, at a
  cost that leaves the cell usable if there's no other way, and comes back to it only after.
  The same goes for what it works at: a wall cell, a torch's cell, a spot to dig, a stockpile that
  fails is passed over for the next, and each time it fails again it's left twice as long
  (`bot.supply.Setbacks`). Something a squadmate was in the way of is tried again soon; a cell
  across water or a torch on leaves is tried less and less.
- **Asks for help when it's out of ways of its own.** The ladder is: try again later, try another
  target or route, then ask. A routine whose last 3 tries in a row all failed, whatever they were
  at, reports CANTCO with the last one's place and reason - clearing, walling, lighting, farming, or
  carrying to the stockpile - and that CANTCO is a request to its squad leader, timed and alarmed
  (docs/military-doctrine.md § 4). One success, or the work going away, ends it.
- **Sprints** on a long move (over 8 blocks) while it carries rations, or on an urgent order or
  under fire, and never at food 6 or below.

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
- Dig straight down, or tunnel, except as the staircase routine does.
- Open doors or gates.
- Decide what to work on. That comes from its squad's task (see [routines.md](routines.md)).
