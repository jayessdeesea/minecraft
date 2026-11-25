---
status: ready
priority: P2
depends-on: []
---

# A squad scouts for animals and leads them back

## Why
The operator's (2026-11-25): a squad goes out, finds animals of a kind and brings them back - chicken scouting, sheep
scouting, cow scouting. The order: look here for {animal} and bring them back to {location}. The force has no stock
but what it meets by chance; a sheep farm's template stands empty until something is led into it.

## Built (2026-11-25)
The algorithm and its eval, wired to nothing yet:
- `bot.supply.Livestock`: the kind's food, one table.
- `command.ScoutSectors`: the square round the place cut into a rectangle a soldier, and each one's legs - its quarters'
  middles, so none of it is out of sight of a leg's end.
- `bot.supply.Herder`, one soldier: walk its legs; go up to the kind's animals first seen in its own sector, the food in
  hand; lead them back, waiting on one that lags and going back for one that lost the food; leave one with water on
  the way to it, one not got to, one gone back for three times, one that comes no nearer - each said; say where water
  was met, once a shore; a leg or a way back that gains no ground is given up; the food put away where they're
  brought. `picked()` against `brought()` is the outcome.
- `AnimalScoutEvalTest` against the simulated server's animals (`SimAnimals`: they follow their food in a hand from 10
  blocks, a block up or down, not into water or off a drop): found, led 40 blocks, none out of the range it follows
  from on the way; seeds for chickens, wheat for cows; a river; a trench; two soldiers at once, each its own.

## Done when
- **The order.** An operator mission with an animal's kind, where to look and where to bring them, given by the
  platoon leader to one squad as a task: a mission kind, an `Objective.Task`, the operator's chat command.
- **The routine in a soldier's tick.** `Herder` given the hands (a `Hands.Routine`), its `walkTo` a `MoveTo`, the food
  put in hand; outside `SoldierRunner`, whose caps only come down (backlog/split-oversized-sources.md).
- **The food drawn first.** A prerequisite, as a mine's shift is drawn for: the kind's food from the stores before the
  squad leaves, a shortfall said before it goes.
- **Where the land ends, said up.** `Herder.Step.landEnds` to the squad leader and on to the platoon leader - a report
  type, and what the platoon leader does with it.
- **The outcome check.** `Herder.picked()` against `brought()` in the task's DONE, a shortfall dug into.
- **The live eval.** On the pinned server: a sheep, a cow and a chicken each led 60 blocks; whether a led animal
  crosses a one-block stream, which the simulation says it won't.

## Notes
- Fences and gates on the way are for later (operator): the first version brings them to open ground at {location}.
- A soldier's walk in the eval is a straight line; in a soldier's tick it is the pathfinder's, which crosses water an
  animal won't - the route for a lead has to keep to ground an animal walks.
- Terms: the place searched is an objective, the squad's part a task (docs/military-doctrine.md).
