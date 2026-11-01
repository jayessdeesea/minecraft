---
status: ready
priority: P1
doctrine: battle drill "react to contact" (ATP 3-21.8); mutual support
---

# React to contact as a squad

## Why
A lone soldier now wins one-on-one. The contact reflex fights at arm's length: it closes to
reach, strikes, backs off inside 2.5 blocks, holds in between, and gets away from creepers.
`CombatScenarioTest` shows 100 of 100 wins against a zombie or skeleton, and a live zombie
summoned next to Alice died in 18 strikes. Several hostiles at once are a different fight. One
soldier can keep only one at arm's length.

In doctrine's react-to-contact drill, the rest of the team moves to support the element in
contact.

## Done when
- When a soldier reports InContact, squad members within supporting distance close on it
  deterministically, without waiting for the squad leader's next decision. Their own reflexes
  take over once the hostiles are within range.
- The combat simulation gains multi-hostile fights, for example one soldier against three
  zombies, and the same with the squad supporting. Support clearly improves survival.
- The squad leader sees the contact in its next SITREP and can override it, for example by
  breaking contact as a squad.

## Notes
- This sits between the soldier's reflex and the squad leader's order, alongside `LinkUp`:
  deterministic squad-level bookkeeping in `command`, run by the squad loop.
- `Battle.step` targets a single soldier today; squad fights need it to target the nearest of
  several.
