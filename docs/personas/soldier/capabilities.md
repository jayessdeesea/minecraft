# Soldier: what it does on its own

A soldier is a player bot with its own connection, driven by plain Java every tick (20 a second).
It never calls a model. Everything here it does without being told, as reflexes of
`bot.DeterministicSoldier` and the routines round it. What it's told is in [orders.md](orders.md).

## Moving
To the goal its order names, by A* over block cells (`bot.nav.Pathfinder`), re-planned every 2
seconds or when stuck:
- **Walks** level ground, round anything solid, and diagonally only where both sides are clear.
- **Steps up one block.** A fence, at 1.5 blocks, is a wall, not a step.
- **Drops three blocks** unhurt. Further only within the fall allowance its order's risk gives it
  (see [orders.md](orders.md)), and only when no route without a fall exists.
- **Swims** across water at the surface. It never enters lava or fire.
- **Cuts a step** into a bank too high to jump: soil by hand, stone with a pickaxe, never sand or
  gravel, never where it would tunnel or let water in.
- **Pillars** with cobblestone or dirt it carries, beside a wall: jumps and places a block under
  itself.
- **Builds steps** with those blocks, down a sheer drop or up to ground above, when that's the
  only way that reaches its goal.
- **Sprints** on a long move (over 8 blocks) while it carries rations, or on an urgent order or
  under fire, and never at food 6 or below.

## Fighting
- **Contact:** a hostile it can see within 8 blocks (16 with a bow) puts it in contact. Whatever it
  was doing stops: movement gives way to the fight that tick. Being hit with nothing in sight
  makes it look for the shooter out to 16 blocks.
- **Melee:** it closes on the nearest hostile and swings at full charge within 3 blocks, keeping
  2.5 blocks off. It holds a sword's swing while a squadmate stands in the sweep round the target.
- **Archers:** bare-handed, or with no route to the archer, it takes cover out of the archer's
  sight within 6 blocks, and holds there. With a bow it stands and shoots, aiming the arrow's arc.
- **Creepers:** never fought at arm's length. It moves off along whichever heading gains the most
  distance. Boxed in, it strikes, and the knockback carries the creeper out of its blast.
- **Shield:** raised while an arrow is in flight toward it.
- **Ledges:** no step in a fight goes over a drop deeper than three blocks, unless onto water.

## Keeping itself going
These are routines (`bot.Hands`): one at a time, by fixed priority, each with its own guard. See
[routines.md](routines.md) for the full list.
- **Eats** when hungry, from rations carried.
- **Hunts** an animal by day when it has nothing to eat and is hungry or can't heal.
- **Makes its kit** by the equipment ladder (`bot.craft.Ladder`) when the squad leader allows it:
  a weapon first, stone over wood, then copper and iron armour, then diamond for weapons and armour
  before tools.
- **Stands to:** whenever its hands are free, it wears the best armour it carries and holds its
  best weapon, every 2 seconds.
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
