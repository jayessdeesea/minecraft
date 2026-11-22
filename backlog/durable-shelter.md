---
status: proposed
priority: P2
depends-on: [rest-plan]
doctrine: ATP 3-37.34 (survivability: hasty to deliberate positions, overhead cover, observation); ATP 3-21.8 (patrol base)
---

# A durable shelter: a gate, observation, beds

## Why
Each night every squad builds a hasty hut and seals itself in, and each morning cuts its way out.
A player's base has a way in that closes, and lasts. The hasty hut is right for the first night at a foothold;
after that a squad should come back to a shelter that stands. The camp's wall has an open doorway and an unlit top.

## What's left
- **Hasty, then deliberate** (ATP 3-37.34): the first night's hut stays as it is. From the second,
  the engineer lays out a deliberate shelter at the camp - cobblestone, roofed, lit inside - with a
  gate: a **fence gate** (`Recipe.FENCE_GATE`), or an iron door with a button - never a wooden door, which zombies
  break on hard difficulty and vindicators in a raid. A squad goes in at nightfall
  (docs/echelons/platoon-leader/operator-rules.md), closes it behind it, and opens it at dawn.
- **A gate on a route.** Today only a farmer opens gates - a chunk farm's, once they stand, left open - and a route
  through a shut gate is no route (docs/echelons/soldier/capabilities.md). Wanted: a shut gate as a move the planner
  takes - opened by a click, like a table, the server's block update saying whether it's open - and closed behind the
  last soldier through.
- **Windows - observation, not light.** A fighting position has firing and observation ports,
  so its occupants see the threat before it's at the wall. Glass, smelted from sand, keeps mobs out
  and lets a soldier see out; light inside comes from torches either way. Worth it in the deliberate
  shelter, where a sentry watches the approach at night; not in the hasty hut.
- **Beds** go in the deliberate shelter: what they're for and how they're made and used is backlog/rest-plan.md.
- **The camp's wall, finished:** a gate in the wall's doorway the squads close behind them, and the wall's own top lit
  so nothing spawns on it.
- **An old hut sealed shut:** a hut left with a doorway is taken back after a restart; one sealed with nobody inside
  has no floor the squad can walk to, so a new one goes up beside it. Taken back too.

## Done when
- From the second night, a squad goes into a standing shelter by its gate, and out by it at dawn,
  with nothing dug or rebuilt.
- The camp's wall has a gate the squads close behind them, and nothing spawns on its top.
