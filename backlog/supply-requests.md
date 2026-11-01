---
status: proposed
priority: P2
depends-on: [inventory-decoding]
doctrine: classes of supply (FM 4-0), LOGSTAT
---

# Supply requests up the chain of command

## Why
"I need armour" and "I'm defenceless" are doctrine's supply requests: Class II (clothing,
individual equipment), Class V (ammunition), Class I (food) and Class VIII (medical). The squad
leader should be able to pass the squad's needs up to the general as a logistics status report,
and the general should weigh them.

## Done when
- `SquadStatus` carries a LOGSTAT-shaped summary built from the soldiers' LACE reports.
- The general's prompt shows it, and the general can respond, for example by giving the squad a
  resupply or reconstitution objective.

## Notes
- Open question: what supplies the supplies? There is no logistics chain in Minecraft beyond the
  soldiers themselves. Until pre-combat-checks lands, the only possible answer is "withdraw and
  reconstitute", which may still be worth having.
