---
status: proposed
priority: P1
doctrine: ATP 3-21.8 (individual movement techniques; cover and concealment; the hasty fighting position)
---

# Dodging, cover and breaking contact: what the exchange doesn't settle

## Why
A soldier weighs the exchange with an archer, takes cover, withdraws, heals and comes back
(docs/military-doctrine.md § 8, `bot.Engagement`). Four cases are left where it still dies or the numbers can't be
trusted. Defending against hostiles is the one thing a soldier does unordered (backlog/authority-and-missions.md);
when and whether to break contact is backlog/engagement-doctrine.md's.

## What's left
- **Digging in.** Pinned across water in the open with no cover to take (`CombatScenarioTest`'s moat), a stone-sworded
  soldier dies 40 times in 40; a sprint away was measured and changed nothing. What would help is a hole two deep
  under it, a hasty fighting position the archer can't see into. The combat simulator doesn't model digging: that
  comes first.
- **Bare hands against a pillager in the open, on hills and in a pond**: a third or less come through in
  `CombatMatrixTest` - its follow range is 32, past the flight's 20 (`ContactThresholds.FLED_PAST`) - and the
  soldier's order takes it back past the archer. Held off it, or routed round, till the archer's gone.
- **Withdrawing toward the squad**, not only to cover or the heading that gains most (`Engagement.withdrawal`) - the
  drill itself is backlog/engagement-doctrine.md's.
- **The simulator's knockback is an estimate** (`Battle.KNOCKBACK`, 0.9 blocks a hit). With soldiers swinging at each
  weapon's real recharge, a lone bare-handed soldier beats three zombies 99 times in 100 and the squad drills stop
  separating. Measure vanilla's.

## Done when
- The combat simulator models a dug hole, and the moat cell's soldier survives in it.
- The matrix's bare-hands pillager cells on the flat, on hills and in a pond meet the matrix's 90%.
- A withdrawing soldier with its squad in reach ends nearer the squad, in the squad simulation.
- `Battle.KNOCKBACK` is a measured figure, and the zombie drills separate again.

## Notes
- Measured, doesn't help: zigzagging in toward an archer loses all 100 too - it slows the approach while the arrows
  that kill land in melee. Dodging is worth another look only for a soldier with a ranged weapon of its own, holding
  at range (backlog/ranged-combat.md).
