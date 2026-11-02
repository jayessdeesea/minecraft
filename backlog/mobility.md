---
status: proposed
priority: P1
---

# Mobility: climbing by digging steps and building them

## Why
A soldier can step up one block and drop three, and nothing else. In hills or mountains most of
the ground is out of reach:
- Bravo, in a valley at the foot of the spawn mountain, surveys "1/81 reachable" and reports "no
  route" to every objective the general picks on the high ground around it.
- A squad leaving its patrol base onto a slope does the same.

The general keeps choosing places it can see but the squad can't reach, and the squad can only
report CANTCO. A player in the same spot would dig a staircase into the hillside, or put a block
down and jump onto it.

## Done when
Pillaring is live: a soldier carrying cobblestone jumps and places a block under itself, one block
up per step, and the pathfinder plans those steps at a price (`Pathfinder`, `DeterministicSoldier`,
`bot.supply.Footing`). What's left:
- **Digging steps:** facing a hillside it can't climb, a soldier digs a staircase. It removes the
  two cells above each step and goes up one block per step. Pillaring needs blocks; digging
  doesn't, and leaves a way back down.
- **The survey counts ground reachable by these moves,** so the squad leader and the general see
  the high ground as reachable at a price, not walled off. Today the survey's flood fill uses
  walking moves only.
- **Blocks budgeted:** a route's pillar steps are checked against the blocks carried. Today a
  soldier that runs out mid-climb stops and re-plans.
- Measured in `SoldierScenarioTest` with a cliff family: 2-, 4- and 8-block walls, crossed with
  blocks or by digging.

## Notes
- Blocks come from what the soldier carries (`Builder`'s cobblestone, planks, logs), or from what
  it dug on the way up.
- Digging steps needs the right tool for the material: a pickaxe for stone, hands for dirt.
  `Digger`'s break times already cover both.
- Also open: the survey takes the top ground in each column. In a valley or under an overhang
  that's often far above the squad, which is part of why the high ground dominates its picture.
