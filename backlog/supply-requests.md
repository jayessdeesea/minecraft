---
status: proposed
priority: P2
doctrine: classes of supply (FM 4-0), LOGSTAT
---

# Supply requests up the chain of command

## Why
"I need armour" and "I'm defenceless" are doctrine's supply requests: Class II (clothing,
individual equipment), Class V (ammunition), Class I (food) and Class VIII (medical). The squad
leader should be able to pass the squad's needs up to the general as a logistics status report,
and the general should weigh them.

## Done when
- A supply point - a chest the operator places and stocks, at a configured position - is shown to
  the general next to each squad's LOGSTAT, and the general can send a squad there.
- A soldier at the supply point who is short of something takes it: opens the chest, moves a
  weapon, armour, arrows or food into its inventory, holds the weapon and wears the armour. All
  deterministic, no LLM.

## Notes
- The LOGSTAT already reaches the general (docs/military-doctrine.md §4).
- Mechanics needed: Use Item On to open the chest, Open Screen and the chest's Container Set
  Content, Container Click (quick move), Container Close, Set Carried Item to hold the weapon.
