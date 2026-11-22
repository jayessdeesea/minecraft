---
status: ready
priority: P1
depends-on: [authority-and-missions]
doctrine: ATP 3-21.8 ch. 2 (troop leading procedures); ATP 3-34.40 (general engineering - production rates, site preparation before works)
---

# The flatten: resourced before it's dealt, a depot that serves it, and its pace

## Why
A flatten (`command.Flatten`) is worked a level at a time, its crew sized to the level (docs/military-doctrine.md § 17),
but nothing checks it has what it needs before a level is dealt: builders leave their rectangles for tools the depot
doesn't hold. Its depot stows and issues less than a base's stockpile should. And the dig runs well under the 90% of
its best the operator asked for (2026-11-14, 2026-11-15; `command.LevelPace`): the first full floors went at 20-26%.

The flatten's record, its phases and its running estimate are backlog/authority-and-missions.md steps 1 and 7; the
rename of the keep's names to the flatten's (`Objective.Task.Keep`, the `[Keep]` tag, `keepwork`, `foremansNet`'s keep
fields, the task's name in the platoon leader's prompt; operator, 2026-11-15) goes with step 1's renames. Who works
what within the crew - spare hands on a level narrower than the crew, a builder with no rectangle, errands held till a
rectangle is done - is the squad leader's sectors, step 4, and backlog/soldier-order.md.

## What's left

### The resource gate
- No level is dealt until the depot's chest stands and each builder on it carries a pickaxe and a shovel: the depot's
  logs and tools stocked before the crew is sent (live, field246: four of five builders on a level left their
  rectangles for tools and timed out, the depot's chest not standing).
- The depot's standard, sized from the dig: chests enough for the fill's cobblestone (its cell count over a chest's
  1,728) and one more for wood and tools; a crafting table and a furnace beside them; a starting stock of logs.
- What's short goes up as the squad's Class IV line - chests, logs, iron for one iron pickaxe - and the platoon leader
  answers with WOOD or MINE (`PlatoonSergeant.classFour` asks only for logs, only under four, and nothing asks for iron).
- With more than one depot (one every 48 blocks along the site's edge), the gate checks every depot's standard.
- Tool-aware rectangles: stone to builders with pickaxes, soil and gravel to those with shovels. One with two pickaxes
  hands one to a builder with none (`CrossLevel.TOOLS_LOW`, 2, keeps too much with the holder).

### The depot
- **A base's stockpile is big** (operator, 2026-11-15): about 32 double chests; eight builders carry about 9,000
  blocks. The engineer keeps chests apart today (`Workshop`); whether `ChestWindow` works a 54-slot window is to be
  shown in the simulation first.
- **Caps on bulk:** an active plan that wants more (the slab, a wall) lifts a material's most; other squads' loads
  dumped as the crew's are; draws of saplings and seeds from the depot, through the reserve (today only metal, logs
  and cobblestone are drawn); a second depot once the row's cells within reach are full.
- **Selective stowing** (operator, 2026-11-14): cobblestone, logs, ore, spare tools, the lumberjacks' axes and saplings
  go in. `Depositor` stows only a full load of logs or cobblestone, so a pack full of dirt and odd blocks never empties.
- Which chest of the row is being filled is kept across a restart (today the first is tried again, a failed trip for
  each full chest).
- Draws look along the row and in any depot that holds the thing wanted, not only the chest being filled; surplus is
  moved to the depot that's short; each outpost depot has its haul road.
- The depot is a stockpile like the camp's, not a second mechanism (backlog/common-stockpile.md, which with
  backlog/mining.md has iron's path).
- **Wood for it** (operator, 2026-11-16):
  - A surveyor: a role that finds forest, ore and ground past what the squads see (`bot.supply.ResourceSurveyor`
    reports only what's within 64 blocks of a squad) and says where it is.
  - A standing lumberjack: clear-cuts the forest the surveyor found, takes a second pass once the leaves have decayed
    for the saplings and logs dropped, and carries wood and saplings back - the grove is planted near the site, not out
    there. It draws its axe and saplings from the depot's chest and puts them back.
  - The grove grown to 16-32 trees, in more than one plot.
  - The platoon leader's whole-squad WOOD answer to Class IV RED dropped for the flatten's squads, the wood party sent
    instead.
  - A trunk taller than 5 above the cutter (`Gatherer`, "leaves N log(s) of the tree out of reach"): pillar up under it
    for the rest, then down.
- **Released squads** (operator, 2026-11-16): hunting has no squad task; the mine's stash of raw iron isn't counted
  with the surface chests'.
- **A hill higher than the bench's twelve rows** still stands as a wall past the twelfth: whether the bench wants rows
  to the full depth is to be seen live, once a site's side is cut down to it.

### The pace
One line a finding; each wants its case in the simulation, then the fix.
- **The uneven split** (operator, 2026-11-16: do a better job assigning): rectangles sized by estimated time - the
  walk, the climb and the blocks' hardness - not block count, a builder with a hard reach getting fewer (operator,
  2026-11-15). As split, a level's best was 192 s against 123 s shared evenly; one builder had 50 digs, two had 3.
- **Steps put up to reach a step** (field266, field269): 323 steps in 30 minutes on the east bench under a hill beyond
  the site - builders building their way off it. Watch a wide level's pace line for steps beyond its blocks' share.
- **A builder's own step dug from under it and put back** at one cell (field329, Eli at (19, 89, -11)): the step under
  someone's feet is kept out of the dig, not a recent one - keeping them out by age left a spire's steps standing.
- **A builder on its own step's column** (field360, Bob, 371 s idle): its position rounds to the open cell beside the
  column, so every route from it fails ("no route from ... to within 0 of" the same cell) - the start cell is the one
  under the body that holds it; and the recovery's stair never reached him as an order (`Recovery.orders`).
- **"clearer idle, nothing to cut"** 15% of a level (field258, field262: Jack 75%, Kate 42%, with no cover routed):
  read which orders hold a builder "held off its rectangle (Order)".
- **Loaded columns drop out of the site's scan** for a cycle and the level flickers (field324, field327: (19, -43)
  "scan 88, last cycle's 71, live 87").
- **A depositor ordered to a stand the world model reads solid** (field256, Gus and Carol at (-26.5, 70, -19.5)); and
  one within the chest's reach by `Depositor`'s own test failing five walks to it (field268, Mia) - suspect its
  `soldierAt` stale while another routine holds the hands.
- **A metal draw that starts and stops** (field270, Jack): the stockpile's view is fixed; watch that the draw holds,
  and find why his order read Idle between tries.
- **A fight with no leash** (field277, Dana: 75 blocks through the caves under the site, one hostile to the next):
  a soldier in contact closes no further than a set distance from where its order holds it, and the caves a dig breaks
  into are sealed or given to a squad. How contact is fought is backlog/engagement-doctrine.md's.
- **Breaks at three times vanilla's:** a dig waits its fixed time plus `Breacher.MARGIN_TICKS` (5) - dirt with a stone
  shovel 12 ticks against 4. The server finishes a dig at 70% of its time, so vanilla's time for the tool plus a tick
  is enough - but `BuilderSimulationTest.aSoldierStrandedUpItsOwnPillarDigsItDown` fails with the shorter wait: the
  pillar's recovery depends on the time between cuts.
- **Pickups:** about 10 ticks a dig waiting for the drop.
- **The way down off a site's top** (field389: 118 crowding alerts at (27, 93, -7) and (51, 93, 2), Alice failing five
  walks to the stockpile): the mine's half is fixed; the rest unexplained.
- Not the flatten's own, found on it:
  - **The mine's stairs blocked from above** (field316: steps 6-10 solid for minutes, Gus stranded at y=0): the cutter
    clears the stairs from the head down when it finds them blocked. And the camp's stairs are kept on disk twice, to
    y=24 and to y=16 - two plans for one shaft.
  - **A way out cut sideways from a pocket under the ground** (field287): the room grew a cell at a time. A way out of
    a room below the ground goes up.

## Done when
- Simulation: a site with no chest and trees 40 blocks off - two builders fetch logs, the chest and table go up at the
  depot, a builder with a full pack unloads, the rest never stop digging; and no level is dealt before that.
- Simulation: a site with a spire and then a full floor - one builder takes the spire while the other squad works
  elsewhere, and both are on the floor.
- Live: the depot's chests stand, nothing useful is thrown away, every builder holds the tool for its block.
- Live: `[Keep] pace` shows "waiting, no rectangle" under 5% across a flatten, and "the dig so far" holds within 90%
  of its best across a day's levels.

## Notes
- **Decided (operator, 2026-11-22): not now** (backlog/sites.md, the order of building). The farm and grove ground of
  2026-11-17 - x=-48..-16, z=-16..16 to y=62, topped with soil (`!flatten -48 -16 -16 16 62 north soil`) - lies beside
  the origin, and nothing is built there until the reserves are up: the wheat and the trees meanwhile are templated
  chunks by the camp at the water (backlog/renewables.md), where less ground has to be cut. Whether that rectangle
  is flattened at all is for when the permanent structure is planned.
