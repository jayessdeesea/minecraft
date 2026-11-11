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
  rarely cover: 79 against 94. An armed soldier with no route to the archer takes cover the same
  way. What's left:
  - Breaking contact properly, back toward the squad, instead of only backing away. Pinned across
    water in the open, with no cover to take, a stone-sworded soldier still dies 40 times in 40.
    In `CombatScenarioTest`'s moat the water rings the soldier, so there is nowhere to run - a
    sprint away was measured and changed nothing. What would help there is digging in: a hole two
    deep under it, a hasty fighting position (ATP 3-21.8) the archer can't see into. The combat
    simulator doesn't model digging yet; that comes first. A soldier with a bow shoots back
    instead (docs/military-doctrine.md § 8).
- **What doesn't help:** zigzagging in toward an archer, measured. It loses all 100 too: it slows
  the approach while the arrows that kill land in melee. Dodging is worth another look only for a
  soldier with a ranged weapon of its own, holding at range (backlog/ranged-combat.md).
- **The simulator's knockback is an estimate.** It pushes a hostile 0.9 blocks per hit. Since
  soldiers swing at each weapon's real recharge (a fist every 5 ticks), a lone bare-handed soldier
  beats three zombies 99 times in 100, and the squad drills stop separating in that sweep.
  Measuring vanilla's knockback would make the zombie numbers trustworthy again.

- **Seen live (field67): a pillager patrol killed three of a squad in three minutes.** Two died at
  the same cell, (-36.4, 63, 24.3), neither moving: one with a stone sword and a shield, its health
  falling to 2 with nothing in its fight changing; the next, with a wooden sword and a shield,
  raising its shield again and again as its health fell 8, 5, 2. Nothing in the fight looks at the
  soldier's own health: an armed soldier always closes on an archer it can reach. A pillager's
  crossbow isn't yet in `CombatScenarioTest`, and neither is a soldier hurt before the fight -
  both first, then whether one at low health should take cover as a bare-handed one does.

## Notes
- Exhaustion from damage taken (0.1 per hit) and from jumping (0.05) is charged by the server
  whatever the client does.
- With soldiers eating and hunting (docs/military-doctrine.md § 4), sprinting becomes a real trade-off: speed now for food
  later.
