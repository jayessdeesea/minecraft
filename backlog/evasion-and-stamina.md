---
status: proposed
priority: P1
doctrine: ATP 3-21.8 (individual movement techniques; cover and concealment)
---

# Dodging, cover, and when sprinting is worth the food

## Why
- **Nobody dodges.** A skeleton's arrows land on soldiers that stand still or walk straight at it.
- **Sprinting costs food but nobody weighs it.** Soldiers sprint honestly now: the server is told,
  so it charges the food, and there's no sprinting at food 6 or below (docs/military-doctrine.md
  § 10). But any goal more than 8 blocks away gets a sprint, whatever the soldier's food and
  rations.

## Done when
- **Sprinting is budgeted.** Soldiers sprint only when the time saved is worth the food: closing on
  a threat, catching a principal, crossing open ground under fire. They don't sprint on an ordinary
  move with no rations to make up the cost.
- **Cover from a shooter:** a soldier under fire with no reachable shooter moves to the nearest
  cell with no line of sight to it (`DeterministicSoldier.lineOfSight`, already used for contact).
  That's the "find cover" half of react to contact.
- **Dodging:** a soldier closing on a skeleton strafes, moving across the line of fire rather than
  along it, so an arrow aimed where it was misses.
  - Arrow entities would make dodging reactive rather than a habit: track them, and step out of
    one's path when it's inbound.
- Measured in `CombatScenarioTest`: damage taken closing on a skeleton drops with strafing; a
  soldier under fire behind cover takes none.

## Notes
- Exhaustion from damage taken (0.1 per hit) and from jumping (0.05) is charged by the server
  whatever the client does.
- With subsistence (backlog/subsistence.md), sprinting becomes a real trade-off: speed now for food
  later.
