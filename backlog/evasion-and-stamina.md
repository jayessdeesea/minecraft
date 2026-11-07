---
status: proposed
priority: P1
doctrine: ATP 3-21.8 (individual movement techniques; cover and concealment)
---

# Dodging, cover and breaking contact

## Why
- **Nobody dodges.** A skeleton's arrows land on soldiers that stand still or walk straight at it,
  unless they carry a shield to raise (docs/military-doctrine.md § 8).

## Done when
- **Cover is live for a bare-handed soldier** (docs/military-doctrine.md § 8). Against a skeleton
  among trees: 36 of 100 die taking cover, against 75 of 100 charging in. On flat ground there's
  rarely cover: 79 against 94. What's left:
  - Cover for an armed soldier pinned at range, where it can't close.
  - Breaking contact properly, back toward the squad, instead of only backing away.
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
