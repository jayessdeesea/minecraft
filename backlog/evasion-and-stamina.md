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
- **Don't fight an archer bare-handed.** `CombatScenarioTest` now flies arrows: line of sight to
  shoot, 1.6 blocks a tick, aimed where the soldier stood, vanilla's spread.
  - A bare-handed soldier loses to a skeleton 100 times in 100. It takes 200 ticks of punching,
    shot point-blank throughout.
  - The same fight at a wooden sword's damage wins 100 in 100, on 9.4 health.
  - So an unarmed soldier under fire should take cover (above) or break contact, and rearm first
    where the squad leader allows. Fighting there is the losing move.
- **What doesn't help:** zigzagging in toward an archer, measured. It loses all 100 too: it slows
  the approach while the arrows that kill land in melee. Dodging is worth another look only for a
  soldier with a ranged weapon of its own, holding at range (backlog/ranged-combat.md).
- **The simulator's knockback is an estimate.** It pushes a hostile 0.9 blocks per hit. Since
  soldiers swing at each weapon's real recharge (a fist every 5 ticks), a lone bare-handed soldier
  beats three zombies 99 times in 100, and the squad drills stop separating in that sweep.
  Measuring vanilla's knockback would make the zombie numbers trustworthy again.

## Notes
- Exhaustion from damage taken (0.1 per hit) and from jumping (0.05) is charged by the server
  whatever the client does.
- With subsistence (backlog/subsistence.md), sprinting becomes a real trade-off: speed now for food
  later.
