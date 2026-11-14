---
status: proposed
priority: P2
depends-on: []
doctrine: FM 6-0 (reports: the task the report is about)
---

# A CANTCO from a side job takes a builder off the keep

## Why
Live (field165, 2026-11-13 23:06), Hal was put on `dig the keep` and taken off it one second later
as CANTCO - "can't light the ground: 4 tries in a row failed" - a report from his torch-lighting
upkeep, not from the dig. The soldier's report is one line chosen by precedence
(`SoldierRunner`, the report chain), and a lighting failure outranks the dig it is actually doing;
the task register (`TaskRegister`) reads any CANTCO as the task's and takes him off. Eli the same
hour: a stone pickaxe he couldn't craft (no logs) failed every minute while he stood on his strip.

## Done when
- A soldier's report names the task it's about: a CANTCO from upkeep or kit-making never reads as
  the squad's task's CANTCO, and the register leaves a builder on its strip while its dig has work.
- A simulation shows it: a builder with a failing side job stays on the keep and its count falls.

## Notes
- The report chain's order was set case by case (a farmer's CANTCO over its farming, the keep's
  dig over the camp's ground); the honest shape is a report per routine with the register reading
  the one for the task it tracks.
