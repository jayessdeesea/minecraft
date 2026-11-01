---
status: proposed
priority: P2
doctrine: LACE report - ammunition and equipment
---

# Inventory decoding: weapon and armour in the LACE report

## Why
`command.Condition` reports food, health and deaths. It doesn't report weapon or armour, so the
squad leader can't tell a bare-fisted soldier from an armed one. Supply requests and pre-combat
checks depend on knowing that too.

## Done when
- `protocol` decodes the player inventory: Set Container Content, Set Container Slot and Set
  Held Slot. IDs and layouts come from the server jar.
- `Condition` gains ammunition (weapon in hand, arrows) and equipment (armour, tools). The squad
  leader's prompt shows them.

## Notes
- The hard part is the item stack's wire format. After the item ID, a stack carries a component
  patch. Each component is written with its own type's codec and has no length prefix
  (`DataComponentPatch`'s stream codec). A component we don't decode can't be skipped, so the
  rest of the packet can't be read either.
- Options:
  - Decode the handful of component types soldiers will meet: damage, enchantments, custom name.
  - Or, when an unknown component appears, keep the item ID and mark the rest of that packet
    unknown, logging it loudly. That's safe because the frame is already length-isolated, the same
    reasoning as `UnknownPacket`.
- Items picked up from the ground or from mob drops usually carry an empty patch, which is the
  common case.
- Mapping item IDs to "weapon" or "armour" is a generated registry, built from the server's
  reports the same way `BlockClassRegistry` is.
