---
status: proposed
priority: P1
doctrine: ATP 3-21.8 (individual movement techniques; cover and concealment)
---

# Dodging, cover, and sprinting that costs food

## Why
- **Sprinting is free, and that's a fidelity bug.** Soldiers move at sprint speed (5.6 blocks/s)
  when the goal is more than 8 blocks away, but never send the sprint flag (Player Command
  START_SPRINTING). The server charges sprint exhaustion (0.1 food exhaustion per metre) only to a
  player flagged as sprinting, so our soldiers sprint for free, and at food 6 or below, where
  vanilla forbids it. That's an advantage the game doesn't give real players. Fix it before
  building on sprinting.
- **Nobody dodges.** A skeleton's arrows land on soldiers that stand still or walk straight at it.

## Done when
- **Sprinting is honest and budgeted.** Soldiers send START/STOP_SPRINTING, never sprint at food 6
  or below, and sprint only when the time saved is worth the food: closing on a threat, catching a
  principal, crossing open ground under fire. The food cost is visible in LACE as it drops.
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
- Exhaustion from damage taken (0.1 per hit) and from jumping (0.05) is already charged by the
  server. Only sprinting depends on the flag we don't send.
- With subsistence (backlog/subsistence.md), sprinting becomes a real trade-off: speed now for food
  later.
