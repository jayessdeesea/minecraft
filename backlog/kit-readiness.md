---
status: ready
priority: P1
---

# The platoon leader keeps the force equipped: it asks how each is kitted, and orders what's missing made and worn

## Why
The operator (2026-11-24): soldiers not in armour with so much copper; the platoon leader should realise the supplies
are there and demand everyone has armour on - ask how people are equipped and say "you need armour, there's copper in a
chest near you: build it, put it on"; and shields. Live that day: of sixteen, none carried a shield, about half wore no
armour, the stores held 326 iron and 490 copper ingots, and Alice carried 258 copper ingots unarmoured. The kit ladder
(`bot.craft.Ladder`) has the rungs - a shield after the iron pickaxe and the buckets, then copper, iron and diamond
armour - but a soldier on a task made only the weapon or pickaxe it lacked, and with every squad on a mission's step
the leave to rearm never came.

Built the same day: on a task, armour and a shield are made when all they're made of is in the pack already
(`SoldierErrands.madeOnMission`, `carried`).

## Done when
- **The readiness is a register the platoon leader reads**: by soldier, what it wears and carries against its basic
  load (weapon, pickaxe, shield, each armour slot and of what), and what the stores hold toward what's missing -
  computed, in its picture and the chat's answer ("how is the force equipped?").
- **A rule orders the gap closed** (`command.DrillPlatoonLeader`, with its tests): where the stores hold the makings
  of a soldier's next rung, that soldier is ordered to it - draw so many ingots from the chest named, the nearest
  holding them; make it; put it on - a temporary order, as cross-levelling is, that doesn't take a squad off its
  mission: one soldier at a time a squad, at a break in its work.
- **Worn, not carried**: a piece made or drawn is put on, and one carried unworn is said in the readiness.
- **One kind of shield** (the game has no tiers of it: planks and one iron ingot); the better kit is the armour's
  ladder - copper, then iron, then diamond - and each soldier climbs it as the stores allow, the worse piece handed on.
- Evals: sixteen bare with copper in a chest - all in copper inside a set time, none off its mission for more than the
  walk; iron arriving - shields first, then iron over copper.
- Live: every soldier armoured and shielded, and the readiness says so.
