---
status: ready
priority: P1
doctrine: LACE report (liquids, ammunition, casualties, equipment) - consolidation and reorganization
---

# Soldiers report their own condition (LACE)

## Why
A soldier can't ask for help today: it reports where it is and what it's doing, never what shape
it's in. The squad leader can't tell a fresh soldier from a bare-fisted one on 2 hearts. LACE is
doctrine's answer, a short self-report after contact or on request. It's also the basis for
every self-advocacy item after this one.

## Done when
- `SoldierStatus` carries a LACE-shaped condition, mapped onto Minecraft:
  - Liquids: food and saturation.
  - Ammunition: weapon in hand, arrows.
  - Casualties: health, and deaths since the last report.
  - Equipment: armour, tools.
- The squad leader's prompt shows it per soldier, and the system prompt says how to use it.
- Unit tests cover the mapping. A live run shows the numbers changing after a fight.

## Notes
- Food and health already arrive in Set Health (`SetHealthPacket`: health, food, saturation),
  but `SoldierRunner` uses health only, for the respawn decision.
- Inventory needs new Play packets: Set Container Content and Set Container Slot for the player
  inventory, plus Set Held Item. Confirm the IDs against the server jar (DEVELOPMENT.md
  § Confirming wire formats).
- Mapping item IDs to "weapon"/"armour" is a generated registry, the same way
  `BlockClassRegistry` is generated from the server's reports.
