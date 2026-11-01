---
status: ready
priority: P1
doctrine: battle drill "react to contact" (ATP 3-21.8); mutual support
---

# React to contact as a squad, not as individuals

## Why
`CombatScenarioTest` shows a lone bare-fisted soldier loses to one zombie: dead in 84 of 100
seeds. The zombie deals 3 damage every 20 ticks against the soldier's 1 every 10. The contact reflex
makes it worse: the soldier retreats straight away at the speed the zombie chases, so contact
never breaks. It runs indefinitely, abandoning its order, until something corners it.

Doctrine doesn't leave contact to the individual. In the react-to-contact drill, the element in
contact returns fire and the rest of the team moves to support it.

## Done when
- When a soldier reports InContact, the squad responds deterministically, without waiting for the
  squad leader's next decision. Soldiers within supporting distance close on it, so the hostile
  faces several fists. The soldier in contact stops running and holds or withdraws toward the
  squad.
- The withdrawal is bounded: a soldier never retreats indefinitely away from its squad and order.
- The combat simulation gains squad fights. Three soldiers against one, then several, zombies
  survive and kill at a measured rate that clearly beats the lone-soldier baseline. The baseline
  stays as a regression reference.
- The squad leader sees the contact in its next SITREP and can override it, for example by
  breaking contact as a squad.

## Notes
- Lone-soldier baseline, 100 seeds each: zombie DIED 84 / STILL_ENGAGED 15 / KILLED_IT 1;
  skeleton DIED 85 / STILL_ENGAGED 15; creeper SURVIVED_BLAST 86 / STILL_ENGAGED 14.
- This sits between the soldier's per-tick reflex and the squad leader's order. That's where
  `LinkUp` sits too: deterministic squad-level bookkeeping in `command`, run by the squad loop.
