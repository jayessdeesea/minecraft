---
status: proposed
priority: P1
---

# Mobility: climbing by digging steps and building them

## Why
On foot a soldier steps up one block and drops three. In hills or mountains much of the ground is
out of reach that way:
- Bravo, in a valley at the foot of the spawn mountain, surveys "1/81 reachable" and reports "no
  route" to every objective the general picks on the high ground around it.
- A squad leaving its patrol base onto a slope does the same.

The general keeps choosing places it can see but the squad can't reach, and the squad can only
report CANTCO. A player in the same spot would dig a staircase into the hillside, or put a block
down and jump onto it.

## Done when
Live:
- **Pillaring.** A soldier carrying cobblestone jumps and places a block under itself, one block up
  per step. Routes are never planned on more blocks than it carries, and the squad leader's survey
  marks ground reached only by climbing, with what the climb costs (`Pathfinder`,
  `DeterministicSoldier`, `bot.supply.Footing`, `TerrainSurveyor`).
- **Cutting steps.** Any soldier, facing a soil bank too high to jump, digs a step into its face
  by hand and jumps onto it; one carrying a pickaxe does the same into plain stone
  (`Pathfinder.stepCut`, `bot.supply.Breacher`). Measured by `SoldierScenarioTest`'s digging runs,
  and seen live climbing a 3-high terrace of each (`climb-soil-terrace`, `climb-stone-terrace`).
- **Built steps** with cobblestone or dirt carried, down a sheer drop or up to ground above
  (docs/military-doctrine.md § 7), planned when they reach the objective.

What's left:
- **A staircase into a face taller than three blocks, with nothing carried.** A step is cut only
  where it comes out under open air, so the planner never tunnels and the survey's flood fill
  stays bounded.
- **Built steps toward an objective beyond one search.** Offered on a partial route they led the
  scenario soldier astray (lakes and ravines), so a soldier builds only when the plan reaches.
- **Swimming up and down, and water elevators.** The planner knows a swimmer only at the surface
  (water at the feet, air at the head, `Pathfinder.swimmable`): a soldier wholly underwater has no
  footing in its plan, and a step one block above the water is the only way out it sees. Live, three
  soldiers floated for hours in the flooded spiral stair of the camp's mine; the stuck-in-water drill
  got them out by filling the water into steps (docs/echelons/soldier/capabilities.md), not by
  swimming. What a player does instead:
  - **Swim as moves:** rise a block through water (jump held), sink a block, and swim forward into
    a step at the surface - each a move the planner can take, at a swim's cost, so a flooded stair
    or a pool with a bank is swum, not stranded in.
  - **Water columns as lifts:** a 1x1 shaft of water sources is a ladder you swim up and drop down
    without damage; with soul sand at its foot it's a bubble column that lifts a body, with magma it
    pulls one down. For the mine (backlog/mining.md: "ladders down a 1x1 shaft"), a water elevator
    beside the staircase is the fast way between levels.
  - **The engineer builds it:** a blueprint the engineer lays out at the mine base - the shaft cut,
    filled from water buckets (`Recipe.BUCKET` exists; carrying water needs the bucket's use), soul
    sand at the foot once there's Nether access, or a plain column until then - and a soldier's moves
    include riding it.
- **The respawn platform, live.** Replacements respawn on a patch of glass at y=102 near (-5, 9),
  17 blocks over the ground. The recovery drill's stair (docs/military-doctrine.md § 7) passes the
  squad simulation's copy of it; it wants seeing live, the next time a replacement lands there.

## Notes
- Blocks come from what the soldier carries (`Builder`'s cobblestone, planks, logs), or from what
  it dug on the way up.
- Also open: the survey takes the top ground in each column. In a valley or under an overhang
  that's often far above the squad, which is part of why the high ground dominates its picture.
